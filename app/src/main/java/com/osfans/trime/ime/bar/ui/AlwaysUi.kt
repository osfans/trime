// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.widget.Space
import android.widget.ViewAnimator
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
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
    }

    var currentState = State.Empty
        private set

    val moreButton = ToolButton(ctx, R.drawable.ic_baseline_more_horiz_24)

    val hideKeyboardButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_drop_down_24)

    val emptyBar = Space(ctx)

    private val animator =
        ViewAnimator(ctx).apply {
            add(emptyBar, lParams(matchParent, matchParent))
        }

    override val root =
        constraintLayout {
            val size = dp(theme.generalStyle.run { candidateViewHeight + commentHeight })
            add(
                moreButton,
                lParams(size, size) {
                    startOfParent()
                    centerVertically()
                },
            )
            add(
                hideKeyboardButton,
                lParams(size, size) {
                    endOfParent()
                    centerVertically()
                },
            )
            add(
                animator,
                lParams(matchConstraints, matchParent) {
                    after(moreButton)
                    before(hideKeyboardButton)
                    centerVertically()
                },
            )
        }

    fun updateState(state: State) {
        Timber.d("Switch always ui to $state")
        when (state) {
            State.Empty -> animator.displayedChild = 0
        }
        currentState = state
    }
}
