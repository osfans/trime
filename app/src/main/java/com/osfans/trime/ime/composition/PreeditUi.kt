/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.buildSpannedString
import com.osfans.trime.core.RimeProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.setPaddingDp

open class PreeditUi(
    final override val ctx: Context,
    private val theme: Theme,
    private val setupPreeditView: (TextView.() -> Unit)? = null,
) : Ui {
    private val textColor = ColorManager.getColor("text_color")
    private val highlightTextColor = ColorManager.getColor("hilited_text_color")
    private val highlightBackColor = ColorManager.getColor("hilited_back_color")

    val preedit =
        view(::PreeditTextView) {
            setPaddingDp(3, 1, 3, 1)
            textColor?.let { setTextColor(it) }
            textSize = theme.generalStyle.textSize
            typeface = FontManager.getTypeface("text_font")
            setupPreeditView?.invoke(this)
        }

    override val root =
        object : LinearLayout(ctx) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

            init {
                orientation = HORIZONTAL
                add(preedit, lParams())
            }
        }

    private fun RimeProto.Context.Composition.toSpannedString() =
        buildSpannedString {
            append(preedit ?: "")
            highlightTextColor?.let {
                setSpan(ForegroundColorSpan(it), selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            }
            highlightBackColor?.let {
                setSpan(BackgroundColorSpan(it), selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }

    var visible = false
        private set

    private fun updateTextView(
        str: CharSequence,
        visible: Boolean,
    ) = preedit.run {
        if (visible) {
            text = str
            if (visibility == View.GONE) visibility = View.VISIBLE
        } else if (visibility != View.GONE) {
            visibility = View.GONE
        }
    }

    fun update(inputComposition: RimeProto.Context.Composition) {
        val string = inputComposition.toSpannedString()
        val cursorPos = inputComposition.cursorPos
        val hasPreedit = inputComposition.length > 0
        visible = hasPreedit
        if (!visible) return
        val stringWithCursor =
            if (cursorPos == 0 || cursorPos == string.length) {
                string
            } else {
                buildSpannedString {
                    if (cursorPos > 0) append(string, 0, cursorPos)
                    append(string, cursorPos, string.length)
                }
            }
        updateTextView(stringWithCursor, hasPreedit)
    }
}
