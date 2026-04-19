/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.ViewOutlineProvider
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.resources.styledDrawable
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.setPaddingDp

class SegmentUi(override val ctx: Context, theme: Theme) : Ui {
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
                        ColorManager.getColor("key_text_color"),
                        ColorManager.getColor("hilited_key_text_color"),
                    ),
                ),
            )
        }

    override val root = GestureFrame(ctx).apply {
        isClickable = true
        background = StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_selected),
                ColorManager.getDecorDrawable(
                    "key_back_color",
                    cornerRadius = ctx.dp(theme.generalStyle.roundCorner),
                ),
            )
            addState(
                intArrayOf(android.R.attr.state_selected),
                ColorManager.getDecorDrawable(
                    "hilited_key_back_color",
                    cornerRadius = dp(theme.generalStyle.roundCorner),
                ),
            )
        }
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
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
