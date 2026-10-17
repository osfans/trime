/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import com.osfans.trime.R
import com.osfans.trime.ime.keyboard.GestureFrame

/** Cancels a held candidate if an asynchronous refresh rebinds its view. */
class CandidateGestureFrame(context: Context) : GestureFrame(context) {
    private val touchState = CandidateTouchState()
    private val published = PublishedAccessibilityIdentity<CandidateIdentity>()
    private var dispatchingAction = false

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    var identity: CandidateIdentity? = null
        set(value) {
            val changed = field != value
            if (changed) published.clear()
            if (changed && touchState.isActive) {
                touchState.cancel()
                cancelGesture()
            }
            field = value
            if (changed) notifyBoundActionChanged()
        }

    // GestureFrame dispatches an accepted click through our performClick override.
    // Calling it again here would select twice; accessibility also uses that override.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchState.begin(identity)
            MotionEvent.ACTION_CANCEL -> touchState.cancel()
        }
        if (event.actionMasked == MotionEvent.ACTION_UP && !touchState.accepts(identity)) {
            cancelGesture()
            touchState.finish()
            return true
        }
        return try {
            super.onTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchState.finish()
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) touchState.finish()
    }

    override fun onDetachedFromWindow() {
        touchState.finish()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean = dispatchCandidateAction { super.performClick() }

    override fun performLongClick(): Boolean = dispatchCandidateAction { super.performLongClick() }

    private inline fun dispatchCandidateAction(action: () -> Boolean): Boolean {
        if (!isEnabled || dispatchingAction || !touchState.accepts(identity)) return false
        dispatchingAction = true
        return try {
            action()
        } finally {
            dispatchingAction = false
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val valid = identity?.takeIf { it.isValid && isEnabled }
        published.publish(valid)
        info.className = Button::class.java.name
        info.isSelected = isSelected
        info.isClickable = valid != null && isClickable
        info.isLongClickable = valid != null && isLongClickable
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
        if (info.isClickable) info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.a11y_candidate_select)))
        if (info.isLongClickable) info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, context.getString(R.string.a11y_candidate_actions)))
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_CLICK || action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
            if (!isEnabled || dispatchingAction || touchState.isActive || hasPendingTouchGesture ||
                !published.accepts(identity?.takeIf { it.isValid })
            ) {
                return false
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun getBaseline(): Int {
        val child = getChildAt(0) ?: return super.getBaseline()
        return child.baseline.takeIf { it >= 0 }?.let { child.top + it } ?: super.getBaseline()
    }
}
