// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import android.content.Context
import android.text.TextUtils
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.pressHighlightDrawable
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class CandidateItemUi(
    override val ctx: Context,
    theme: Theme,
) : Ui {
    private val maybeCandidateTextColor = ColorManager.getColor("candidate_text_color")
    private val maybeCommentTextColor = ColorManager.getColor("comment_text_color")
    private val maybeHighlightedCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")
    private val maybeHighlightedCommentTextColor = ColorManager.getColor("hilited_comment_text_color")

    val label =
        textView {
            textSize = theme.generalStyle.candidateTextSize.toFloat()
            typeface = FontManager.getTypeface("candidate_font")
            isSingleLine = true
            gravity = gravityCenter
            ellipsize = TextUtils.TruncateAt.END
            maybeCandidateTextColor?.let { setTextColor(it) }
        }

    val altLabel =
        textView {
            textSize = theme.generalStyle.commentTextSize.toFloat()
            typeface = FontManager.getTypeface("comment_font")
            isSingleLine = true
            gravity = gravityCenter
            ellipsize = TextUtils.TruncateAt.END
            maybeCommentTextColor?.let { setTextColor(it) }
        }

    override val root =
        constraintLayout {
            background = ColorManager.getColor("hilited_candidate_back_color")?.let { pressHighlightDrawable(it) }
            if (theme.generalStyle.commentOnTop) {
                add(
                    altLabel,
                    lParams(wrapContent, wrapContent) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    label,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                    },
                )
            } else {
                add(
                    label,
                    lParams(wrapContent, wrapContent) {
                        startOfParent()
                        centerVertically()
                        before(altLabel)

                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                        horizontalBias = 0.5f
                    },
                )
                add(
                    altLabel,
                    lParams(wrapContent, wrapContent) {
                        after(label)
                        centerVertically()
                        endOfParent()

                        horizontalBias = 0.5f
                    },
                )
            }
        }

    fun highlight(yes: Boolean) {
        if (yes) {
            maybeHighlightedCandidateTextColor?.let { label.setTextColor(it) }
            maybeHighlightedCommentTextColor?.let { altLabel.setTextColor(it) }
        } else {
            maybeCandidateTextColor?.let { label.setTextColor(it) }
            maybeCommentTextColor?.let { altLabel.setTextColor(it) }
        }
    }
}
