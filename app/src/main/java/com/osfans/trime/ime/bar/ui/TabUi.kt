// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add

class TabUi(
    override val ctx: Context,
    theme: Theme,
) : Ui {
    private val backButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_back_24)

    private var external: View? = null

    private val size = ctx.dp(theme.generalStyle.run { candidateViewHeight + commentHeight })

    override val root =
        constraintLayout {
            add(
                backButton,
                lParams(size, size) {
                    startOfParent()
                    centerVertically()
                },
            )
        }

    fun setBackButtonOnClickListener(block: () -> Unit) {
        backButton.setOnClickListener {
            block()
        }
    }

    fun addExternal(
        view: View,
        showTitle: Boolean,
    ) {
        if (external != null) {
            throw IllegalStateException("TabBar external view is already present")
        }
        backButton.isVisible = showTitle
        external = view
        root.run {
            add(
                view,
                lParams(matchConstraints, size) {
                    centerVertically()
                    if (showTitle) {
                        endOfParent()
                    } else {
                        centerHorizontally()
                    }
                },
            )
        }
    }

    fun removeExternal() {
        external?.let {
            root.removeView(it)
            external = null
        }
    }
}
