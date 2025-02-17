// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.util.pressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.horizontalChain
import splitties.views.dsl.constraintlayout.packed
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.verticalChain
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class CandidateItemUi(
    override val ctx: Context,
    theme: Theme,
) : Ui {
    private val firstTextSize = theme.generalStyle.candidateTextSize
    private val lastTextSize = theme.generalStyle.commentTextSize
    private val firstTextFont = FontManager.getTypeface("candidate_font")
    private val lastTextFont = FontManager.getTypeface("comment_font")
    private val firstTextColor = ColorManager.getColor("candidate_text_color")
    private val lastTextColor = ColorManager.getColor("comment_text_color")
    private val lastTextColorH = ColorManager.getColor("hilited_comment_text_color")
    private val firstTextColorH = ColorManager.getColor("hilited_candidate_text_color")
    private val firstBackColorH = ColorManager.getColor("hilited_candidate_back_color")

    private val firstText =
        view(::AutoScaleTextView) {
            textSize = firstTextSize
            typeface = firstTextFont
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val lastText =
        view(::AutoScaleTextView) {
            textSize = lastTextSize
            typeface = lastTextFont
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    override val root =
        constraintLayout {
            if (theme.generalStyle.commentOnTop) {
                verticalChain(
                    listOf(lastText, firstText),
                    style = packed,
                    defaultWidth = wrapContent,
                    initFirstViewParams = {
                        height = dp(theme.generalStyle.commentHeight)
                        topOfParent()
                    },
                    initLastViewParams = {
                        height = dp(theme.generalStyle.candidateViewHeight)
                        bottomOfParent()
                    },
                    initParams = { centerHorizontally() },
                )
            } else {
                horizontalChain(
                    listOf(firstText, lastText),
                    style = packed,
                    defaultWidth = wrapContent,
                    initParams = { centerVertically() },
                )
            }
        }

    fun update(
        item: CandidateItem,
        isHighlighted: Boolean,
        obtainLast: Boolean,
    ) {
        val firstColor = if (isHighlighted) firstTextColorH else firstTextColor
        val lastColor = if (isHighlighted) lastTextColorH else lastTextColor
        firstText.text = item.text
        firstText.setTextColor(firstColor)
        lastText.run {
            if (obtainLast) {
                lastText.text = item.comment
                lastText.setTextColor(lastColor)
                if (visibility == View.GONE) visibility = View.VISIBLE
            } else if (visibility != View.GONE) {
                visibility = View.GONE
            }
        }
        root.background =
            if (isHighlighted) {
                ColorDrawable(firstBackColorH)
            } else {
                pressHighlightDrawable(firstBackColorH)
            }
    }
}
