// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add

class SuggestionUi(
    override val ctx: Context,
    private val compatView: View,
) : Ui {
    val homeButton =
        ToolButton(ctx, R.drawable.ic_trime_status)

    override val root =
        ctx.constraintLayout {
            add(
                homeButton,
                lParams(dp(40)) {
                    centerVertically()
                    startOfParent()
                },
            )
            add(
                compatView,
                lParams {
                    centerVertically()
                    after(homeButton)
                    endOfParent()
                },
            )
        }
}
