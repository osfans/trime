// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent

class SuggestionUi(
    override val ctx: Context,
    private val compatView: View,
) : Ui {
    override val root =
        ctx.constraintLayout {
            add(
                compatView,
                lParams(wrapContent, wrapContent) {
                    centerVertically()
                    startOfParent()
                    endOfParent()
                },
            )
        }
}
