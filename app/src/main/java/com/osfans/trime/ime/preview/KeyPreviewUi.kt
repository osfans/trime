/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.preview

import android.content.Context
import android.graphics.drawable.GradientDrawable
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView
import splitties.views.gravityCenter

class KeyPreviewUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    enum class Position {
        MIDDLE,
        LEFT,
        RIGHT,
    }

    override val root =
        textView {
            setCompoundDrawables(null, null, null, null)
            background =
                GradientDrawable().apply {
                    setColor(ColorManager.getColor("preview_back_color"))
                    cornerRadius = theme.generalStyle.roundCorner
                }
            setTextColor(ColorManager.getColor("preview_text_color"))
            textSize = theme.generalStyle.previewTextSize
            typeface = FontManager.getTypeface("preview_font")
            gravity = gravityCenter
        }

    fun setPreviewText(text: String) {
        root.text = text
    }

    fun setPreviewBackground(position: Position) {
        val background = root.background ?: return
        background.state = KEY_PREVIEW_BACKGROUND_STATE_TABLE[position.ordinal]
    }

    companion object {
        private val KEY_PREVIEW_BACKGROUND_STATE_TABLE =
            arrayOf(
                intArrayOf(), // middle
                intArrayOf(R.attr.state_left_edge), // left
                intArrayOf(R.attr.state_right_edge), // right
            )
    }
}
