/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import androidx.core.view.isVisible
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityVerticalCenter

class TabUi(
    override val ctx: Context,
    theme: Theme,
) : Ui {
    private val backButton: ToolButton

    init {
        val firstButtonConfig = theme.toolBar.buttons.firstOrNull()
        val backButtonConfig = firstButtonConfig?.copy(
            foreground = firstButtonConfig.foreground.copy(
                style = theme.toolBar.backStyle,
            ),
        )
        backButton = if (backButtonConfig != null) {
            ToolButton(ctx, backButtonConfig)
        } else {
            ToolButton(ctx, R.drawable.ic_baseline_arrow_back_24)
        }
    }

    private val titleText = textView {
        typeface = Typeface.defaultFromStyle(Typeface.BOLD)
        gravity = gravityVerticalCenter
        textSize = theme.generalStyle.candidateTextSize
        setTextColor(ColorManager.getColor("key_text_color"))
    }

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
            add(
                titleText,
                lParams(wrapContent, size) {
                    after(backButton, dp(8))
                    centerVertically()
                },
            )
        }

    fun setBackButtonOnClickListener(block: () -> Unit) {
        backButton.setOnClickListener {
            block()
        }
    }

    fun setTitle(title: String) {
        titleText.text = title
    }

    fun addExternal(
        view: View,
        showTitle: Boolean,
    ) {
        if (external != null) {
            throw IllegalStateException("TabBar external view is already present")
        }
        backButton.isVisible = showTitle
        titleText.isVisible = showTitle
        external = view
        root.run {
            add(
                view,
                lParams(matchConstraints, size) {
                    centerVertically()
                    if (showTitle) {
                        after(titleText, dp(8))
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
