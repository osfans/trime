// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.widget.ViewAnimator
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
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
    private val onButtonClick: ((String?) -> Unit)? = null,
) : Ui {
    enum class State {
        Toolbar,
    }

    var currentState = State.Toolbar
        private set

    private fun toolButton(
        buttonConfig: ToolBar.Button?,
        @DrawableRes icon: Int = 0,
    ): ToolButton = if (buttonConfig != null) {
        ToolButton(ctx, buttonConfig)
    } else {
        ToolButton(ctx, icon)
    }.apply {
        setOnClickListener { onButtonClick?.invoke(buttonConfig?.action) }
    }

    val moreButton: ToolButton = toolButton(
        theme.toolBar.primaryButton,
        R.drawable.ic_baseline_more_horiz_24,
    )
    val hideKeyboardButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_drop_down_24)

    val buttonsUi = ButtonsBarUi(ctx, theme, onButtonClick)

    private val animator =
        ViewAnimator(ctx).apply {
            add(buttonsUi.root, lParams(matchParent, matchParent))
        }

    override val root: ConstraintLayout = constraintLayout {
        val defaultButtonSize = theme.generalStyle.run { candidateViewHeight + commentHeight }
        val (width, height) = theme.toolBar.primaryButton?.foreground?.size
            ?.takeIf { it.size == 2 }
            ?: List(2) { defaultButtonSize }
        add(
            moreButton,
            lParams(dp(width), dp(height)) {
                startOfParent()
                centerVertically()
            },
        )
        if (theme.toolBar.buttons.isEmpty()) {
            val size = dp(defaultButtonSize)
            add(
                hideKeyboardButton,
                lParams(size, size) {
                    endOfParent()
                    centerVertically()
                },
            )
        }
        add(
            animator,
            lParams(matchConstraints, matchParent) {
                after(moreButton)
                if (theme.toolBar.buttons.isEmpty()) {
                    before(hideKeyboardButton)
                }
                endOfParent()
                centerVertically()
            },
        )
    }

    fun updateButtonsStyle() {
        moreButton.updateStyle()
        buttonsUi.updateStyle()
    }

    fun updateState(state: State) {
        Timber.d("Switch always ui to $state")
        when (state) {
            State.Toolbar -> animator.displayedChild = 0
        }
        currentState = state
    }
}
