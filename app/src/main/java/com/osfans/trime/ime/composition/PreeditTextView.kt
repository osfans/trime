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

@SuppressLint("AppCompatCustomView")
class PreeditTextView
@JvmOverloads
constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : TextView(context, attributeSet) {
    var onMoveCursor: ((Int) -> Unit)? = null
    private var newCursorPos = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val textString = text.toString()
                val textLength = textString.length
                val position = getOffsetForPosition(x, y)
                if (position != textLength) {
                    val codePointPosition = if (textLength < position) {
                        textString.codePointCount(0, textLength)
                    } else {
                        textString.codePointCount(0, position)
                    } + if (textString.contains('‸')) -1 else 0
                    newCursorPos = codePointPosition
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                onMoveCursor?.invoke(newCursorPos)
                newCursorPos = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                newCursorPos = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
