/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.baselineToBaselineOf
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

class CandidateItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme
        get() = scope.theme

    private val textSize = theme.generalStyle.candidateTextSize
    private val commentSize = theme.generalStyle.commentTextSize

    private val textFont = FontManager.getTypeface("candidate_font")
    private val commentFont = FontManager.getTypeface("comment_font")

    // Read at use time so a scheme switch re-binds rows with the new colors.
    private val textColor: Int get() = scope.colors.candidateTextColor
    private val commentColor: Int get() = scope.colors.commentTextColor
    private val hlCommentColor: Int get() = scope.colors.hilitedCommentTextColor
    private val hlTextColor: Int get() = scope.colors.hilitedCandidateTextColor
    private val hlBackColor: Int get() = scope.colors.hilitedCandidateBackColor

    private val commentPosition = theme.generalStyle.commentPosition
    private val commentVerticalBias = theme.generalStyle.commentVerticalBias
    private val candidateTextVerticalBias = theme.generalStyle.candidateTextVerticalBias

    private val text =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            this.textSize = this@CandidateItemUi.textSize
            typeface = textFont
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val comment =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            this.textSize = commentSize
            typeface = commentFont
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val content = constraintLayout {
        horizontalPadding = dp(theme.generalStyle.candidatePadding)
        when (commentPosition) {
            GeneralStyle.CommentPosition.RIGHT -> {
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        centerVertically()
                        startOfParent()
                        endToStartOf(comment)
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        startToEndOf(text, ctx.dp(1))
                        endOfParent()
                        baselineToBaselineOf(text)
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
            }
            GeneralStyle.CommentPosition.TOP -> {
                add(
                    text,
                    lParams(wrapContent, matchConstraints) {
                        centerHorizontally()
                        bottomOfParent()
                        topToBottomOf(comment)
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, matchConstraints) {
                        matchConstraintPercentHeight = 0.4f
                        topOfParent()
                        centerHorizontally()
                        bottomToTopOf(text)
                    },
                )
            }
            GeneralStyle.CommentPosition.OVERLAY -> {
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                        verticalBias = candidateTextVerticalBias
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                        verticalBias = commentVerticalBias
                    },
                )
            }
        }
    }

    override val root = view(::GestureFrame) {
        /**
         * candidate long press feedback is handled by `showCandidateActionMenu`
         */
        add(
            content,
            lParams(wrapContent, dp(theme.generalStyle.candidateViewHeight)) {
                gravity = gravityCenter
            },
        )
    }

    @SuppressLint("UseKtx")
    fun update(
        item: CandidateProto,
        highlighted: Boolean,
    ) {
        val tColor = if (highlighted) hlTextColor else textColor
        val cColor = if (highlighted) hlCommentColor else commentColor
        val cornerRadius = ctx.dp(theme.generalStyle.candidateCornerRadius)
        val contentColor = if (highlighted) hlBackColor else Color.TRANSPARENT

        content.background = roundedRippleDrawable(hlBackColor, cornerRadius, contentColor)
        text.text = item.text
        text.setTextColor(tColor)

        val commentText = item.comment
        comment.text = commentText
        comment.setTextColor(cColor)
        comment.isVisible = commentText.isNotEmpty()
    }
}
