package com.osfans.trime.ime.text

import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.PopupWindow
import com.blankj.utilcode.util.BarUtils
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.Theme.Companion.get
import com.osfans.trime.ime.enums.PopupPosition
import com.osfans.trime.ime.enums.PopupPosition.Companion.fromString
import com.osfans.trime.util.dp2px
import timber.log.Timber

class CompositionPopupWindow() {
    var isPopupWindowEnabled = true // 顯示懸浮窗口

    private var isPopupWindowMovable: String? = null // 悬浮窗口是否可移動

    private var popupWindowX = 0
    private var popupWindowY = 0 // 悬浮床移动座標

    private var popupMargin = 0 // 候選窗與邊緣空隙

    private var popupMarginH = 0 // 悬浮窗与屏幕两侧的间距

    private var popupWindowPos: PopupPosition? = null // 悬浮窗口彈出位置

    private var mPopupWindow: PopupWindow? = null

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

                val minX: Int = popupMarginH
                val minY: Int = popupMargin
                val maxX: Int = anchor.getWidth() - mPopupWindow!!.width - minX
                val maxY = candidateLocation[1] - mPopupWindow!!.height - minY
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
                    x = Math.min(maxX, x)
                    x = Math.max(minX, x)
                    when (popupWindowPos) {
                        PopupPosition.LEFT, PopupPosition.RIGHT ->
                            y =
                                mPopupRectF.bottom.toInt() + popupMargin

                        PopupPosition.LEFT_UP, PopupPosition.RIGHT_UP ->
                            y =
                                mPopupRectF.top.toInt() - mPopupWindow!!.height - popupMargin

                        else -> Timber.wtf("UNREACHABLE BRANCH")
                    }
                    y = Math.min(maxY, y)
                    y = Math.max(minY, y)
                }
                y -= BarUtils.getStatusBarHeight() // 不包含狀態欄
                if (!mPopupWindow!!.isShowing) {
                    mPopupWindow!!.showAtLocation(anchorView, Gravity.START or Gravity.TOP, x, y)
                } else {
                    mPopupWindow!!.update(x, y, mPopupWindow!!.width, mPopupWindow!!.height)
                }
            }
        }

    fun init(
        view: View,
        anchorView: View,
    ) {
        destroy()
        mPopupWindow = PopupWindow(view)
        mPopupWindow!!.isClippingEnabled = false
        mPopupWindow!!.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            mPopupWindow!!.windowLayoutType =
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        }

        this.anchorView = anchorView
        hideCompositionView()
    }

    fun loadConfig(
        theme: Theme,
        appPrefs: AppPrefs,
    ) {
        popupWindowPos = fromString(theme.style.getString("layout/position"))
        isPopupWindowMovable = theme.style.getString("layout/movable")
        popupMargin = dp2px(theme.style.getFloat("layout/spacing")).toInt()
        popupMarginH = dp2px(theme.style.getFloat("layout/real_margin")).toInt()
        isPopupWindowEnabled =
            appPrefs.keyboard.popupWindowEnabled && theme.style.getObject("window") != null
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
        mPopupWindow!!.update(popupWindowX, popupWindowY, -1, -1, true)
    }

    fun hideCompositionView() {
        if (isPopupWindowMovable != null && isPopupWindowMovable.equals("once")) {
            popupWindowPos = fromString(get().style.getString("layout/position"))
        }
        mPopupWindow?.let {
            if (it.isShowing) {
                it.dismiss()
                mPopupHandler.removeCallbacks(mPopupTimer)
            }
        }
    }

    fun updateCompositionView(
        width: Int,
        height: Int,
    ) {
        mPopupWindow?.width = width
        mPopupWindow?.height = height
        mPopupHandler.post(mPopupTimer)
    }

    fun setThemeStyle(
        elevation: Float,
        drawable: Drawable?,
    ) {
        if (drawable != null) mPopupWindow?.setBackgroundDrawable(drawable)
        mPopupWindow?.elevation = elevation
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
                mPopupRectF.top = Math.min(startCharRectF.top, endCharRectF.top)
                mPopupRectF.left = Math.min(startCharRectF.left, endCharRectF.left)
                mPopupRectF.bottom = Math.max(startCharRectF.bottom, endCharRectF.bottom)
                mPopupRectF.right = Math.max(startCharRectF.right, endCharRectF.right)
            }
            cursorAnchorInfo.matrix.mapRect(mPopupRectF)
        }
    }

    fun destroy() {
        mPopupWindow?.dismiss()
        mPopupWindow = null
        anchorView = null
    }
}
