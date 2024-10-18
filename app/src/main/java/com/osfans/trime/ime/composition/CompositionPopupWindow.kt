// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.composition

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.PopupWindow
import androidx.core.math.MathUtils
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.enums.PopupPosition
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding
import splitties.views.verticalPadding
import timber.log.Timber

@InputScope
@Inject
class CompositionPopupWindow(
    private val ctx: Context,
    private val rime: RimeSession,
    private val theme: Theme,
    private val commonKeyboardActionListener: CommonKeyboardActionListener,
    private val bar: QuickBar,
) : InputBroadcastReceiver {
    // 顯示懸浮窗口
    val isPopupWindowEnabled =
        AppPrefs.defaultInstance().keyboard.popupWindowEnabled &&
            theme.generalStyle.window.isNotEmpty()

    val composition =
        Composition(ctx, theme).apply {
            setOnActionMoveListener { x, y ->
                updatePopupWindow(x.toInt(), y.toInt())
            }
            setOnSelectCandidateListener { idx ->
                rime.launchOnReady { it.selectCandidate(idx) }
            }
            setKeyboardActionListener(commonKeyboardActionListener.listener)
        }

    private val root =
        ctx.horizontalLayout {
            layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
            visibility = if (isPopupWindowEnabled) View.VISIBLE else View.GONE
            horizontalPadding = dp(theme.generalStyle.layout.marginX)
            verticalPadding = dp(theme.generalStyle.layout.marginY)
            add(composition, lParams(wrapContent, wrapContent))
        }

    // 悬浮窗口是否可移動
    private val isPopupWindowMovable = theme.generalStyle.layout.movable

    private var popupWindowX = 0
    private var popupWindowY = 0 // 悬浮床移动座標

    // 候選窗與邊緣空隙
    private val popupMargin = theme.generalStyle.layout.spacing

    // 悬浮窗与屏幕两侧的间距
    private val popupMarginH = theme.generalStyle.layout.realMargin

    // 悬浮窗口彈出位置
    private var popupWindowPos = PopupPosition.fromString(theme.generalStyle.layout.position)

    private val mPopupWindow by lazy {
        PopupWindow(root).apply {
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                windowLayoutType =
                    WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            setBackgroundDrawable(
                ColorManager.getDrawable(
                    ctx,
                    "text_back_color",
                    theme.generalStyle.layout.border,
                    "border_color",
                    theme.generalStyle.layout.roundCorner,
                    theme.generalStyle.layout.alpha,
                ),
            )
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            elevation =
                ctx.dp(
                    theme.generalStyle.layout.elevation
                        .toFloat(),
                )
        }
    }

    var isCursorUpdated = false // 光標是否移動

    private val mPopupRectF = RectF()
    private val mPopupHandler = Handler(Looper.getMainLooper())

    private val mPopupTimer =
        Runnable {
            if (!isPopupWindowEnabled || bar.view.windowToken == null) return@Runnable
            bar.view.let { anchor ->
                var x = 0
                var y = 0
                val (_, anchorY) =
                    intArrayOf(0, 0).also {
                        anchor.getLocationInWindow(it)
                    }
                root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val selfWidth = root.measuredWidth
                val selfHeight = root.measuredHeight

                val minX = anchor.dp(popupMarginH)
                val minY = anchor.dp(popupMargin)
                val maxX = anchor.width - selfWidth - minX
                val maxY = anchorY - selfHeight - minY
                if (isWinFixed() || !isCursorUpdated) {
                    // setCandidatesViewShown(true);
                    when (popupWindowPos) {
                        PopupPosition.TOP_RIGHT -> {
                            x = maxX
                            y = minY
                        }
                        PopupPosition.TOP_LEFT -> {
                            x = minX
                            y = minY
                        }
                        PopupPosition.BOTTOM_RIGHT -> {
                            x = maxX
                            y = maxY
                        }
                        PopupPosition.DRAG -> {
                            x = popupWindowX
                            y = popupWindowY
                        }
                        PopupPosition.FIXED, PopupPosition.BOTTOM_LEFT -> {
                            x = minX
                            y = maxY
                        }
                        else -> {
                            x = minX
                            y = maxY
                        }
                    }
                } else {
                    // setCandidatesViewShown(false);
                    when (popupWindowPos) {
                        PopupPosition.LEFT, PopupPosition.LEFT_UP -> x = mPopupRectF.left.toInt()
                        PopupPosition.RIGHT, PopupPosition.RIGHT_UP -> x = mPopupRectF.right.toInt()
                        else -> Timber.wtf("UNREACHABLE BRANCH")
                    }
                    x = MathUtils.clamp(x, minX, maxX)
                    when (popupWindowPos) {
                        PopupPosition.LEFT, PopupPosition.RIGHT ->
                            y = mPopupRectF.bottom.toInt() + popupMargin
                        PopupPosition.LEFT_UP, PopupPosition.RIGHT_UP ->
                            y = mPopupRectF.top.toInt() - selfHeight - popupMargin
                        else -> Timber.wtf("UNREACHABLE BRANCH")
                    }
                    y = MathUtils.clamp(y, minY, maxY)
                }
                if (!mPopupWindow.isShowing) {
                    mPopupWindow.showAtLocation(anchor, Gravity.START or Gravity.TOP, x, y)
                } else {
                    /* must use the width and height of popup window itself here directly,
                     * otherwise the width and height cannot be updated! */
                    mPopupWindow.update(x, y, -1, -1)
                }
            }
        }

    fun isWinFixed(): Boolean =
        Build.VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP ||
            popupWindowPos !== PopupPosition.LEFT &&
            popupWindowPos !== PopupPosition.RIGHT &&
            popupWindowPos !== PopupPosition.LEFT_UP &&
            popupWindowPos !== PopupPosition.RIGHT_UP

    private fun updatePopupWindow(
        offsetX: Int,
        offsetY: Int,
    ) {
        Timber.d("updatePopupWindow: winX = %s, winY = %s", offsetX, offsetY)
        popupWindowPos = PopupPosition.DRAG
        popupWindowX = offsetX
        popupWindowY = offsetY
        mPopupWindow.update(popupWindowX, popupWindowY, -1, -1, true)
    }

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        if (ctx.composition.length > 0) {
            updateCompositionView()
        } else {
            hideCompositionView()
        }
    }

    fun hideCompositionView() {
        mPopupWindow.dismiss()
        mPopupHandler.removeCallbacks(mPopupTimer)
    }

    private fun updateCompositionView() {
        if (isPopupWindowMovable == "once") {
            popupWindowPos = PopupPosition.fromString(theme.generalStyle.layout.position)
        }
        mPopupHandler.post(mPopupTimer)
    }

    fun updateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        if (!isWinFixed()) {
            val composingText = cursorAnchorInfo.composingText
            // update mPopupRectF
            if (composingText == null) {
                // composing is disabled in target app or trime settings
                // use the position of the insertion marker instead
                mPopupRectF.top = cursorAnchorInfo.insertionMarkerTop
                mPopupRectF.left = cursorAnchorInfo.insertionMarkerHorizontal
                mPopupRectF.bottom = cursorAnchorInfo.insertionMarkerBottom
                mPopupRectF.right = mPopupRectF.left
            } else {
                val startPos: Int = cursorAnchorInfo.composingTextStart
                val endPos = startPos + composingText.length - 1
                val startCharRectF = cursorAnchorInfo.getCharacterBounds(startPos)
                val endCharRectF = cursorAnchorInfo.getCharacterBounds(endPos)
                if (startCharRectF == null || endCharRectF == null) {
                    // composing text has been changed, the next onUpdateCursorAnchorInfo is on the road
                    // ignore this outdated update
                    return
                }
                // for different writing system (e.g. right to left languages),
                // we have to calculate the correct RectF
                mPopupRectF.top = startCharRectF.top.coerceAtMost(endCharRectF.top)
                mPopupRectF.left = startCharRectF.left.coerceAtMost(endCharRectF.left)
                mPopupRectF.bottom = startCharRectF.bottom.coerceAtLeast(endCharRectF.bottom)
                mPopupRectF.right = startCharRectF.right.coerceAtLeast(endCharRectF.right)
            }
            cursorAnchorInfo.matrix.mapRect(mPopupRectF)
        }
    }
}
