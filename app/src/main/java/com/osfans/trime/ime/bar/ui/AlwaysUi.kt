// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.widget.Space
import android.widget.ViewAnimator
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ui.always.switches.SwitchesUi
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class AlwaysUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    enum class State {
        Empty,
        Switchers,
    }

    var currentState = State.Empty
        private set

    val emptyBar = Space(ctx)

    val switchesUi = SwitchesUi(ctx, theme)

    private val animator =
        ViewAnimator(ctx).apply {
            add(emptyBar, lParams(matchParent, matchParent))
            add(switchesUi.root, lParams(matchParent, matchParent))
        }

    override val root =
        constraintLayout {
            add(
                animator,
                lParams(matchConstraints, matchParent) {
                    centerInParent()
                },
            )
        }

    fun updateState(state: State) {
        Timber.d("Switch always ui to $state")
        when (state) {
            State.Empty -> animator.displayedChild = 0
            State.Switchers -> animator.displayedChild = 1
        }
        currentState = state
    }
}
