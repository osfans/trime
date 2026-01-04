/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

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
        Clipboard,
        InlineSuggestion,
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

    val buttonsUi = ButtonsBarUi(ctx, theme, onButtonClick)

    val clipboardUi = ClipboardSuggestionUi(ctx)

    val inlineSuggestionsUi = InlineSuggestionsUi(ctx)

    val hideKeyboardButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_drop_down_24)
    private val rightButtonAnimator =
        ViewAnimator(ctx).apply {
            add(hideKeyboardButton, lParams(matchParent, matchParent))
            buttonsUi.firstButton?.let { add(it, lParams(matchParent, matchParent)) }
        }

    private val backButton: ToolButton
    private val moreButtonAnimator =
        ViewAnimator(ctx).apply {
            add(moreButton, lParams(matchParent, matchParent))
            backButton =
                createBackButton().also {
                    add(it, lParams(matchParent, matchParent))
                }
        }

    private val animator =
        ViewAnimator(ctx).apply {
            add(buttonsUi.root, lParams(matchParent, matchParent))
            add(clipboardUi.root, lParams(matchParent, matchParent))
            add(inlineSuggestionsUi.root, lParams(matchParent, matchParent))
        }

    override val root: ConstraintLayout = constraintLayout {
        val defaultButtonSize = theme.generalStyle.run { candidateViewHeight + commentHeight }

        fun getButtonSize(config: ToolBar.Button?): Pair<Int, Int> {
            val sizeList = config?.foreground?.size?.takeIf { it.size == 2 } ?: List(2) { defaultButtonSize }
            val (width, height) = sizeList
            return width to height
        }

        val (primaryWidth, primaryHeight) = getButtonSize(theme.toolBar.primaryButton)
        val (rightWidth, rightHeight) = getButtonSize(theme.toolBar.buttons.firstOrNull())

        add(
            moreButtonAnimator,
            lParams(dp(primaryWidth), dp(primaryHeight)) {
                startOfParent()
                centerVertically()
            },
        )
        add(
            rightButtonAnimator,
            lParams(dp(rightWidth), dp(rightHeight)) {
                endOfParent()
                centerVertically()
            },
        )
        add(
            animator,
            lParams(matchConstraints, matchParent) {
                after(moreButtonAnimator)
                before(rightButtonAnimator)
                endOfParent()
                centerVertically()
            },
        )
    }.apply {
        updateRightButton(State.Toolbar)
    }

    private fun createBackButton(): ToolButton {
        val firstConfig = theme.toolBar.buttons.firstOrNull()
        val backConfig =
            if (ToolButton.getContentType(firstConfig?.foreground?.style) == ToolButton.ContentType.TEXT) {
                firstConfig?.copy(foreground = firstConfig.foreground?.copy(style = "ic@arrow-left"))
            } else {
                null
            }

        return toolButton(backConfig, R.drawable.ic_baseline_arrow_back_24).apply {
            setOnClickListener { updateState(State.Toolbar) }
        }
    }

    fun updateButtonsStyle() {
        moreButton.updateStyle()
        backButton.updateStyle()
        buttonsUi.firstButton?.updateStyle()
        buttonsUi.updateStyle()
    }

    fun updateState(state: State) {
        Timber.d("Switch always ui to $state")
        when (state) {
            State.Toolbar -> animator.displayedChild = 0
            State.Clipboard -> animator.displayedChild = 1
            State.InlineSuggestion -> animator.displayedChild = 2
        }
        currentState = state
        updateRightButton(state)
        updateMoreButton(state)
    }

    private fun updateRightButton(state: State) {
        val shouldShowFirstButton = buttonsUi.firstButton != null &&
            !(theme.toolBar.buttons.isEmpty() && state == State.Toolbar)
        rightButtonAnimator.displayedChild = if (shouldShowFirstButton) 1 else 0
    }

    private fun updateMoreButton(state: State) {
        moreButtonAnimator.displayedChild = if (state == State.Toolbar) 0 else 1
    }
}
