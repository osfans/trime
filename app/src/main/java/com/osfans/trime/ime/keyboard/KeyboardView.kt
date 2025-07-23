// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
import android.os.SystemClock
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.preview.KeyPreviewChoreographer
import com.osfans.trime.util.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import timber.log.Timber
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 顯示[鍵盤][Keyboard]及[按鍵][Key]  */
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
    private val keyPreviewChoreographer: KeyPreviewChoreographer,
) : View(context) {
    private val rime = RimeDaemon.getFirstSessionOrNull()!!
    private var mCurrentKeyIndex = NOT_A_KEY
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
    private val mVerticalCorrection = theme.generalStyle.verticalCorrection
    private var mProximityThreshold = 0

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified version of
     * the depressed key. By default the preview is enabled.
     */
    private val showPreview by AppPrefs.defaultInstance().keyboard.popupKeyPressEnabled
    private var mLastX = 0
    private var mLastY = 0
    private var mStartX = 0
    private var mStartY = 0
    private var touchX0 = 0
    private var touchY0 = 0
    private var touchOnePoint = false

    /**
     * 是否允許距離校正 When enabled, calls to [KeyboardActionListener.onKey] will include key codes for
     * adjacent keys. When disabled, only the primary key code will be reported.
     */
    private val enableProximityCorrection = theme.generalStyle.proximityCorrection
    private var mDownTime: Long = 0
    private var mLastMoveTime: Long = 0
    private var mLastKey = 0
    private var mLastCodeX = 0
    private var mLastCodeY = 0
    private var mCurrentKey = NOT_A_KEY
    private var mDownKey = NOT_A_KEY
    private var mLastKeyTime: Long = 0
    private var mCurrentKeyTime: Long = 0
    private var mLastUpTime: Long = 0
    private val mKeyIndices = IntArray(12)
    private var mRepeatKeyIndex = -1
    private var mAbortKey = true
    private val mDisambiguateSwipe = false

    // Variables for dealing with multiple pointers
    private var mOldPointerCount = 1
    private val mComboCodes = IntArray(10)
    private var mComboCount = 0
    private var mComboMode = false
    private val mDistances = IntArray(MAX_NEARBY_KEYS)

    // For multi-tap
    private var mLastSentIndex = -1
    private var mLastTapTime: Long = -1

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

    private val lifecycleScope by lazy {
        findViewTreeLifecycleOwner()?.lifecycleScope!!
    }

    private var longPressJob: Job? = null
    private var repeatJob: Job? = null
    private var removePreviewJob: Job? = null

    private fun handleLongPressJob() {
        longPressJob?.cancel()
        longPressJob =
            lifecycleScope.launch {
                delay(longPressTimeout.toLong())
                InputFeedbackManager.keyPressVibrate(this@KeyboardView, true)
                openPopupIfRequired()
            }
    }

    private fun handleRepeatJob() {
        repeatJob?.cancel()
        repeatJob =
            lifecycleScope.launch {
                delay(longPressTimeout.toLong())
                var lastTriggerTime: Long
                while (isActive && isEnabled) {
                    lastTriggerTime = SystemClock.uptimeMillis()
                    if (repeatKey()) {
                        val t = lastTriggerTime + repeatInterval - SystemClock.uptimeMillis()
                        if (t > 0) delay(t)
                    }
                }
            }
    }

    private fun handleRemovePreviewJob(key: Key) {
        removePreviewJob?.cancel()
        removePreviewJob =
            lifecycleScope.launch {
                delay(DELAY_AFTER_PREVIEW)
                dismissKeyPreviewWithoutDelay(key)
            }
    }

    init {
        computeProximityThreshold(keyboard)
        invalidateAllKeys()
    }

    private val swipeEnabled by AppPrefs.defaultInstance().keyboard.swipeEnabled
    private val swipeTravel by AppPrefs.defaultInstance().keyboard.swipeTravel // threshold distance
    private val swipeVelocity by AppPrefs.defaultInstance().keyboard.swipeVelocity // threshold velocity

    private val customSwipeTracker = CustomSwipeTracker()
    private val customGestureDetector =
        GestureDetector(
            context,
            object : SimpleOnGestureListener() {
                override fun onFling(
                    me1: MotionEvent?,
                    me2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                        /*
                    Judgment basis: the sliding distance exceeds the threshold value,
                    and the sliding distance on the corresponding axis is less than
                    the sliding distance on the other coordinate axis.
                         */
                    if (mDownKey == -1) return false
                    val deltaX = me2.x - me1!!.x // distance X
                    val deltaY = me2.y - me1.y // distance Y
                    val absX = abs(deltaX) // absolute value of distance X
                    val absY = abs(deltaY) // absolute value of distance Y
                    customSwipeTracker.computeCurrentVelocity(10)
                    val endingVelocityX: Float = customSwipeTracker.xVelocity
                    val endingVelocityY: Float = customSwipeTracker.yVelocity
                    var sendDownKey = false
                    var behavior = KeyBehavior.CLICK
                    //  In my tests velocity always smaller than 400
                    //  so I don't really why we need to compare velocity here,
                    //  as default value of getSwipeVelocity() is 800
                    //  and default value of getSwipeVelocityHi() is 25000,
                    //  so for most of the users that judgment is always true
                    if ((deltaX > swipeTravel || velocityX > swipeVelocity) &&
                        (
                            absY < absX ||
                                (
                                    deltaY > 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_UP] == null
                                ) ||
                                (
                                    deltaY < 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_DOWN] == null
                                )
                        ) &&
                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_RIGHT] != null
                    ) {
                        // I should have implement mDisambiguateSwipe as a config option, but the logic
                        // here is really weird, and I don't really know
                        // when it is enabled what should be the behavior, so I just left it always false.
                        // endingVelocityX and endingVelocityY seems always > 0 but velocityX and
                        // velocityY can be negative.
                        if (mDisambiguateSwipe && endingVelocityX > velocityX / 4) {
                            return true
                        } else {
                            sendDownKey = true
                            behavior = KeyBehavior.SWIPE_RIGHT
                        }
                    } else if ((deltaX < -swipeTravel || velocityX < -swipeVelocity) &&
                        (
                            absY < absX ||
                                (
                                    deltaY > 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_UP] == null
                                ) ||
                                (
                                    deltaY < 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_DOWN] == null
                                )
                        ) &&
                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_LEFT] != null
                    ) {
                        if (mDisambiguateSwipe && endingVelocityX < velocityX / 4) {
                            return true
                        } else {
                            sendDownKey = true
                            behavior = KeyBehavior.SWIPE_LEFT
                        }
                    } else if ((deltaY < -swipeTravel || velocityY < -swipeVelocity) &&
                        (
                            absX < absY ||
                                (
                                    deltaX > 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_RIGHT] == null
                                ) ||
                                (
                                    deltaX < 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_LEFT] == null
                                )
                        ) &&
                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_UP] != null
                    ) {
                        if (mDisambiguateSwipe && endingVelocityY < velocityY / 4) {
                            return true
                        } else {
                            sendDownKey = true
                            behavior = KeyBehavior.SWIPE_UP
                        }
                    } else if ((deltaY > swipeTravel || velocityY > swipeVelocity) &&
                        (
                            absX < absY ||
                                (
                                    deltaX > 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_RIGHT] == null
                                ) ||
                                (
                                    deltaX < 0 &&
                                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_LEFT] == null
                                )
                        ) &&
                        mKeys[mDownKey].keyActions[KeyBehavior.SWIPE_DOWN] != null
                    ) {
                        if (mDisambiguateSwipe && endingVelocityY > velocityY / 4) {
                            return true
                        } else {
                            sendDownKey = true
                            behavior = KeyBehavior.SWIPE_DOWN
                        }
                    } else {
                        Timber.d("swipeDebug.onFling fail , dY=$deltaY, vY=$velocityY, eVY=$endingVelocityY, travel=$swipeTravel")
                    }
                    if (sendDownKey) {
                        Timber.d("initGestureDetector: sendDownKey")
                        showPreview(mDownKey, behavior)
                        detectAndSendKey(mDownKey, mStartX, mStartY, me1.eventTime, behavior)
                        return true
                    }
                    return false
                }
            },
        ).apply { setIsLongpressEnabled(false) }

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

    /**
     * 设置键盘修饰键的状态
     *
     * @param key 按下的修饰键(非组合键）
     * @param behavior 按键行为(单击、长按等)
     * @return
     */
    private fun setModifier(
        key: Key,
        behavior: KeyBehavior,
    ): Boolean = setModifier(key.isShiftLock xor (behavior == KeyBehavior.LONG_CLICK), key.modifierKeyOnMask)

    private fun setModifier(
        on: Boolean,
        code: Int,
    ): Boolean = keyboard.clikModifierKey(on, code).also { if (it) invalidateAllKeys() }

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

        // debug: show touch points
//        paint.alpha = 128
//        paint.color = -0x10000
//        canvas.drawCircle(mStartX.toFloat(), mStartY.toFloat(), 3f, paint)
//        canvas.drawLine(mStartX.toFloat(), mStartY.toFloat(), mLastX.toFloat(), mLastY.toFloat(), paint)
//        paint.color = -0xffff01
//        canvas.drawCircle(mLastX.toFloat(), mLastY.toFloat(), 3f, paint)
//        paint.color = -0xff0100
//        canvas.drawCircle((mStartX + mLastX) / 2f, (mStartY + mLastY) / 2f, 2f, paint)
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
        mDistances.fill(Int.MAX_VALUE)
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
        Timber.d("releaseKey: keyCode=$code, comboMode=$mComboMode, comboCount=$mComboCount")
        if (mComboMode) {
            if (mComboCount > 9) mComboCount = 9
            mComboCodes[mComboCount++] = code
        } else {
            keyboardActionListener?.onRelease(code)
            if (mComboCount > 0) {
                for (i in 0 until mComboCount) {
                    keyboardActionListener?.onRelease(mComboCodes[i])
                }
                mComboCount = 0
            }
        }
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
        x: Int,
        y: Int,
        eventTime: Long,
        behavior: KeyBehavior = KeyBehavior.CLICK,
    ) {
        Timber.d("detectAndSendKey: index=$index, x=$x, y=$y, type=$behavior, mKeys.size=${mKeys.size}")
        if (index in mKeys.indices) {
            val key = mKeys[index]
            if (key.isModifierKey && !key.sendBindings(behavior)) {
                Timber.d("detectAndSendKey: ModifierKey, key.getEvent, keyLabel=${key.getLabel()}")
                setModifier(key, behavior)
            } else {
                if (key.click!!.isRepeatable) {
                    if (behavior > KeyBehavior.CLICK) mAbortKey = true
                    if (!key.hasAction(behavior)) return
                }
                val code = key.getCode(behavior)
                // TextEntryState.keyPressedAt(key, x, y);
                // getKeyIndices(x, y, codes); // 这里实际上并没有生效
                Timber.d("detectAndSendKey: onEvent, code=$code, key.getEvent")
                // 可以在这里把 mKeyboard.getModifer() 获取的修饰键状态写入event里
                key.getAction(behavior)?.let { keyboardActionListener?.onAction(it) }
                releaseKey(code)
                Timber.d("detectAndSendKey: refreshModifier")
                if (!isHookShiftArrow(code)) {
                    refreshModifier()
                }
            }
            mLastSentIndex = index
            mLastTapTime = eventTime
        }
    }

    private fun showPreview(
        keyIndex: Int,
        behavior: KeyBehavior = KeyBehavior.COMPOSING,
    ) {
        val oldKeyIndex = mCurrentKeyIndex
        mCurrentKeyIndex = keyIndex
        // Release the old key and press the new key
        val keys = mKeys
        if (oldKeyIndex != mCurrentKeyIndex) {
            keys.getOrNull(oldKeyIndex)?.let { oldKey ->
                oldKey.onReleased()
                invalidateKey(oldKey)
                if (showPreview) dismissKeyPreview(oldKey)
            }
            keys.getOrNull(mCurrentKeyIndex)?.let { newKey ->
                newKey.onPressed()
                invalidateKey(newKey)
                if (showPreview) showKeyPreview(newKey, behavior)
            }
        }
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

    private fun openPopupIfRequired(): Boolean {
        // Check if we have a popup layout specified first.
        if (mCurrentKey !in mKeys.indices) {
            return false
        }
        showPreview(mCurrentKey, KeyBehavior.LONG_CLICK)
        val popupKey = mKeys[mCurrentKey]
        return onLongPress(popupKey).also {
            if (it) {
                mAbortKey = true
                showPreview(NOT_A_KEY)
            }
        }
    }

    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated with
     * this key through the attributes popupLayout and popupCharacters.
     *
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the method
     * on the base class if the subclass doesn't wish to handle the call.
     */
    private fun onLongPress(popupKey: Key): Boolean {
        popupKey.longClick?.let {
            cancelAllJobs()
            mAbortKey = true
            keyboardActionListener?.onAction(it)
            releaseKey(it.code)
            if (!isHookShiftArrow(it.code)) {
                refreshModifier()
            }
            return true
        }
        if (popupKey.isModifierKey && !popupKey.sendBindings(KeyBehavior.LONG_CLICK)) {
            setModifier(popupKey, KeyBehavior.LONG_CLICK)
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        // Convert multi-pointer up/down events to single up/down events to
        // deal with the typical multi-pointer behavior of two-thumb typing
        val index = me.actionIndex
        val pointerCount = me.pointerCount
        val action = me.actionMasked
        var result: Boolean
        val now = me.eventTime
        mComboMode = false
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_CANCEL) {
            mComboCount = 0
        } else if (pointerCount > 1 || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            mComboMode = true
        }
        if (action == MotionEvent.ACTION_UP) {
            Timber.d("swipeDebug.onTouchEvent ?, action = ACTION_UP")
        }
        if (action == MotionEvent.ACTION_POINTER_UP || mOldPointerCount > 1 && action == MotionEvent.ACTION_UP) {
            // 並擊鬆開前的虛擬按鍵事件
            val ev =
                MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_POINTER_DOWN,
                    me.getX(index),
                    me.getY(index),
                    me.metaState,
                )
            result = onModifiedTouchEvent(ev)
            ev.recycle()
            Timber.d("\t<TrimeInput>\tonTouchEvent()\tactionUp done")
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // 並擊中的按鍵事件，需要按鍵提示
            val ev =
                MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_DOWN,
                    me.getX(index),
                    me.getY(index),
                    me.metaState,
                )
            result = onModifiedTouchEvent(ev)
            ev.recycle()
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tactionDown done")
        } else {
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tonModifiedTouchEvent")
            result = onModifiedTouchEvent(me)
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tnot actionDown done")
        }
        if (action != MotionEvent.ACTION_MOVE) mOldPointerCount = pointerCount
        performClick()
        return result
    }

    private val longPressTimeout by AppPrefs.defaultInstance().keyboard.longPressTimeout
    private val repeatInterval by AppPrefs.defaultInstance().keyboard.repeatInterval

    private fun onModifiedTouchEvent(me: MotionEvent): Boolean {
        // final int pointerCount = me.getPointerCount();
        val index = me.actionIndex
        var touchX = me.getX(index).toInt() - paddingLeft
        var touchY = me.getY(index).toInt() - paddingTop
        if (touchY >= -mVerticalCorrection) touchY += mVerticalCorrection
        val action = me.actionMasked
        val eventTime = me.eventTime
        val keyIndex = getKeyIndices(touchX, touchY)

        // Track the last few movements to look for spurious swipes.
        if (action == MotionEvent.ACTION_DOWN) customSwipeTracker.clear()
        customSwipeTracker.addMovement(me)
        when (action) {
            MotionEvent.ACTION_CANCEL -> {
                Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action = cancel")
            }
            MotionEvent.ACTION_UP -> {
                Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action = UP")
            }
            else -> {
                Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action != UP")
            }
        }

        // Ignore all motion events until a DOWN.
        if (mAbortKey && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
            return true
        }

        // 优先判定是否触发了滑动手势
        if (swipeEnabled) {
            if (customGestureDetector.onTouchEvent(me)) {
                showPreview(NOT_A_KEY)
                repeatJob?.cancel()
                repeatJob = null
                longPressJob?.cancel()
                longPressJob = null
                return true
            }
        }

        fun modifiedPointerDown() {
            mAbortKey = false
            mStartX = touchX
            mStartY = touchY
            mLastCodeX = touchX
            mLastCodeY = touchY
            mLastKeyTime = 0
            mCurrentKeyTime = 0
            mLastKey = NOT_A_KEY
            mCurrentKey = keyIndex
            mDownKey = keyIndex
            mDownTime = me.eventTime
            mLastMoveTime = mDownTime
            touchOnePoint = false
            if (action == MotionEvent.ACTION_POINTER_DOWN) return // 並擊鬆開前的虛擬按鍵事件
            checkMultiTap(eventTime, keyIndex)
            keyboardActionListener?.onPress(if (keyIndex != NOT_A_KEY) mKeys[keyIndex].code else 0)
            if (mCurrentKey >= 0 && mKeys[mCurrentKey].click!!.isRepeatable) {
                mRepeatKeyIndex = mCurrentKey
                handleRepeatJob()
                // Delivering the key could have caused an abort
                if (mAbortKey) {
                    mRepeatKeyIndex = NOT_A_KEY
                    return
                }
            }
            if (mCurrentKey != NOT_A_KEY) {
                handleLongPressJob()
            }
            showPreview(keyIndex, KeyBehavior.CLICK)
        }

        /**
         * @return 跳出外层函数
         */
        fun modifiedPointerUp(): Boolean {
            cancelAllJobs()
            mLastUpTime = eventTime
            if (keyIndex == mCurrentKey) {
                mCurrentKeyTime += eventTime - mLastMoveTime
            } else {
                resetMultiTap()
                mLastKey = mCurrentKey
                mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime
                mCurrentKey = keyIndex
                mCurrentKeyTime = 0
            }
            if (swipeEnabled) {
                val dx = touchX - touchX0
                val dy = touchY - touchY0
                val absX = abs(dx)
                val absY = abs(dy)
                if (max(absY, absX) > swipeTravel && touchOnePoint) {
                    Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\ttouch")
                    val keyBehavior =
                        if (absX < absY) {
                            Timber.d("swipeDebug.ext y, dX=$dx, dY=$dy")
                            if (dy > swipeTravel) KeyBehavior.SWIPE_DOWN else KeyBehavior.SWIPE_UP
                        } else {
                            Timber.d("swipeDebug.ext x, dX=$dx, dY=$dy")
                            if (dx > swipeTravel) KeyBehavior.SWIPE_RIGHT else KeyBehavior.SWIPE_LEFT
                        }
                    showPreview(NOT_A_KEY)
                    repeatJob?.cancel()
                    repeatJob = null
                    longPressJob?.cancel()
                    longPressJob = null
                    detectAndSendKey(mDownKey, mStartX, mStartY, me.eventTime, keyBehavior)
                    return true
                } else {
                    Timber.d("swipeDebug.ext fail, dX=$dx, dY=$dy")
                }
            }
            if (mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < DEBOUNCE_TIME && mLastKey != NOT_A_KEY) {
                mCurrentKey = mLastKey
                touchX = mLastCodeX
                touchY = mLastCodeY
            }
            showPreview(NOT_A_KEY)
            Arrays.fill(mKeyIndices, NOT_A_KEY)
            if (mRepeatKeyIndex != NOT_A_KEY && !mAbortKey) repeatKey()
            if (mRepeatKeyIndex == NOT_A_KEY && !mAbortKey) {
                Timber.d("onModifiedTouchEvent: detectAndSendKey")
                detectAndSendKey(
                    mCurrentKey,
                    touchX,
                    touchY,
                    eventTime,
                    if (mOldPointerCount > 1 || mComboMode) KeyBehavior.COMBO else KeyBehavior.CLICK,
                )
            }
            mRepeatKeyIndex = NOT_A_KEY
            return false
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchX0 = touchX
                touchY0 = touchY
                touchOnePoint = true
                modifiedPointerDown()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                modifiedPointerDown()
            }

            MotionEvent.ACTION_MOVE -> {
                var continueLongPress = false
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex
                        mCurrentKeyTime = eventTime - mDownTime
                    } else {
                        if (keyIndex == mCurrentKey) {
                            mCurrentKeyTime += eventTime - mLastMoveTime
                            continueLongPress = true
                        } else if (mRepeatKeyIndex == NOT_A_KEY) {
                            resetMultiTap()
                            mLastKey = mCurrentKey
                            mLastCodeX = mLastX
                            mLastCodeY = mLastY
                            mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime
                            mCurrentKey = keyIndex
                            mCurrentKeyTime = 0
                        }
                    }
                }
                if (!mComboMode && !continueLongPress) {
                    // Start new long press if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        handleLongPressJob()
                    }
                }
                showPreview(mCurrentKey)
                mLastMoveTime = eventTime
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            -> {
                val breakout = modifiedPointerUp()
                if (breakout) return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelAllJobs()
                mAbortKey = true
                showPreview(NOT_A_KEY)
                invalidateKey(mKeys[mCurrentKey])
            }
        }
        mLastX = touchX
        mLastY = touchY
        return true
    }

    private fun repeatKey(): Boolean {
        Timber.d("repeatKey")
        val key = mKeys[mRepeatKeyIndex]
        detectAndSendKey(mCurrentKey, key.x, key.y, mLastTapTime)
        return true
    }

    private fun cancelAllJobs() {
        repeatJob?.cancel()
        repeatJob = null
        longPressJob?.cancel()
        longPressJob = null
        removePreviewJob?.cancel()
        removePreviewJob = null
    }

    fun onDetach() {
        cancelAllJobs()
        freeDrawingBuffer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        freeDrawingBuffer()
    }

    private fun resetMultiTap() {
        mLastSentIndex = -1
        // final int mTapCount = 0;
        mLastTapTime = -1
        // final boolean mInMultiTap = false;
    }

    private fun checkMultiTap(
        eventTime: Long,
        keyIndex: Int,
    ) {
        if (keyIndex == NOT_A_KEY) return
        if (eventTime > mLastTapTime + longPressTimeout || keyIndex != mLastSentIndex) {
            resetMultiTap()
        }
    }

    companion object {
        private const val NOT_A_KEY = -1
        private const val DELAY_AFTER_PREVIEW = 100L
        private const val DEBOUNCE_TIME = 70
        private const val MAX_NEARBY_KEYS = 12
    }
}
