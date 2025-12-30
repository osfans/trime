/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.prefs.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

open class GestureFrame(context: Context) : FrameLayout(context) {
    enum class SwipeDirection {
        Up,
        Down,
        Left,
        Right,
    }

    private val lifecycleScope by lazy {
        findViewTreeLifecycleOwner()?.lifecycleScope!!
    }

    private var touchId = 0

    @Volatile
    private var longPressTriggered = false
    private var longPressJob: Job? = null

    @Volatile
    var longPressFeedbackEnabled = true

    @Volatile
    private var swipeTriggered = false
    private var shouldPerformSwipe = false
    private var swipeStartX = -1f
    private var swipeStartY = -1f
    private var swipeLastX = -1f

    @Volatile
    private var slideActivated = false

    private var lastTapTime = 0L
    private var maybeDoubleTap = false

    var onDoubleTapListener: (() -> Unit)? = null
    var onSwipeListener: ((SwipeDirection) -> Unit)? = null
    var onSlideListener: ((Int) -> Unit)? = null
    var onReleaseListener: (() -> Unit)? = null

    init {
        // disable system sound effect and haptic feedback
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        // avoid gaining focus unexpectedly
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun getSwipeDirection(dx: Float, dy: Float): SwipeDirection = if (abs(dx) > abs(dy)) {
        if (dx > 0) SwipeDirection.Right else SwipeDirection.Left
    } else {
        if (dy > 0) SwipeDirection.Down else SwipeDirection.Up
    }

    fun getNStep(start: Float, end: Float, step: Float): Int = (if (start < end) 1 else -1) * floor(abs(end - start) / step).toInt()

    private fun resetState() {
        onReleaseListener?.invoke()
        swipeStartX = -1f
        swipeStartY = -1f
        swipeLastX = -1f
        longPressTriggered = false
        longPressJob?.cancel()
        longPressJob = null
        shouldPerformSwipe = false
        swipeTriggered = false
        slideActivated = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                touchId = (touchId) and 0xFFFF
                val currentTouchId = touchId
                drawableHotspotChanged(x, y)
                isPressed = true
                InputFeedbackManager.keyPressVibrate(this)
                longPressJob?.cancel()
                longPressJob = lifecycleScope.launch {
                    delay(longPressTimeout.toLong())
                    if (touchId != currentTouchId) return@launch
                    if (!(swipeTriggered || longPressTriggered || shouldPerformSwipe)) {
                        longPressTriggered = performLongClick()
                        if (longPressFeedbackEnabled && longPressTriggered) {
                            InputFeedbackManager.keyPressVibrate(this@GestureFrame, true)
                        }
                    }
                }
                swipeStartX = x
                swipeStartY = y
                swipeLastX = x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isEnabled) return false
                drawableHotspotChanged(x, y)
                val dx = x - swipeStartX
                val dy = y - swipeStartY

                if (!longPressTriggered) {
                    if (!shouldPerformSwipe && (abs(dx) > SWIPE_THRESHOLD || abs(dy) > SWIPE_THRESHOLD)) {
                        shouldPerformSwipe = true
                        swipeTriggered = true
                    }
                }
                val onSlide = onSlideListener
                if (onSlide != null) {
                    if (!slideActivated) {
                        if (abs(dx) >= SWIPE_THRESHOLD) {
                            val startX = swipeStartX
                            slideActivated = true
                            swipeLastX = startX + (if (dx > 0) SWIPE_THRESHOLD else -SWIPE_THRESHOLD)
                        }
                    }
                    if (slideActivated) {
                        val startX = swipeStartX
                        val lastX = swipeLastX
                        val totalPast = getNStep(startX, lastX, SLIDE_STEP_SIZE)
                        val totalNow = getNStep(startX, x, SLIDE_STEP_SIZE)
                        val delta = totalNow - totalPast
                        if (delta != 0) {
                            onSlide(delta)
                        }
                        swipeLastX = x
                    }
                } else if (longPressTriggered) {
                    val lastX = swipeLastX
                    val delta = getNStep(lastX, x, SWIPE_MOVE_SIZE)
                    if (delta != 0) {
                        swipeLastX += delta.toFloat() * SWIPE_MOVE_SIZE
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                val dx = x - swipeStartX
                val dy = y - swipeStartY

                if (slideActivated) {
                    val onSlide = onSlideListener
                    if (onSlide != null) {
                        onSlide(0)
                        resetState()
                        return true
                    }
                }

                if (shouldPerformSwipe) {
                    if (!longPressTriggered) {
                        onSwipeListener?.invoke(getSwipeDirection(dx, dy))
                    }
                } else {
                    if (!longPressTriggered) {
                        val onDoubleTap = onDoubleTapListener
                        if (onDoubleTap != null) {
                            val now = System.currentTimeMillis()
                            if (maybeDoubleTap && now - lastTapTime <= longPressTimeout) {
                                maybeDoubleTap = false
                                onDoubleTap()
                            } else {
                                maybeDoubleTap = true
                                performClick()
                            }
                            lastTapTime = now
                        } else {
                            performClick()
                        }
                    }
                }
                resetState()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                resetState()
                return true
            }
        }
        return true
    }

    companion object {
        private const val SWIPE_THRESHOLD = 24f
        private const val SWIPE_MOVE_SIZE = 24f
        private const val SLIDE_STEP_SIZE = 12f

        private val longPressTimeout by AppPrefs.defaultInstance().keyboard.longPressTimeout
    }
}
