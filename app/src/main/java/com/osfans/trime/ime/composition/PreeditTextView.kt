/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.math.MathUtils

@SuppressLint("AppCompatCustomView")
class PreeditTextView
@JvmOverloads
constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : TextView(context, attributeSet) {
    var onMoveCursor: ((Int, String) -> Unit)? = null
    private var lastTapOffset = -1
    private var newCursorPos = -1
    private var pressedText: CharSequence? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedText = text
                val textString = text.toString()
                lastTapOffset = MathUtils.clamp(
                    getOffsetForPosition(x, y),
                    0,
                    textString.length,
                )
                newCursorPos = preeditByteOffset(textString, lastTapOffset)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val pressed = pressedText
                if (pressed != null && text === pressed && newCursorPos >= 0) {
                    onMoveCursor?.invoke(newCursorPos, pressed.toString())
                }
                pressedText = null
                lastTapOffset = -1
                newCursorPos = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedText = null
                lastTapOffset = -1
                newCursorPos = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
