// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import android.widget.Space
import android.widget.ViewAnimator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
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
import splitties.views.dsl.core.wrapContent
import timber.log.Timber

class AlwaysUi(
    override val ctx: Context,
    private val theme: Theme,
    private val onButtonClick: ((String?) -> Unit)? = null,
) : Ui {
    enum class State {
        Empty,
    }

    var currentState = State.Empty
        private set

    private val buttonSpacing = ctx.dp(theme.toolBar.buttonSpacing)
    private val dynamicButtons = mutableListOf<View>()

    val moreButton: ToolButton = createPrimaryButton()
    val emptyBar = Space(ctx).apply { id = View.generateViewId() }

    private val animator =
        ViewAnimator(ctx).apply {
            id = View.generateViewId()
            add(emptyBar, lParams(matchParent, matchParent))
        }

    override val root: ConstraintLayout = constraintLayout {
        add(
            moreButton,
            lParams(wrapContent, wrapContent) {
                startOfParent()
                centerVertically()
            },
        )
        add(
            animator,
            lParams(matchConstraints, matchParent) {
                after(moreButton)
                endOfParent()
                centerVertically()
            },
        )
    }

    init {
        theme.toolBar.buttons.forEach { buttonName ->
            val button = createConfigButton(buttonName)
            val layoutParams = getCustomButtonLayoutParams(buttonName)
            root.addView(button, layoutParams)
            dynamicButtons.add(button)
        }
        updateButtonConstraints()
    }

    fun updateButtonsStyle() {
        (dynamicButtons.asSequence() + moreButton)
            .filterIsInstance<ToolButton>()
            .forEach(ToolButton::updateStyle)
    }

    fun updateState(state: State) {
        Timber.d("Switch always ui to $state")
        when (state) {
            State.Empty -> animator.displayedChild = 0
        }
        currentState = state
    }

    private fun createPrimaryButton(): ToolButton {
        val primaryButton = theme.toolBar.primaryButton
        return (
            if (primaryButton != null) {
                ToolButton(ctx, primaryButton, "primary")
            } else {
                ToolButton(ctx, R.drawable.ic_baseline_more_horiz_24)
            }
            )
            .apply {
                id = View.generateViewId()
                setOnClickListener { onButtonClick?.invoke(primaryButton?.action) }
            }
    }

    private fun createConfigButton(buttonName: String): ToolButton {
        val config = theme.toolBar.customButtons[buttonName]
        return (
            if (config != null) {
                ToolButton(ctx, config, buttonName)
            } else {
                ToolButton(ctx, buttonName)
            }
            )
            .apply {
                id = View.generateViewId()
                setOnClickListener { onButtonClick?.invoke(config?.action ?: buttonName) }
            }
    }

    private fun getCustomButtonLayoutParams(buttonName: String): ConstraintLayout.LayoutParams {
        val config = theme.toolBar.customButtons[buttonName]
        return if (config?.foreground?.size?.size == 2) {
            val size = config.foreground.size
            ConstraintLayout.LayoutParams(ctx.dp(size[0]), ctx.dp(size[1]))
        } else {
            ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun updateButtonConstraints() {
        val constraintSet = ConstraintSet().apply { clone(root) }

        val animatorEndTarget =
            if (dynamicButtons.isEmpty()) {
                ConstraintSet.PARENT_ID to ConstraintSet.END
            } else {
                dynamicButtons.last().id to ConstraintSet.START
            }
        constraintSet.connect(
            animator.id,
            ConstraintSet.END,
            animatorEndTarget.first,
            animatorEndTarget.second,
        )

        dynamicButtons.forEachIndexed { index, button ->
            constraintSet.connect(
                button.id,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
            )
            constraintSet.connect(
                button.id,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
            )

            when {
                index == 0 -> {
                    constraintSet.connect(
                        button.id,
                        ConstraintSet.END,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.END,
                    )
                    if (dynamicButtons.size == 1) {
                        constraintSet.connect(
                            button.id,
                            ConstraintSet.START,
                            animator.id,
                            ConstraintSet.END,
                            buttonSpacing,
                        )
                    }
                }
                index == dynamicButtons.size - 1 -> {
                    constraintSet.connect(
                        button.id,
                        ConstraintSet.START,
                        animator.id,
                        ConstraintSet.END,
                        buttonSpacing,
                    )
                    constraintSet.connect(
                        button.id,
                        ConstraintSet.END,
                        dynamicButtons[index - 1].id,
                        ConstraintSet.START,
                        buttonSpacing,
                    )
                }
                else -> {
                    constraintSet.connect(
                        button.id,
                        ConstraintSet.END,
                        dynamicButtons[index - 1].id,
                        ConstraintSet.START,
                        buttonSpacing,
                    )
                }
            }
        }

        constraintSet.applyTo(root)
    }
}
