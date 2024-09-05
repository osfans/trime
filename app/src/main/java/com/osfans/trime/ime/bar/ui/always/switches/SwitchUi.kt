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
import com.osfans.trime.util.rippleDrawable
import splitties.dimensions.dp
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
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding

class SwitchUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    var enabled: Int = -1

    private val label =
        textView {
            textSize = theme.generalStyle.candidateTextSize.toFloat()
            typeface = FontManager.getTypeface("candidate_font")
            ColorManager.getColor("candidate_text_color")?.let { setTextColor(it) }
        }

    private val altLabel =
        textView {
            textSize = theme.generalStyle.commentTextSize.toFloat()
            typeface = FontManager.getTypeface("comment_font")
            ColorManager.getColor("comment_text_color")?.let { setTextColor(it) }
            visibility = View.GONE
        }

    override val root =
        constraintLayout {
            horizontalPadding = dp(theme.generalStyle.candidatePadding)
            layoutParams = ViewGroup.LayoutParams(wrapContent, matchParent)
            background = rippleDrawable(ColorManager.getColor("hilited_candidate_back_color")!!)
            if (theme.generalStyle.commentOnTop) {
                add(
                    altLabel,
                    lParams(wrapContent, wrapContent) {
                        bottomMargin = dp(-3)
                        topOfParent()
                        above(label)
                        centerHorizontally()
                    },
                )
                add(
                    label,
                    lParams(wrapContent, wrapContent) {
                        topMargin = dp(-3)
                        below(altLabel)
                        centerHorizontally()
                        bottomOfParent()
                    },
                )
            } else {
                add(
                    label,
                    lParams(wrapContent, wrapContent) {
                        startOfParent()
                        before(altLabel)
                        centerVertically()
                    },
                )
                add(
                    altLabel,
                    lParams(wrapContent, wrapContent) {
                        after(label)
                        centerVertically()
                        endOfParent()
                    },
                )
            }
        }

    fun setLabel(str: String) {
        label.text = str
    }

    fun setAltLabel(str: String) {
        altLabel.run {
            if (str.isNotEmpty()) {
                text = str
                if (visibility == View.GONE) visibility = View.VISIBLE
            } else if (visibility != View.GONE) {
                visibility = View.GONE
            }
        }
    }
}
