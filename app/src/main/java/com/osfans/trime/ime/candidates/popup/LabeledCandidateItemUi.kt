/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.osfans.trime.R
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.CandidateGestureFrame
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent

class LabeledCandidateItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme
        get() = scope.theme

    private val labelSize = theme.window.foreground.labelFontSize
    private val textSize = theme.window.foreground.textFontSize
    private val commentSize = theme.window.foreground.commentFontSize
    private val labelFont = theme.fonts.label
    private val textFont = theme.fonts.candidate
    private val commentFont = theme.fonts.comment

    // Read at use time so a scheme switch re-binds rows with the new colors.
    private val labelColor: Int get() = scope.colors.labelColor
    private val textColor: Int get() = scope.colors.candidateTextColor
    private val commentColor: Int get() = scope.colors.commentTextColor
    private val highlightLabelColor: Int get() = scope.colors.hilitedLabelColor
    private val highlightCommentTextColor: Int get() = scope.colors.hilitedCommentTextColor
    private val highlightCandidateTextColor: Int get() = scope.colors.hilitedCandidateTextColor
    private val highlightCandidateBackColor: Int get() = scope.colors.hilitedCandidateBackColor

    private val content =
        textView {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val v = dp(theme.window.itemPadding.vertical)
            val h = dp(theme.window.itemPadding.horizontal)
            setPadding(h, v, h, v)
        }

    override val root = view(::CandidateGestureFrame) {
        add(content, lParams(wrapContent, wrapContent))
    }

    private inline fun SpannableStringBuilder.inSpanWith(
        @ColorInt color: Int,
        textSize: Float,
        typeface: Typeface,
        builderAction: SpannableStringBuilder.() -> Unit,
    ) = inSpans(CandidateItemSpan(color, textSize, typeface), builderAction)

    fun update(
        candidate: CandidateProto,
        highlighted: Boolean,
        isPrediction: Boolean = false,
    ) {
        val labelFg = if (highlighted) highlightLabelColor else labelColor
        val textFg = if (highlighted) highlightCandidateTextColor else textColor
        val commentFg = if (highlighted) highlightCommentTextColor else commentColor
        content.text =
            buildSpannedString {
                val label = if (isPrediction) ctx.getString(R.string.next_word_prediction_label) else candidate.label
                if (label.isNotEmpty()) {
                    inSpanWith(labelFg, ctx.sp(labelSize), labelFont) { append(label) }
                }
                append(" ")
                if (candidate.text.isNotEmpty()) {
                    val font = if (highlighted) Typeface.create(textFont, Typeface.BOLD) else textFont
                    inSpanWith(textFg, ctx.sp(textSize), font) { append(candidate.text) }
                }
                if (!isPrediction && AppPrefs.defaultInstance().candidates.showComments.getValue() && candidate.comment.isNotBlank()) {
                    append(" ")
                    inSpanWith(commentFg, ctx.sp(commentSize), commentFont) { append(candidate.comment) }
                }
            }
        val bg =
            GradientDrawable().apply {
                if (highlighted) {
                    setColor(highlightCandidateBackColor)
                    cornerRadius = ctx.dp(theme.style.candidateCornerRadius)
                } else {
                    setColor(Color.TRANSPARENT)
                }
            }
        root.background = bg
        root.isSelected = highlighted
        root.contentDescription = content.text
    }
}
