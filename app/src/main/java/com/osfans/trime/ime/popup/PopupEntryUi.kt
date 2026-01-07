/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.popup

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.keyboard.toIconName
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter

class PopupEntryUi(override val ctx: Context, private val theme: Theme, keyHeight: Int, radius: Float) : Ui {

    var lastShowTime = -1L

    val textView = view(::AutoScaleTextView) {
        scaleMode = AutoScaleTextView.Mode.Proportional
        textSize = theme.generalStyle.popupTextSize
        gravity = gravityCenter
        setTextColor(ColorManager.getColor("popup_text_color"))
        typeface = FontManager.getTypeface("POPUP_FONT")
    }

    val imageView = view(::AppCompatImageView) {
        visibility = android.view.View.GONE
    }

    override val root = constraintLayout {
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(ColorManager.getColor("popup_back_color"))
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(2f)
        add(
            textView,
            lParams(wrapContent, keyHeight) {
                topOfParent()
                centerHorizontally()
            },
        )
        add(
            imageView,
            lParams(wrapContent, keyHeight) {
                topOfParent()
                centerHorizontally()
            },
        )
    }

    fun setText(text: String) {
        if (text.isIconFont) {
            imageView.setImageDrawable(
                IconicsDrawable(ctx, text.toIconName()).apply {
                    sizeDp = theme.generalStyle.popupTextSize.toInt()
                    colorFilter = PorterDuffColorFilter(ColorManager.getColor("popup_text_color"), PorterDuff.Mode.SRC_IN)
                },
            )
            imageView.isVisible = true
            textView.isVisible = false
        } else {
            textView.text = text
            textView.isVisible = true
            imageView.isVisible = false
        }
    }
}
