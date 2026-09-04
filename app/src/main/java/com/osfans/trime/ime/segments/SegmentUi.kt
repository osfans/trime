/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.view.ViewOutlineProvider
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.setPaddingDp

class SegmentUi(override val ctx: Context, private val scope: ThemeScope) : Ui {
    private val theme: Theme get() = scope.theme

    private val spacing = ctx.dp(4)

    val textView =
        textView {
            textSize = 16f
            isSingleLine = true
            typeface = FontManager.getTypeface("key_font")
            setPaddingDp(8, 4, 8, 4)
            setTextColor(
                ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_selected),
                        intArrayOf(android.R.attr.state_selected),
                    ),
                    intArrayOf(
                        scope.colors.keyTextColor,
                        scope.colors.hilitedKeyTextColor,
                    ),
                ),
            )
        }

    override val root = GestureFrame(ctx).apply {
        isClickable = true
        background = StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_selected),
                scope.decorDrawable(
                    "key_back_color",
                    cornerRadius = ctx.dp(theme.generalStyle.roundCorner),
                ),
            )
            addState(
                intArrayOf(android.R.attr.state_selected),
                scope.decorDrawable(
                    "hilited_key_back_color",
                    cornerRadius = dp(theme.generalStyle.roundCorner),
                ),
            )
        }
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, wrapContent).apply {
            setMargins(spacing, spacing, spacing, spacing)
        }
        add(
            textView,
            lParams(wrapContent, wrapContent) {
                gravity = gravityCenter
            },
        )
    }

    fun update(isSelected: Boolean) {
        root.isSelected = isSelected
        textView.isSelected = isSelected
    }
}
