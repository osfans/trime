package com.osfans.trime.ime.candidates

import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.pressHighlightDrawable
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
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
            background = ColorManager.getColor("hilited_candidate_back_color")?.let { pressHighlightDrawable(it) }
            if (theme.generalStyle.commentOnTop) {
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        topOfParent()
                        centerHorizontally()
                        above(text)

                        verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                        verticalBias = 0.5f
                    },
                )
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        below(comment)
                        centerHorizontally()
                        bottomOfParent()

                        verticalBias = 0.5f
                    },
                )
            } else {
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        startOfParent()
                        centerVertically()
                        before(comment)

                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                        horizontalBias = 0.5f
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        after(text)
                        centerVertically()
                        endOfParent()

                        horizontalBias = 0.5f
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
