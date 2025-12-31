// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.pressHighlightDrawable
import splitties.views.dsl.constraintlayout.baselineToBaselineOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class CandidateItemUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    private val textSize = theme.generalStyle.candidateTextSize
    private val commentSize = theme.generalStyle.commentTextSize
    private val textFont = FontManager.getTypeface("candidate_font")
    private val commentFont = FontManager.getTypeface("comment_font")
    private val textColor = ColorManager.getColor("candidate_text_color")
    private val commentColor = ColorManager.getColor("comment_text_color")
    private val hlCommentColor = ColorManager.getColor("hilited_comment_text_color")
    private val hlTextColor = ColorManager.getColor("hilited_candidate_text_color")
    private val hlBackColor = ColorManager.getColor("hilited_candidate_back_color")

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
            this.textSize = commentSize
            typeface = commentFont
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val content = constraintLayout {
        if (theme.generalStyle.commentOnTop) {
            add(
                comment,
                lParams {
                    centerHorizontally()
                    topOfParent()
                    width = wrapContent
                    matchConstraintPercentHeight = 0.3f // TODO: new param for customization
                },
            )
            add(
                text,
                lParams {
                    centerInParent()
                    width = wrapContent
                },
            )
        } else {
            add(
                text,
                lParams(wrapContent, wrapContent) {
                    centerVertically()
                    startOfParent()
                    horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    endToStartOf(comment)
                },
            )
            add(
                comment,
                lParams(wrapContent, wrapContent) {
                    startToEndOf(text)
                    endOfParent()
                    baselineToBaselineOf(text)
                    horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                },
            )
        }
    }

    override val root = view(::GestureFrame) {
        /**
         * candidate long press feedback is handled by `showCandidateActionMenu`
         */
        longPressFeedbackEnabled = false

        add(
            content,
            lParams(matchParent, matchParent) {
                gravity = gravityCenter
            },
        )
    }

    @SuppressLint("UseKtx")
    fun update(
        item: CandidateItem,
        highlighted: Boolean,
    ) {
        val tColor = if (highlighted) hlTextColor else textColor
        val cColor = if (highlighted) hlCommentColor else commentColor
        text.text = item.text
        text.setTextColor(tColor)
        comment.text = if (theme.generalStyle.commentOnTop) item.comment else " ${item.comment}"
        comment.setTextColor(cColor)
        comment.isGone = item.comment.isEmpty()
        root.background =
            if (highlighted) {
                ColorDrawable(hlBackColor)
            } else {
                pressHighlightDrawable(hlBackColor)
            }
    }
}
