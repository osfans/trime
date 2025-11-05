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
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.preview.KeyPreviewChoreographer
import com.osfans.trime.util.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val keyPreviewChoreographer: KeyPreviewChoreographer,
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
    private val showPreview by AppPrefs.defaultInstance().keyboard.popupKeyPressEnabled

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

    init {
        computeProximityThreshold(keyboard)
        invalidateAllKeys()

        onKeyActionListener = { keyIndex, behavior, repeat ->
            if (!repeat) keyboardActionListener?.onPress(keyIndex)
            detectAndSendKey(keyIndex, behavior)
            true
        }

        onKeySlideListener = { keyIndex, deltaX, x, y ->
            val key = mKeys[keyIndex]
            val ic = service.currentInputConnection

            when {
                key.click?.isSlideCursor == true -> {
                    when {
                        deltaX > 0 -> keyboardActionListener?.onAction(KeyAction("Right"))
                        deltaX < 0 -> keyboardActionListener?.onAction(KeyAction("Left"))
                    }
                    true
                }
                key.click?.isSlideDelete == true -> {
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

        onKeyPreviewListener = { keyIndex, behavior, showing ->
            val key = mKeys[keyIndex]
            if (showing) {
                key.onPressed()
                invalidateKey(key)
                if (showPreview) showKeyPreview(key, behavior)
            } else {
                key.onReleased()
                invalidateKey(key)
                if (showPreview) dismissKeyPreview(key)
            }
        }

        onKeyReleaseListener = {
            deletedTextBuffer.clear()
        }
    }

    override fun getKeyIndex(x: Float, y: Float): Int = getKeyIndices(x.toInt(), y.toInt())
    override fun isKeyRepeatable(keyIndex: Int): Boolean = mKeys.getOrNull(keyIndex)?.click?.isRepeatable ?: false
    override fun isKeySlideCursor(keyIndex: Int): Boolean = mKeys.getOrNull(keyIndex)?.click?.isSlideCursor ?: false
    override fun isKeySlideDelete(keyIndex: Int): Boolean = mKeys.getOrNull(keyIndex)?.click?.isSlideDelete ?: false
    override fun hasAction(keyIndex: Int, behavior: KeyBehavior): Boolean = mKeys.getOrNull(keyIndex)?.hasAction(behavior) ?: false

    private val lifecycleScope by lazy {
        findViewTreeLifecycleOwner()?.lifecycleScope
    }

    private var removePreviewJob: Job? = null

    private fun showKeyPreview(
        key: Key,
        behavior: KeyBehavior,
    ) {
        getLocationInWindow(originCoords)
        keyPreviewChoreographer.placeAndShowKeyPreview(key, key.getPreviewText(behavior), width, originCoords)
    }

    private fun dismissKeyPreviewWithoutDelay(key: Key) {
        keyPreviewChoreographer.dismissKeyPreview(key)
        invalidateKey(key)
    }

    private fun dismissKeyPreview(key: Key) {
        if (isHardwareAccelerated) {
            keyPreviewChoreographer.dismissKeyPreview(key)
            return
        }
        handleRemovePreviewJob(key)
    }

    private fun handleRemovePreviewJob(key: Key) {
        removePreviewJob?.cancel()
        // NOTE: hide without delay when the view is destroyed
        val scope = lifecycleScope ?: return dismissKeyPreviewWithoutDelay(key)
        removePreviewJob = scope.launch {
            delay(DELAY_AFTER_PREVIEW)
            dismissKeyPreviewWithoutDelay(key)
        }
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

        val keyBackground = key.getBackgroundDrawable()
        if (keyBackground != null) {
            if (keyBackground is GradientDrawable) {
                floatArrayOf(key.roundCorner, keyboard.roundCorner)
                    .firstOrNull { it > 0f }
                    ?.let { keyBackground.cornerRadius = dp(it) }
            }
            onDrawKeyBackground(key, canvas, keyBackground)
        }

        // Switch the character to uppercase if shift is pressed

        val centerX = key.width * 0.5f
        val centerY = key.height * 0.5f
        val keyLabel =
            key.getLabel().let {
                if (it == "enter_labels") {
                    labelEnter
                } else {
                    it
                }
            }
        if (keyLabel.isNotEmpty()) {
            paint.typeface = FontManager.getTypeface("key_font")
            // For characters, use large font. For labels like "Done", use small font.
            paint.textSize =
                if (key.keyTextSize > 0) {
                    sp(key.keyTextSize)
                } else {
                    sp(if (keyLabel.length > 1) labelTextSize else keyTextSize)
                }

            val labelX = centerX + sp(key.keyTextOffsetX)
            val labelBaseline = centerY + sp(key.keyTextOffsetY)

            paint.color = key.getTextColor()

            // Draw a drop shadow for the text
            if (mShadowRadius > 0f) {
                paint.setShadowLayer(mShadowRadius, 0f, 0f, mShadowColor)
            } else {
                paint.clearShadowLayer()
            }

            val adjustmentY = paint.run { textSize - descent() } / 2f
            // Draw the text
            canvas.drawText(keyLabel, labelX, labelBaseline + adjustmentY, paint)
            // Turn off drop shadow
            paint.clearShadowLayer()

            val showKeySymbol = rime.run { !getRuntimeOption("_hide_key_symbol") }
            val showKeyHint = rime.run { !getRuntimeOption("_hide_key_hint") }
            if (showKeySymbol || showKeyHint) {
                paint.typeface = FontManager.getTypeface("symbol_font")
                floatArrayOf(key.symbolTextSize, symbolTextSize)
                    .firstOrNull { it > 0f }
                    ?.let { paint.textSize = sp(it) }
                paint.color = key.getSymbolColor()

                val fontMetrics = paint.fontMetrics

                val symbolLabel = key.symbolLabel
                if (showKeySymbol && symbolLabel.isNotEmpty()) {
                    val symbolX = centerX + sp(key.keySymbolOffsetX)
                    val symbolBaseline = -fontMetrics.top
                    canvas.drawText(symbolLabel, symbolX, symbolBaseline + sp(key.keySymbolOffsetY), paint)
                }
                val hintLabel = key.hint
                if (showKeyHint && hintLabel.isNotEmpty()) {
                    val hintX = centerX + sp(key.keyHintOffsetX)
                    val hintBaseline = -fontMetrics.bottom
                    canvas.drawText(hintLabel, hintX, hintBaseline + key.height + sp(key.keyHintOffsetY), paint)
                }
            }
        }
        canvas.translate(-keyDrawX, -keyDrawY)
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
        removePreviewJob?.cancel()
        removePreviewJob = null
        freeDrawingBuffer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        freeDrawingBuffer()
    }

    companion object {
        private const val DELAY_AFTER_PREVIEW = 100L
        private const val MAX_NEARBY_KEYS = 12
    }
}
