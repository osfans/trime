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
import androidx.annotation.ColorInt
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView

class LabeledCandidateItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme
        get() = scope.theme

    private val labelSize = theme.window.foreground.labelFontSize
    private val textSize = theme.window.foreground.textFontSize
    private val commentSize = theme.window.foreground.commentFontSize
    private val labelFont = FontManager.getTypeface("label_font")
    private val textFont = FontManager.getTypeface("candidate_font")
    private val commentFont = FontManager.getTypeface("comment_font")

    // Read at use time so a scheme switch re-binds rows with the new colors.
    private val labelColor: Int get() = scope.colors.labelColor
    private val textColor: Int get() = scope.colors.candidateTextColor
    private val commentColor: Int get() = scope.colors.commentTextColor
    private val highlightLabelColor: Int get() = scope.colors.hilitedLabelColor
    private val highlightCommentTextColor: Int get() = scope.colors.hilitedCommentTextColor
    private val highlightCandidateTextColor: Int get() = scope.colors.hilitedCandidateTextColor
    private val highlightCandidateBackColor: Int get() = scope.colors.hilitedCandidateBackColor

    override val root =
        textView {
            val v = dp(theme.window.itemPadding.vertical)
            val h = dp(theme.window.itemPadding.horizontal)
            setPadding(h, v, h, v)
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
    ) {
        val labelFg = if (highlighted) highlightLabelColor else labelColor
        val textFg = if (highlighted) highlightCandidateTextColor else textColor
        val commentFg = if (highlighted) highlightCommentTextColor else commentColor
        root.text =
            buildSpannedString {
                inSpanWith(labelFg, ctx.sp(labelSize), labelFont) { append(candidate.label) }
                append(" ")
                inSpanWith(textFg, ctx.sp(textSize), textFont) { append(candidate.text) }
                if (candidate.comment.isNotBlank()) {
                    append(" ")
                    inSpanWith(commentFg, ctx.sp(commentSize), commentFont) { append(candidate.comment) }
                }
            }
        val bg =
            GradientDrawable().apply {
                if (highlighted) {
                    setColor(highlightCandidateBackColor)
                    cornerRadius = ctx.dp(theme.generalStyle.candidateCornerRadius)
                } else {
                    setColor(Color.TRANSPARENT)
                }
            }
        root.background = bg
    }
}
