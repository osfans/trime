/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.content.Context
import android.view.View
import android.view.ViewGroup
import splitties.dimensions.dp
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDimenPxSize
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.checkBox
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp
import splitties.views.textAppearance

class SchemaListEntryUi(
    override val ctx: Context,
) : Ui {
    val checkBox = checkBox()
    val nameText =
        textView {
            setPaddingDp(0, 16, 0, 16)
            textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        }

    override val root: View =
        constraintLayout {
            layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
            backgroundColor = styledColor(android.R.attr.colorBackground)
            minHeight = styledDimenPxSize(android.R.attr.listPreferredItemHeightSmall)

            val paddingStart = styledDimenPxSize(android.R.attr.listPreferredItemPaddingStart)
            add(
                checkBox,
                lParams {
                    width = dp(30)
                    height = matchConstraints
                    centerVertically()
                    startOfParent(paddingStart)
                },
            )

            add(
                nameText,
                lParams {
                    width = matchConstraints
                    height = wrapContent
                    centerVertically()
                    after(checkBox, paddingStart)
                },
            )
        }
}
