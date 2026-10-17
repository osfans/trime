/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

/** Binds both a held touch and a service click to the action the user encountered. */
@SuppressLint("ClickableViewAccessibility")
internal class VersionedClickBinding<T : Any>(private val view: View, private val actionLabel: Int) {
    private val actionOwner = requireNotNull(view as? BoundActionOwner) {
        "Versioned actions require a View that guards its widget accessibility callbacks"
    }
    private data class Binding<T>(val identity: T, val click: () -> Unit)
    private data class PendingClick<T>(val binding: Binding<T>)

    private val published = PublishedAccessibilityIdentity<T>()
    private var current: Binding<T>? = null
    private var pressed: Binding<T>? = null
    private var pendingClick: PendingClick<T>? = null
    private var touchActive = false
    private var blockedTouch = false
    private var serviceClick = false
    private var dispatchingClick = false

    init {
        view.isFocusable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                clearTouchState()
            }
        })
        view.setOnTouchListener { _, event ->
            if (blockedTouch && event.actionMasked != MotionEvent.ACTION_DOWN) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    blockedTouch = false
                    touchActive = false
                    pressed = null
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchActive = true
                    // A previous UP still owns its queued View click. Do not
                    // replace that identity with a second overlapping gesture.
                    blockedTouch = pendingClick != null || dispatchingClick
                    if (blockedTouch) {
                        pressed = null
                        return@setOnTouchListener true
                    }
                    pressed = current
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchActive = false
                    pressed = null
                }

                MotionEvent.ACTION_UP -> {
                    touchActive = false
                    val touch = pressed
                    pressed = null
                    if (touch == null || touch.identity != current?.identity) {
                        val cancel = MotionEvent.obtain(event)
                        try {
                            cancel.action = MotionEvent.ACTION_CANCEL
                            view.onTouchEvent(cancel)
                        } finally {
                            cancel.recycle()
                        }
                        return@setOnTouchListener true
                    }
                    // View may post its click until after UP. Keep the original callback,
                    // whose engine revision/snapshot is checked even if a bind intervenes.
                    val pending = PendingClick(touch)
                    pendingClick = pending
                    // View may decline a click after a slide or focus change.
                    // Queue cleanup after the PerformClick posted by this UP,
                    // without clearing the token before View handles the event.
                    view.post {
                        view.post {
                            if (pendingClick === pending) pendingClick = null
                        }
                    }
                }
            }
            false
        }
        view.setOnClickListener {
            val clicked = if (serviceClick) current else pendingClick?.binding ?: current
            pendingClick = null
            if (view.isEnabled && !dispatchingClick && clicked?.identity == current?.identity) {
                dispatchingClick = true
                try {
                    clicked?.click?.invoke()
                } finally {
                    dispatchingClick = false
                }
            }
        }
        // Widget overrides run after superclass node initialization and before
        // superclass action dispatch, including API 21 TextView's direct click.
        // RecyclerView retains ownership of its ItemDelegate when pooling views.
        actionOwner.clickBinding = this
    }

    internal fun populateAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        info.className = Button::class.java.name
        val identity = current?.identity.takeIf { accessibilityReady() }
        published.publish(identity)
        info.isClickable = identity != null
        info.isSelected = view.isSelected
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        if (identity != null) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, view.context.getString(actionLabel)))
        }
    }

    internal fun performBoundAccessibilityAction(action: Int, inheritedAction: () -> Boolean): Boolean {
        if (action != AccessibilityNodeInfo.ACTION_CLICK) return inheritedAction()
        if (touchActive || pendingClick != null || serviceClick || dispatchingClick ||
            !accessibilityReady() || !published.accepts(current?.identity)
        ) {
            return false
        }
        serviceClick = true
        return try {
            inheritedAction()
        } finally {
            serviceClick = false
        }
    }

    private fun accessibilityReady(): Boolean {
        if (!view.isEnabled) return false
        if (!view.isAttachedToWindow || !view.isShown) return false
        var parent = view.parent
        while (parent is View) {
            if (parent is RecyclerView) {
                // ItemDelegate's pending-update fallback skips saved delegates.
                // Check before entering that path, while preserving the layout
                // manager's collection and scrolling semantics when settled.
                return !parent.hasPendingAdapterUpdates() && !parent.isComputingLayout && !parent.isLayoutSuppressed
            }
            parent = parent.parent
        }
        return true
    }

    fun bind(identity: T?, click: () -> Unit) {
        if (!view.isEnabled) {
            view.cancelPendingInputEvents()
            clearTouchState()
        }
        val changed = identity != current?.identity
        if (changed) published.clear()
        current = identity?.let { Binding(it, click) }
        if (changed) view.notifyBoundActionChanged()
    }

    private fun clearTouchState() {
        pressed = null
        pendingClick = null
        touchActive = false
        blockedTouch = false
    }
}
