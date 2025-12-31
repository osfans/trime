/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.prefs.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

open class KeyboardGestureFrame(context: Context) : FrameLayout(context) {

    private data class PointerState(
        var keyIndex: Int = -1,
        var startX: Float = 0f,
        var startY: Float = 0f,
        var lastX: Float = 0f,
        var startTime: Long = 0L,
        var isLongPressed: Boolean = false,
        var shouldPerformSwipe: Boolean = false,
        var slideActivated: Boolean = false,
        var longPressJob: Job? = null,
        var repeatJob: Job? = null,
        var lastSwipeBehavior: KeyBehavior? = null,
        var currentX: Float = 0f,
        var currentY: Float = 0f,
    )

    private val lifecycleScope by lazy {
        findViewTreeLifecycleOwner()?.lifecycleScope!!
    }

    private val activePointers = SparseArray<PointerState>()
    private val lastTapTimes = SparseArray<Long>()

    var onKeyActionListener: ((keyIndex: Int, behavior: KeyBehavior) -> Boolean)? = null
    var onKeySlideListener: ((keyIndex: Int, deltaX: Int, x: Float, y: Float) -> Boolean)? = null
    var onKeyStateListener: ((keyIndex: Int, behavior: KeyBehavior, isVisible: Boolean, isPressed: Boolean, isRepeating: Boolean) -> Unit)? = null
    var onKeyReleaseListener: ((keyIndex: Int) -> Unit)? = null
    var onPopupChangeFocus: ((keyIndex: Int, x: Float, y: Float) -> Unit)? = null
    var onPopupSelected: ((keyIndex: Int) -> Unit)? = null

    open fun getKeyIndex(x: Float, y: Float): Int = -1
    open fun isKeyRepeatable(keyIndex: Int): Boolean = false
    open fun isKeySlideCursor(keyIndex: Int): Boolean = false
    open fun isKeySlideDelete(keyIndex: Int): Boolean = false
    open fun hasAction(keyIndex: Int, behavior: KeyBehavior): Boolean = false
    open fun hasPopupKeys(keyIndex: Int): Boolean = false
    open val verticalCorrection: Int = 0

    init {
        setWillNotDraw(false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val adjustedY = if (y >= -verticalCorrection) y + verticalCorrection else y
                val keyIndex = getKeyIndex(x, adjustedY)
                if (keyIndex == -1) return true

                val state = PointerState(
                    keyIndex = keyIndex,
                    startX = x,
                    startY = y,
                    lastX = x,
                    currentX = x,
                    currentY = y,
                    startTime = SystemClock.elapsedRealtime(),
                )
                activePointers.put(pointerId, state)

                onKeyStateListener?.invoke(keyIndex, KeyBehavior.CLICK, true, true, false)
                val hasLongClick = hasAction(keyIndex, KeyBehavior.LONG_CLICK)
                val hasPopup = hasPopupKeys(keyIndex)
                if (hasLongClick || hasPopup || isKeyRepeatable(keyIndex)) launchLongPressJob(pointerId, state)
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val state = activePointers.get(pointerId) ?: continue
                    val keyIndex = state.keyIndex
                    if (keyIndex == -1) continue
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val dx = x - state.startX
                    val dy = y - state.startY
                    state.currentX = x
                    state.currentY = y

                    if (!swipeEnabled) {
                        val currentKeyIndex = getKeyIndex(x, y)
                        if (currentKeyIndex != -1 && currentKeyIndex != state.keyIndex) {
                            activateKeyFeedback(currentKeyIndex)
                            deactivateKeyFeedback(state.keyIndex)
                            state.keyIndex = currentKeyIndex
                        }
                        state.longPressJob?.cancel()
                        continue
                    }

                    if (!state.isLongPressed && swipeEnabled) {
                        val behavior = getSwipeBehavior(state, dx, dy)
                        if (state.lastSwipeBehavior != behavior) {
                            state.lastSwipeBehavior = behavior
                            activateKeyFeedback(keyIndex, behavior)
                        }
                    }

                    if ((isKeySlideCursor(keyIndex) || isKeySlideDelete(keyIndex)) && onKeySlideListener != null && !state.isLongPressed) {
                        if (!state.slideActivated) {
                            if (abs(dx) >= swipeTravel) {
                                state.slideActivated = true
                                state.lastX = state.startX + (if (dx > 0) swipeTravel else -swipeTravel)
                            }
                        }

                        if (state.slideActivated) {
                            val startX = state.startX
                            val lastX = state.lastX
                            val totalPast = getNStep(startX, lastX, slideStepSize.toFloat())
                            val totalNow = getNStep(startX, x, slideStepSize.toFloat())
                            val delta = totalNow - totalPast
                            if (delta != 0) {
                                onKeySlideListener?.invoke(keyIndex, delta, x, y)
                                state.lastX = x
                            }
                        }
                    }

                    if (state.isLongPressed) {
                        onPopupChangeFocus?.invoke(keyIndex, x, y)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val state = activePointers.get(pointerId) ?: return true
                activePointers.delete(pointerId)
                val keyIndex = state.keyIndex
                if (keyIndex == -1) return true
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                if (state.slideActivated) onKeySlideListener?.invoke(keyIndex, 0, x, y)
                state.longPressJob?.cancel()
                state.repeatJob?.cancel()
                onKeyReleaseListener?.invoke(keyIndex)

                val hasLazy = hasAction(keyIndex, KeyBehavior.LAZY_DOUBLE_CLICK)
                val hasDouble = hasAction(keyIndex, KeyBehavior.DOUBLE_CLICK)

                if (state.isLongPressed) {
                    onPopupSelected?.invoke(keyIndex)
                    deactivateKeyFeedback(keyIndex, KeyBehavior.LONG_CLICK)
                    return true
                }

                if (state.shouldPerformSwipe && !state.slideActivated) {
                    onKeyActionListener?.invoke(keyIndex, state.lastSwipeBehavior!!)
                    deactivateKeyFeedback(keyIndex)
                    return true
                }

                if (activePointers.isNotEmpty()) {
                    onKeyActionListener?.invoke(keyIndex, KeyBehavior.COMBO)
                    deactivateKeyFeedback(keyIndex)
                    return true
                }

                if (!hasLazy && !hasDouble) {
                    if (!state.slideActivated) {
                        onKeyActionListener?.invoke(keyIndex, KeyBehavior.CLICK)
                    }
                    deactivateKeyFeedback(keyIndex)
                    return true
                }

                val now = SystemClock.elapsedRealtime()
                val lastTime = lastTapTimes.get(keyIndex, 0L)
                val timeDelta = now - lastTime
                if (hasLazy && !hasDouble) {
                    if (timeDelta <= doubleTapTimeout) {
                        lastTapTimes.delete(keyIndex)
                        onKeyActionListener?.invoke(keyIndex, KeyBehavior.LAZY_DOUBLE_CLICK)
                    } else {
                        val scheduledAt = now
                        lastTapTimes.put(keyIndex, scheduledAt)
                        lifecycleScope.launch {
                            delay(doubleTapTimeout.toLong())
                            if (lastTapTimes.get(keyIndex, -1L) == scheduledAt) {
                                lastTapTimes.delete(keyIndex)
                                onKeyActionListener?.invoke(keyIndex, KeyBehavior.CLICK)
                            }
                        }
                    }
                } else if (timeDelta <= doubleTapTimeout) {
                    lastTapTimes.delete(keyIndex)
                    onKeyActionListener?.invoke(keyIndex, KeyBehavior.DOUBLE_CLICK)
                } else if (!state.slideActivated) {
                    lastTapTimes.put(keyIndex, now)
                    onKeyActionListener?.invoke(keyIndex, KeyBehavior.CLICK)
                }

                deactivateKeyFeedback(keyIndex)
            }

            MotionEvent.ACTION_CANCEL -> {
                onKeyReleaseListener?.invoke(-1)
                for (i in 0 until activePointers.size) {
                    val state = activePointers.valueAt(i)
                    state.longPressJob?.cancel()
                    state.repeatJob?.cancel()
                    deactivateKeyFeedback(state.keyIndex)
                }
                activePointers.clear()
                lastTapTimes.clear()
            }
        }

        return true
    }

    private fun launchLongPressJob(pointerId: Int, state: PointerState) {
        val keyIndex = state.keyIndex
        state.longPressJob = lifecycleScope.launch {
            delay(longPressTimeout.toLong())
            if (activePointers.get(pointerId) != state || state.shouldPerformSwipe || state.slideActivated) return@launch
            state.isLongPressed = true
            if (isKeyRepeatable(keyIndex)) {
                onKeyActionListener?.invoke(keyIndex, KeyBehavior.CLICK)
                launchRepeatClickJob(pointerId, state)
            } else {
                if (hasPopupKeys(keyIndex)) onKeyStateListener?.invoke(keyIndex, KeyBehavior.LONG_CLICK, true, true, false)
                if (onKeyActionListener?.invoke(keyIndex, KeyBehavior.LONG_CLICK) == true) {
                    deactivateKeyFeedback(keyIndex, KeyBehavior.LONG_CLICK)
                    activePointers.delete(pointerId)
                }
            }
        }
    }

    private fun launchRepeatClickJob(pointerId: Int, state: PointerState) {
        val keyIndex = state.keyIndex
        state.repeatJob = lifecycleScope.launch {
            try {
                delay(repeatInterval.toLong())
                while (activePointers.get(pointerId) == state) {
                    onKeyActionListener?.invoke(keyIndex, KeyBehavior.CLICK)
                    onKeyStateListener?.invoke(keyIndex, KeyBehavior.CLICK, false, false, true)
                    delay(repeatInterval.toLong())
                }
            } finally {
                deactivateKeyFeedback(keyIndex)
            }
        }
    }

    private fun getSwipeBehavior(state: PointerState, dx: Float, dy: Float): KeyBehavior {
        val absDx = abs(dx)
        val absDy = abs(dy)
        val distance = max(absDx, absDy)
        val elapsed = SystemClock.elapsedRealtime() - state.startTime

        val velocity = if (elapsed > 0) (distance / elapsed) * 1000f else 0f
        val isSwipe = distance >= swipeTravel || velocity >= swipeVelocity

        val behavior = if (isSwipe) {
            if (absDx > absDy) {
                if (dx > 0) KeyBehavior.SWIPE_RIGHT else KeyBehavior.SWIPE_LEFT
            } else {
                if (dy > 0) KeyBehavior.SWIPE_DOWN else KeyBehavior.SWIPE_UP
            }
        } else {
            KeyBehavior.CLICK
        }

        state.shouldPerformSwipe = isSwipe

        return behavior
    }

    fun getNStep(start: Float, end: Float, step: Float): Int = (if (start < end) 1 else -1) * floor(abs(end - start) / step).toInt()

    private fun activateKeyFeedback(keyIndex: Int, behavior: KeyBehavior = KeyBehavior.CLICK) {
        onKeyStateListener?.invoke(keyIndex, behavior, true, false, false)
    }

    private fun deactivateKeyFeedback(keyIndex: Int, behavior: KeyBehavior = KeyBehavior.CLICK) {
        onKeyStateListener?.invoke(keyIndex, behavior, false, false, false)
    }

    private val touchPaint by lazy {
        if (DEBUG_SHOW_TOUCH_PATH) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                strokeWidth = 2f
                strokeCap = Paint.Cap.ROUND
            }
        } else {
            null
        }
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (touchPaint == null) return

        for (i in 0 until activePointers.size) {
            val state = activePointers.valueAt(i)
            drawTouchPath(canvas, state.startX, state.startY, state.currentX, state.currentY)
        }
    }

    private fun drawTouchPath(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
        val p = touchPaint ?: return

        p.color = 0xFF90CAF9.toInt()
        p.style = Paint.Style.FILL
        canvas.drawCircle(startX, startY, 6f, p)

        p.color = 0xFF000000.toInt()
        p.style = Paint.Style.STROKE
        canvas.drawLine(startX, startY, endX, endY, p)

        p.color = 0xFFFFCC80.toInt()
        p.style = Paint.Style.FILL
        canvas.drawCircle(endX, endY, 6f, p)

        p.textSize = 28f
        p.style = Paint.Style.FILL
        p.alpha = 255
        p.color = 0xFF000000.toInt()

        val startText = "x:${startX.toInt()} y:${startY.toInt()}"
        val endText = "x:${endX.toInt()} y:${endY.toInt()}"

        canvas.drawText(startText, startX + 10f, startY + 30f, p)
        canvas.drawText(endText, endX + 10f, endY + 30f, p)

        invalidate()
    }

    companion object {
        private const val DEBUG_SHOW_TOUCH_PATH = false

        private val swipeEnabled by AppPrefs.defaultInstance().keyboard.swipeEnabled
        private val swipeTravel by AppPrefs.defaultInstance().keyboard.swipeTravel
        private val swipeVelocity by AppPrefs.defaultInstance().keyboard.swipeVelocity
        private val longPressTimeout by AppPrefs.defaultInstance().keyboard.longPressTimeout
        private val repeatInterval by AppPrefs.defaultInstance().keyboard.repeatInterval
        private val doubleTapTimeout by AppPrefs.defaultInstance().keyboard.doubleTapTimeout
        private val slideStepSize by AppPrefs.defaultInstance().keyboard.slideStepSize
    }
}
