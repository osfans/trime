/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.view.children
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.view

class ButtonsBarUi(
    override val ctx: Context,
    private val theme: Theme,
    private val onButtonClick: ((String?) -> Unit)? = null,
) : Ui {
    override val root = view(::FlexboxLayout) {
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.FLEX_START
        flexDirection = FlexDirection.ROW_REVERSE
    }

    val firstButton: ToolButton?

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

    init {
        val defaultButtonSize = theme.generalStyle.run { candidateViewHeight + commentHeight }
        val buttons = theme.toolBar.buttons
        firstButton = buttons.firstOrNull()?.let { toolButton(it) }

        buttons.drop(1).forEach { config ->
            val button = toolButton(config)
            val size = getButtonSize(config, defaultButtonSize)
            val lParams = FlexboxLayout.LayoutParams(size.first, size.second).apply {
                marginEnd = ctx.dp(theme.toolBar.buttonSpacing)
            }
            root.addView(button, lParams)
        }
    }

    private fun getButtonSize(config: ToolBar.Button, defaultSize: Int): Pair<Int, Int> {
        val sizeList = config.foreground?.size?.takeIf { it.size == 2 } ?: List(2) { defaultSize }
        val (width, height) = sizeList

        val finalWidth = if (width >= 0) ctx.dp(width) else width
        val finalHeight = if (height >= 0) ctx.dp(height) else height

        return finalWidth to finalHeight
    }

    fun updateStyle() {
        root.children.forEach { (it as ToolButton).updateStyle() }
    }
}
