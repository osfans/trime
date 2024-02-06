package com.osfans.trime.ime.text

import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.PopupWindow
import com.blankj.utilcode.util.BarUtils
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.databinding.CompositionRootBinding
import com.osfans.trime.ime.enums.PopupPosition
import splitties.dimensions.dp
import timber.log.Timber

class CompositionPopupWindow(
    private val ctx: Context,
    private val theme: Theme,
) {
    // 顯示懸浮窗口
    val isPopupWindowEnabled =
        AppPrefs.defaultInstance().keyboard.popupWindowEnabled &&
            theme.style.getObject("window") != null

    val composition =
        CompositionRootBinding.inflate(LayoutInflater.from(ctx)).apply {
            root.visibility = if (isPopupWindowEnabled) View.VISIBLE else View.GONE
            with(compositionView) {
                reset()
                setShowComment(!Rime.getOption("_hide_comment"))
            }
        }

    // 悬浮窗口是否可移動
    private val isPopupWindowMovable = theme.style.getString("layout/movable")

    private var popupWindowX = 0
    private var popupWindowY = 0 // 悬浮床移动座標

    // 候選窗與邊緣空隙
    private val popupMargin = theme.style.getInt("layout/spacing")

    // 悬浮窗与屏幕两侧的间距
    private val popupMarginH = theme.style.getInt("layout/real_margin")

    // 悬浮窗口彈出位置
    private var popupWindowPos = PopupPosition.fromString(theme.style.getString("layout/position"))

    private val mPopupWindow =
        PopupWindow(composition.root).apply {
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                windowLayoutType =
                    WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            setBackgroundDrawable(
                theme.colors.getDrawable(
                    "text_back_color",
                    "layout/border",
                    "border_color",
                    "layout/round_corner",
                    "layout/alpha",
                ),
            )
            elevation = ctx.dp(theme.style.getFloat("layout/elevation"))
        }

    var isCursorUpdated = false // 光標是否移動

    private val mPopupRectF = RectF()
    private val mPopupHandler = Handler(Looper.getMainLooper())
    var anchorView: View? = null

    private val mPopupTimer =
        Runnable {
            if (!isPopupWindowEnabled) return@Runnable
            anchorView?.let { anchor ->
                if (anchor.windowToken == null) return@Runnable

                var x = 0
                var y = 0
                val candidateLocation = IntArray(2)
                anchor.getLocationOnScreen(candidateLocation)

                val minX: Int = anchor.dp(popupMarginH)
                val minY: Int = anchor.dp(popupMargin)
                val maxX: Int = anchor.width - mPopupWindow.width - minX
                val maxY = candidateLocation[1] - mPopupWindow.height - minY
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
                    x = x.coerceIn(minX, maxX)
                    when (popupWindowPos) {
                        PopupPosition.LEFT, PopupPosition.RIGHT ->
                            y = mPopupRectF.bottom.toInt() + popupMargin
                        PopupPosition.LEFT_UP, PopupPosition.RIGHT_UP ->
                            y = mPopupRectF.top.toInt() - mPopupWindow.height - popupMargin
                        else -> Timber.wtf("UNREACHABLE BRANCH")
                    }
                    y = y.coerceIn(minY, maxY)
                }
                y -= BarUtils.getStatusBarHeight()
                if (!mPopupWindow.isShowing) {
                    mPopupWindow.showAtLocation(anchorView, Gravity.START or Gravity.TOP, x, y)
                } else {
                    /* must use the width and height of popup window itself here directly,
                     * otherwise the width and height cannot be updated! */
                    mPopupWindow.update(x, y, mPopupWindow.width, mPopupWindow.height)
                }
            }
        }

    fun isWinFixed(): Boolean {
        return Build.VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP ||
            popupWindowPos !== PopupPosition.LEFT && popupWindowPos !== PopupPosition.RIGHT &&
            popupWindowPos !== PopupPosition.LEFT_UP && popupWindowPos !== PopupPosition.RIGHT_UP
    }

    fun updatePopupWindow(
        offsetX: Int,
        offsetY: Int,
    ) {
        Timber.d("updatePopupWindow: winX = %s, winY = %s", offsetX, offsetY)
        popupWindowPos = PopupPosition.DRAG
        popupWindowX = offsetX
        popupWindowY = offsetY
        mPopupWindow.update(popupWindowX, popupWindowY, -1, -1, true)
    }

    fun hideCompositionView() {
        mPopupWindow.dismiss()
        mPopupHandler.removeCallbacks(mPopupTimer)
    }

    fun updateCompositionView() {
        if (isPopupWindowMovable == "once") {
            popupWindowPos = PopupPosition.fromString(ThemeManager.activeTheme.style.getString("layout/position"))
        }
        composition.root.measure(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        mPopupWindow.width = composition.root.measuredWidth
        mPopupWindow.height = composition.root.measuredHeight
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

    fun finishInput() {
        hideCompositionView()
        anchorView = null
    }
}
