/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import androidx.annotation.DrawableRes
import androidx.core.view.children
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.view
import kotlin.collections.component1
import kotlin.collections.component2

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
        theme.toolBar.buttons.forEachIndexed { index, config ->
            val (width, height) = if (config.foreground?.size?.size == 2) {
                config.foreground.size
            } else {
                List(2) { defaultButtonSize }
            }.map { if (it < 0) it else ctx.dp(it) }
            val button = toolButton(config)
            val lParams = FlexboxLayout.LayoutParams(width, height).apply {
                if (index != 0 && index != theme.toolBar.buttons.lastIndex) {
                    marginEnd = ctx.dp(theme.toolBar.buttonSpacing)
                }
            }
            root.addView(button, lParams)
        }
    }

    fun updateStyle() {
        root.children.forEach { (it as ToolButton).updateStyle() }
    }
}
