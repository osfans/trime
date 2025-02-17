// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui.always.switches

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.util.rippleDrawable
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
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

class SwitchUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    var enabled: Int = -1

    private val firstText =
        view(::AutoScaleTextView) {
            textSize = theme.generalStyle.candidateTextSize
            typeface = FontManager.getTypeface("candidate_font")
            isSingleLine = true
            gravity = gravityCenter
            setTextColor(ColorManager.getColor("candidate_text_color"))
        }

    private val lastText =
        view(::AutoScaleTextView) {
            textSize = theme.generalStyle.commentTextSize
            typeface = FontManager.getTypeface("comment_font")
            isSingleLine = true
            gravity = gravityCenter
            setTextColor(ColorManager.getColor("comment_text_color"))
            visibility = View.GONE
        }

    override val root =
        constraintLayout {
            horizontalPadding = dp(theme.generalStyle.candidatePadding)
            layoutParams = ViewGroup.LayoutParams(wrapContent, matchParent)
            background = rippleDrawable(ColorManager.getColor("hilited_candidate_back_color"))
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

    fun setFirstText(str: String) {
        firstText.text = str
    }

    fun setLastText(str: String) {
        lastText.run {
            if (str.isNotEmpty()) {
                text = str
                if (visibility == View.GONE) visibility = View.VISIBLE
            } else if (visibility != View.GONE) {
                visibility = View.GONE
            }
        }
    }
}
