/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupAction
import com.osfans.trime.ime.popup.PopupActionListener
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import timber.log.Timber
import kotlin.math.min
import kotlin.math.pow

/** 顯示[鍵盤][Keyboard]及[按鍵][Key]  */
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
    private val popup: PopupDelegate,
    private val service: TrimeInputMethodService,
) : KeyboardGestureFrame(context) {

    private val rime get() = RimeDaemon.getFirstSessionOrNull()!!
    private val keyTextSize = theme.generalStyle.keyTextSize
    private val labelTextSize =
        theme.generalStyle.keyLongTextSize
            .takeIf { it > 0 } ?: keyTextSize

    private val symbolTextSize = theme.generalStyle.symbolTextSize
    private val mShadowRadius = theme.generalStyle.shadowRadius
    private val mShadowColor = ColorManager.getColor("shadow_color")
    private val keyBorderColor = ColorManager.getColor("key_border_color")

    // Working variable
    private val originCoords = intArrayOf(0, 0)
    private val mKeys get() = keyboard.keys

    var keyboardActionListener: KeyboardActionListener? = null
    override val verticalCorrection: Int
        get() = theme.generalStyle.verticalCorrection
    private var mProximityThreshold = 0

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified version of
     * the depressed key. By default the preview is enabled.
     */
    private val showPreview by AppPrefs.defaultInstance().keyboard.popupOnKeyPress
    private val vibrateOnKeyRelease by AppPrefs.defaultInstance().keyboard.vibrateOnKeyRelease
    private val vibrateOnKeyRepeat by AppPrefs.defaultInstance().keyboard.vibrateOnKeyRepeat

    private val deletedTextBuffer = ArrayDeque<String>()

    /**
     * 是否允許距離校正 When enabled, calls to [KeyboardActionListener.onKey] will include key codes for
     * adjacent keys. When disabled, only the primary key code will be reported.
     */
    private val enableProximityCorrection = theme.generalStyle.proximityCorrection

    /** True if all keys should be drawn */
    private var invalidateAllKeys = false

    /** The keys that should be drawn  */
    private val invalidatedKeys = hashSetOf<Key>()

    /** The dirty region in the keyboard bitmap */
    private val dirtyRect = Rect()

    /** The keyboard bitmap for faster updates */
    private var drawingBuffer: Bitmap? = null

    /** The canvas for the above mutable keyboard bitmap  */
    private val drawingCanvas = Canvas()

    private val basePaint =
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

    private var labelEnter: String = theme.generalStyle.enterLabel.default

    fun onEnterKeyLabelUpdate(label: String) {
        labelEnter = label
    }

    private val popupActionListener: PopupActionListener by lazy { popup.listener }

    init {
        computeProximityThreshold(keyboard)
        invalidateAllKeys()

        onKeyActionListener = { keyIndex, behavior ->
            if (behavior == KeyBehavior.LONG_CLICK && hasPopupKeys(keyIndex)) {
                val popupKeys = mKeys.get(keyIndex).popup
                val bounds = getKeyBounds(keyIndex)
                popupActionListener.onPopupAction(
                    PopupAction.ShowKeyboardAction(keyIndex, popupKeys, bounds),
                )
                false
            } else {
                detectAndSendKey(keyIndex, behavior)
                true
            }
        }

        onKeySlideListener = { keyIndex, deltaX, x, y ->
            val key = mKeys.getOrNull(keyIndex)
            val ic = service.currentInputConnection

            when {
                key?.click?.isSlideCursor == true -> {
                    when {
                        deltaX > 0 -> keyboardActionListener?.onAction(KeyAction("Right"))
                        deltaX < 0 -> keyboardActionListener?.onAction(KeyAction("Left"))
                    }
                    true
                }
                key?.click?.isSlideDelete == true -> {
                    when {
                        deltaX < 0 -> {
                            val beforeText = ic.getTextBeforeCursor(1, 0) ?: ""
                            if (beforeText.isNotEmpty()) {
                                deletedTextBuffer.addFirst(beforeText.toString())
                                ic?.deleteSurroundingText(1, 0)
                            }
                            true
                        }
                        deltaX > 0 -> {
                            if (deletedTextBuffer.isNotEmpty()) {
                                ic?.commitText(deletedTextBuffer.removeFirst(), 1)
                            }
                            true
                        }
                    }
                }
            }
            false
        }

        onKeyStateListener = { keyIndex, behavior, isVisible, isPressed, isRepeating ->
            val key = mKeys.getOrNull(keyIndex)
            if (isPressed || (isRepeating && vibrateOnKeyRepeat)) keyboardActionListener?.onPress(key?.getCode(behavior) ?: 0, !isRepeating)
            if (!isRepeating) {
                if (isVisible) {
                    key?.onPressed()
                    invalidateKey(key)
                    if (showPreview) showPopup(keyIndex, behavior)
                } else {
                    key?.onReleased()
                    invalidateKey(key)
                    hidePopup(keyIndex)
                    if (behavior == KeyBehavior.LONG_CLICK || vibrateOnKeyRelease) keyboardActionListener?.onPress(key?.getCode(behavior) ?: 0, false)
                }
            }
        }

        onKeyReleaseListener = { keyIndex ->
            deletedTextBuffer.clear()
        }

        onPopupSelected = { keyIndex ->
            val triggerAction = PopupAction.TriggerAction(keyIndex)
            popupActionListener.onPopupAction(triggerAction)
            triggerAction.outAction?.let { action ->
                keyboardActionListener?.onAction(KeyAction(action))
            }
        }

        onPopupChangeFocus = { keyIndex, x, y ->
            val key = mKeys.getOrNull(keyIndex)
            if (key != null) {
                val relativeX = x - key.x.toFloat()
                val relativeY = y - key.y.toFloat()

                val changeFocusAction = PopupAction.ChangeFocusAction(keyIndex, relativeX, relativeY)
                popupActionListener.onPopupAction(changeFocusAction)
            }
        }
    }

    override fun getKeyIndex(x: Float, y: Float): Int = getKeyIndices(x.toInt(), y.toInt())
    override fun isKeyRepeatable(keyIndex: Int): Boolean = mKeys.getOrNull(keyIndex)?.click?.isRepeatable ?: false
    override fun isKeySlideCursor(keyIndex: Int): Boolean = mKeys.getOrNull(keyIndex)?.click?.isSlideCursor ?: false
    override fun isKeySlideDelete(keyIndex: Int): Boolean = mKeys.getOrNull(keyIndex)?.click?.isSlideDelete ?: false
    override fun hasAction(keyIndex: Int, behavior: KeyBehavior): Boolean = mKeys.getOrNull(keyIndex)?.hasAction(behavior) ?: false
    override fun hasPopupKeys(keyIndex: Int): Boolean = mKeys.getOrNull(keyIndex)?.popup?.isNotEmpty() == true

    private fun showPopup(keyIndex: Int, behavior: KeyBehavior = KeyBehavior.CLICK) {
        val key = mKeys.getOrNull(keyIndex) ?: return
        val bounds = getKeyBounds(keyIndex)
        val previewText = key.getPreviewText(behavior)
        val context = if (previewText.isNotEmpty()) {
            if (previewText.isIconFont) previewText else String(Character.toChars(previewText.codePointAt(0)))
        } else {
            ""
        }

        popupActionListener.onPopupAction(
            PopupAction.PreviewAction(keyIndex, context, bounds),
        )
    }

    private fun hidePopup(keyIndex: Int) {
        popupActionListener.onPopupAction(PopupAction.DismissAction(keyIndex))
    }

    private fun getKeyBounds(keyIndex: Int): Rect {
        val key = mKeys.getOrNull(keyIndex) ?: return Rect()
        val location = intArrayOf(0, 0)
        getLocationInWindow(location)
        return Rect(
            key.x + location[0],
            key.y + location[1],
            key.x + key.width + location[0],
            key.y + key.height + location[1],
        )
    }

    private fun setModifier(
        action: KeyAction,
        behavior: KeyBehavior,
    ): Boolean = setModifier(action.isShiftLock xor (behavior == KeyBehavior.LONG_CLICK), action.modifierKeyOnMask)

    private fun setModifier(
        on: Boolean,
        code: Int,
    ): Boolean = keyboard.clickModifierKey(on, code).also { if (it) invalidateAllKeys() }

    // 重置全部修饰键的状态(如果有锁定则不重置）
    private fun refreshModifier() {
        if (keyboard.refreshModifier()) {
            invalidateAllKeys()
        }
    }

    /**
     * 返回鍵盤是否爲大寫狀態
     */
    val isCapsOn: Boolean
        get() = keyboard.mShiftKey?.isOn ?: false

    public override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // Round up a little
        val fullWidth = keyboard.minWidth + paddingLeft + paddingRight
        val fullHeight = keyboard.height + paddingTop + paddingBottom
        val measuredWidth =
            if (MeasureSpec.getSize(widthMeasureSpec) < fullWidth + 10) {
                MeasureSpec.getSize(widthMeasureSpec)
            } else {
                fullWidth
            }
        setMeasuredDimension(measuredWidth, fullHeight)
    }

    /**
     * 計算水平和豎直方向的相鄰按鍵中心的平均距離的平方，這樣不需要做開方運算
     *
     * @param keyboard 鍵盤
     */
    private fun computeProximityThreshold(keyboard: Keyboard?) {
        if (keyboard == null && mKeys.isEmpty()) return
        val dimensionSum = mKeys.sumOf { key -> min(key.width, key.height) + key.gap }
        if (dimensionSum < 0) return
        mProximityThreshold = (dimensionSum * Keyboard.SEARCH_DISTANCE / mKeys.size).pow(2).toInt() // Square it
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (canvas.isHardwareAccelerated) {
            onDrawKeyboard(canvas)
            return
        }
        val bufferNeedsUpdates = invalidateAllKeys || invalidatedKeys.isNotEmpty()
        if (bufferNeedsUpdates || drawingBuffer == null) {
            if (maybeAllocateDrawingBuffer()) {
                invalidateAllKeys = true
                drawingCanvas.setBitmap(drawingBuffer)
            }
            onDrawKeyboard(drawingCanvas)
        }
        canvas.drawBitmap(drawingBuffer!!, 0.0f, 0.0f, null)
    }

    private fun maybeAllocateDrawingBuffer(): Boolean {
        if (width == 0 || height == 0) {
            return false
        }
        if (drawingBuffer != null && drawingBuffer!!.width == width && drawingBuffer!!.height == height) {
            return false
        }
        freeDrawingBuffer()
        drawingBuffer = createBitmap(width, height)
        return true
    }

    private fun freeDrawingBuffer() {
        drawingCanvas.setBitmap(null)
        drawingCanvas.setMatrix(null)
        if (drawingBuffer != null) {
            drawingBuffer!!.recycle()
            drawingBuffer = null
        }
    }

    private fun onDrawKeyboard(canvas: Canvas) {
        val paint = basePaint
        val drawAllKeys = invalidateAllKeys || invalidatedKeys.isEmpty()
        val isHardwareAccelerated = canvas.isHardwareAccelerated
        if (drawAllKeys || isHardwareAccelerated) {
            if (!isHardwareAccelerated && background != null) {
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                background.draw(canvas)
            }
            for (key in mKeys) {
                onDrawKey(key, canvas, paint)
            }
        } else {
            for (key in invalidatedKeys) {
                if (!mKeys.contains(key)) continue
                if (background != null) {
                    val x = key.x + paddingLeft
                    val y = key.y + paddingTop
                    dirtyRect.set(x, y, x + key.width, y + key.height)
                    canvas.withClip(dirtyRect) {
                        drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                        background.draw(this)
                    }
                }
                onDrawKey(key, canvas, paint)
            }
        }

        invalidatedKeys.clear()
        invalidateAllKeys = false
    }

    private fun onDrawKey(
        key: Key,
        canvas: Canvas,
        paint: Paint,
    ) {
        val keyDrawX = (key.x + paddingLeft).toFloat()
        val keyDrawY = (key.y + paddingTop).toFloat()
        canvas.translate(keyDrawX, keyDrawY)

        drawKeyBackground(key, canvas)
        drawKeyLabel(key, canvas, paint)
        drawKeySymbol(key, canvas, paint)

        canvas.translate(-keyDrawX, -keyDrawY)
    }

    private fun drawKeyBackground(
        key: Key,
        canvas: Canvas,
    ) {
        val keyBackground = key.getBackgroundDrawable()
        if (keyBackground != null) {
            if (keyBackground is GradientDrawable) {
                (key.roundCorner ?: keyboard.roundCorner).takeIf { it > 0f }?.let {
                    keyBackground.cornerRadius = dp(it)
                }
                (key.keyBorder ?: keyboard.keyBorder).takeIf { it > 0 }?.let {
                    keyBackground.setStroke(dp(it), keyBorderColor)
                }
            }
            onDrawKeyBackground(key, canvas, keyBackground)
        }
    }

    private fun drawKeyLabel(
        key: Key,
        canvas: Canvas,
        paint: Paint,
    ) {
        val keyLabel = key.getLabel().let {
            if (it == "enter_labels") labelEnter else it
        }
        if (keyLabel.isEmpty()) return

        if (keyLabel.isIconFont) {
            val size = if (key.keyTextSize > 0) sp(key.keyTextSize) else sp(if (keyLabel.length > 1) labelTextSize else keyTextSize)
            drawIconLabel(key, keyLabel, canvas, size, centered = true)
        } else {
            drawTextLabel(key, keyLabel, canvas, paint)
        }
    }

    private fun drawIconLabel(
        key: Key,
        label: String,
        canvas: Canvas,
        size: Float,
        color: Int = key.getTextColor(),
        offsetX: Float = key.keyTextOffsetX,
        offsetY: Float = key.keyTextOffsetY,
        centered: Boolean = true,
    ) {
        val iconSize = size / resources.displayMetrics.density
        val icon = IconicsDrawable(context, label.toIconName()).apply {
            sizeDp = iconSize.toInt()
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }

        val centerX = key.width / 2f - icon.intrinsicWidth / 2f
        val centerY = if (centered) key.height / 2f else key.height.toFloat()
        val x = (centerX + sp(offsetX)).toInt()
        val y = (centerY - icon.intrinsicHeight / 2f + sp(offsetY)).toInt()

        icon.setBounds(x, y, x + icon.intrinsicWidth, y + icon.intrinsicHeight)
        icon.draw(canvas)
    }

    private fun drawTextLabel(
        key: Key,
        label: String,
        canvas: Canvas,
        paint: Paint,
    ) {
        paint.typeface = FontManager.getTypeface("key_font")
        paint.textSize = if (key.keyTextSize > 0) sp(key.keyTextSize) else sp(if (label.length > 1) labelTextSize else keyTextSize)
        paint.color = key.getTextColor()

        val centerX = key.width * 0.5f
        val centerY = key.height * 0.5f
        val labelX = centerX + sp(key.keyTextOffsetX)
        val labelBaseline = centerY + sp(key.keyTextOffsetY)

        if (mShadowRadius > 0f) {
            paint.setShadowLayer(mShadowRadius, 0f, 0f, mShadowColor)
        }

        val adjustmentY = (paint.textSize - paint.descent()) / 2f
        canvas.drawText(label, labelX, labelBaseline + adjustmentY, paint)

        paint.clearShadowLayer()
    }

    private fun drawKeySymbol(
        key: Key,
        canvas: Canvas,
        paint: Paint,
    ) {
        val showSymbol = rime.run { !getRuntimeOption("_hide_key_symbol") }
        val showHint = rime.run { !getRuntimeOption("_hide_key_hint") }
        if (!showSymbol && !showHint) return

        val centerX = key.width / 2f
        val symbolSize = listOf(key.symbolTextSize, symbolTextSize).firstOrNull { it > 0f }?.let { sp(it) } ?: sp(symbolTextSize)

        fun drawSymbolText(label: String, offsetX: Float, offsetY: Float, isTop: Boolean) {
            paint.apply {
                typeface = FontManager.getTypeface("symbol_font")
                textSize = symbolSize
                color = key.getSymbolColor()
            }
            val fontMetrics = paint.fontMetrics
            val y = if (isTop) -fontMetrics.top + sp(offsetY) else key.height - fontMetrics.bottom + sp(offsetY)
            canvas.drawText(label, centerX + sp(offsetX), y, paint)
        }

        if (showSymbol && key.symbolLabel.isNotEmpty()) {
            val label = key.symbolLabel
            if (label.isIconFont) {
                drawIconLabel(key, label, canvas, symbolSize, key.getSymbolColor(), key.keySymbolOffsetX, key.keySymbolOffsetY, centered = false)
            } else {
                drawSymbolText(label, key.keySymbolOffsetX, key.keySymbolOffsetY, isTop = true)
            }
        }

        if (showHint && key.hint.isNotEmpty()) {
            val label = key.hint
            if (label.isIconFont) {
                drawIconLabel(key, label, canvas, symbolSize, key.getSymbolColor(), key.keyHintOffsetX, key.keyHintOffsetY, centered = false)
            } else {
                drawSymbolText(label, key.keyHintOffsetX, key.keyHintOffsetY, isTop = false)
            }
        }
    }

    private fun onDrawKeyBackground(
        key: Key,
        canvas: Canvas,
        background: Drawable,
    ) {
        val padding = Rect().also { background.getPadding(it) }
        val bgWidth = key.width + padding.left + padding.right
        val bgHeight = key.height + padding.top + padding.bottom
        val bgX = -padding.left.toFloat()
        val bgY = -padding.top.toFloat()
        background.setBounds(0, 0, bgWidth, bgHeight)
        canvas.translate(bgX, bgY)
        background.draw(canvas)
        canvas.translate(-bgX, -bgY)
    }

    private fun getKeyIndices(
        x: Int,
        y: Int,
    ): Int {
        var primaryIndex = -1
        var closestKey = -1
        var closestKeyDist = mProximityThreshold + 1
        IntArray(MAX_NEARBY_KEYS).fill(Int.MAX_VALUE)
        val nearestKeyIndices = keyboard.getNearestKeys(x, y)
        for (nearestKeyIndex in nearestKeyIndices!!) {
            val key = mKeys[nearestKeyIndex]
            val isInside = key.isInside(x, y)
            if (isInside) {
                primaryIndex = nearestKeyIndex
            }
            val dist = key.squaredDistanceFrom(x, y)
            if (enableProximityCorrection && dist < mProximityThreshold || isInside) {
                // Find insertion point
                if (dist < closestKeyDist) {
                    closestKeyDist = dist
                    closestKey = nearestKeyIndex
                }
            }
        }
        if (primaryIndex == -1) {
            primaryIndex = closestKey
        }
        return primaryIndex
    }

    private fun releaseKey(code: Int) {
        Timber.d("releaseKey: keyCode=$code")
        keyboardActionListener?.onRelease(code)
    }

    private val hookShiftArrow by AppPrefs.defaultInstance().keyboard.hookShiftArrow

    fun isHookShiftArrow(keyCode: Int): Boolean {
        if (!hookShiftArrow) return false

        return when (keyCode) {
            in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT -> true
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> true
            else -> false
        }
    }

    private fun detectAndSendKey(
        index: Int,
        behavior: KeyBehavior = KeyBehavior.CLICK,
    ) {
        val key = mKeys.getOrNull(index) ?: return
        val action = key.getAction(behavior) ?: return
        Timber.d("detectAndSendKey: label=${key.getLabel()}, code=${action.code}, type=$behavior, modifier=${action.isModifierKey}")

        if (action.isModifierKey) {
            setModifier(action, behavior)
            return
        }

        keyboardActionListener?.onAction(action)
        releaseKey(action.code)
        if (!isHookShiftArrow(action.code)) refreshModifier()
        return
    }

    /**
     * Requests a redraw of the entire keyboard. Calling [invalidate] is not sufficient because
     * the keyboard renders the keys to an off-screen buffer and an invalidate() only draws the cached
     * buffer.
     *
     * @see invalidateKey
     */
    fun invalidateAllKeys() {
        Timber.d("invalidateAllKeys")
        invalidatedKeys.clear()
        invalidateAllKeys = true
        invalidate()
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only one
     * key is changing it's content. Any changes that affect the position or size of the key may not
     * be honored.
     *
     * @param key the key in the attached [Keyboard].
     * @see invalidateAllKeys
     */
    private fun invalidateKey(key: Key?) {
        if (invalidateAllKeys || key == null) return
        invalidatedKeys.add(key)
        invalidate()
    }

    fun onDetach() {
        popup.dismissAll()
        freeDrawingBuffer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        freeDrawingBuffer()
    }

    companion object {
        private const val MAX_NEARBY_KEYS = 12
    }
}
