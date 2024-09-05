// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent

class TabUi(
    override val ctx: Context,
) : Ui {
    private var external: View? = null

    override val root = constraintLayout { }

    fun addExternal(view: View) {
        if (external != null) {
            throw IllegalStateException("TabBar external view is already present")
        }
        external = view
        root.run {
            add(
                view,
                lParams(matchParent, matchParent) {
                    centerInParent()
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
