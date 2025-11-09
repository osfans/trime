/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.content.Context
import android.view.ViewGroup
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDimenPxSize
import splitties.resources.styledDrawable
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageButton
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.setPaddingDp
import splitties.views.textAppearance

class UserDictListEntryUi(
    override val ctx: Context,
) : Ui {
    val nameText = textView {
        setPaddingDp(0, 16, 0, 16)
        textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
    }

    val moreButton = imageButton {
        background = styledDrawable(android.R.attr.selectableItemBackground)
        imageDrawable = drawable(R.drawable.ic_baseline_more_horiz_24)
    }

    override val root = constraintLayout {
        layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
        backgroundColor = styledColor(android.R.attr.colorBackground)
        minHeight = styledDimenPxSize(android.R.attr.listPreferredItemHeightSmall)

        val paddingStart = styledDimenPxSize(android.R.attr.listPreferredItemPaddingStart)
        add(
            nameText,
            lParams {
                width = matchConstraints
                height = wrapContent
                centerVertically()
                startOfParent(paddingStart)
                before(moreButton)
            },
        )
        add(
            moreButton,
            lParams {
                width = dp(53)
                height = matchConstraints
                centerVertically()
                endOfParent()
            },
        )
    }
}
