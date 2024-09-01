// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import android.content.Context
import android.view.View
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class CandidateItemUi(override val ctx: Context, theme: Theme) : Ui {
    private val maybeCandidateTextColor = ColorManager.getColor("candidate_text_color")
    private val maybeCommentTextColor = ColorManager.getColor("comment_text_color")
    private val maybeHighlightedCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")
    private val maybeHighlightedCommentTextColor = ColorManager.getColor("hilited_comment_text_color")

    private val text =
        textView {
            textSize = theme.generalStyle.candidateTextSize.toFloat()
            typeface = FontManager.getTypeface("candidate_font")
            isSingleLine = true
            gravity = gravityCenter
            maybeCandidateTextColor?.let { setTextColor(it) }
        }

    private val comment =
        textView {
            textSize = theme.generalStyle.commentTextSize.toFloat()
            typeface = FontManager.getTypeface("comment_font")
            isSingleLine = true
            gravity = gravityCenter
            maybeCommentTextColor?.let { setTextColor(it) }
            visibility = View.GONE
        }

    override val root =
        constraintLayout {
            if (theme.generalStyle.commentOnTop) {
                add(
                    comment,
                    lParams(wrapContent, matchParent) {
                        topOfParent()
                        centerHorizontally()
                        above(text)
                    },
                )
                add(
                    text,
                    lParams(wrapContent, matchParent) {
                        centerHorizontally()
                        topOfParent()
                    },
                )
            } else {
                add(
                    text,
                    lParams(wrapContent, matchParent) {
                        startOfParent()
                        centerVertically()
                        before(comment)
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, matchParent) {
                        centerVertically()
                        endOfParent()
                    },
                )
            }
        }

    fun setText(str: String) {
        text.text = str
    }

    fun setComment(str: String) {
        comment.run {
            if (str.isNotEmpty()) {
                text = str
                if (visibility == View.GONE) visibility = View.VISIBLE
            } else if (visibility != View.GONE) {
                visibility = View.GONE
            }
        }
    }

    fun highlight(yes: Boolean) {
        if (yes) {
            maybeHighlightedCandidateTextColor?.let { text.setTextColor(it) }
            maybeHighlightedCommentTextColor?.let { comment.setTextColor(it) }
        } else {
            maybeCandidateTextColor?.let { text.setTextColor(it) }
            maybeCommentTextColor?.let { comment.setTextColor(it) }
        }
    }
}
