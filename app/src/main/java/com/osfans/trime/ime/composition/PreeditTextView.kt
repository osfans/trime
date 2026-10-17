/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.osfans.trime.R
import com.osfans.trime.ime.candidates.PublishedAccessibilityIdentity

@SuppressLint("AppCompatCustomView")
class PreeditTextView
@JvmOverloads
constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : TextView(context, attributeSet) {
    var onMoveCursor: ((Int, String) -> Unit)? = null
    private var touchedText: String? = null
    private var newCursorPos = -1
    private var cursorPosition = 0
    private data class Identity(val text: String, val cursor: Int)
    private val published = PublishedAccessibilityIdentity<Identity>()

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun updateComposition(content: CharSequence, cursor: Int) {
        val value = content.toString()
        val sameText = text.toString() == value
        val bounded = preeditCharacterBoundary(value, cursor)
        val cursorChanged = cursorPosition != bounded
        if (!sameText || cursorChanged) clearTransientContent()
        text = content
        cursorPosition = bounded
        // Report the caret only after Rime has accepted and returned its position.
        if (sameText && cursorChanged && isShown) sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
    }

    fun clearTransientContent() {
        touchedText = null
        newCursorPos = -1
        published.clear()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val textString = text.toString()
                touchedText = textString
                newCursorPos = preeditByteOffset(textString, getOffsetForPosition(x, y))
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (touchedText != null) performClick()
                touchedText = null
                newCursorPos = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // Cancelling a pointer does not change the published text or caret.
                touchedText = null
                newCursorPos = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        val value = text.toString()
        if (!isEnabled || value.isEmpty()) return false
        val touched = touchedText
        if (touched != null && (touched != value || newCursorPos < 0)) return false
        super.performClick()
        onMoveCursor?.invoke(if (touched != null) newCursorPos else preeditByteOffset(value, cursorPosition), value)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val value = text.toString()
        published.publish(Identity(value, cursorPosition).takeIf { value.isNotEmpty() && isEnabled })
        info.contentDescription = if (value.isEmpty()) null else "${context.getString(R.string.a11y_preedit)}, $value"
        info.setTextSelection(cursorPosition, cursorPosition)
        info.movementGranularities = AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
        if (value.isNotEmpty() && isEnabled) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, context.getString(R.string.a11y_preedit_cursor_previous)))
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, context.getString(R.string.a11y_preedit_cursor_next)))
        }
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            event.fromIndex = cursorPosition
            event.toIndex = cursorPosition
            event.itemCount = text.length
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val value = text.toString()
        when (action) {
            AccessibilityNodeInfo.ACTION_CLICK,
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            -> {
                if (!isEnabled || !published.accepts(Identity(value, cursorPosition))) return false
                if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    touchedText = null
                    newCursorPos = -1
                    return super.performAccessibilityAction(action, arguments)
                }
                val position = if (action == AccessibilityNodeInfo.ACTION_SET_SELECTION) {
                    val start = arguments?.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, -1) ?: -1
                    val end = arguments?.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, -1) ?: -1
                    if (start != end || start !in 0..value.length || preeditCharacterBoundary(value, start) != start) return false
                    start
                } else {
                    if ((arguments?.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER) ?: AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER) != AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER ||
                        arguments?.getBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false) == true
                    ) {
                        return false
                    }
                    adjacentPreeditCharacter(value, cursorPosition, action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                }
                if (position == cursorPosition) return false
                onMoveCursor?.invoke(preeditByteOffset(value, position), value)
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
