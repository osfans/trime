// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.util.circlePressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageResource
import splitties.views.padding

class ToolButton(
    context: Context,
) : FrameLayout(context) {
    val image =
        imageView {
            isClickable = false
            isFocusable = false
            padding = dp(10)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

    constructor(
        context: Context,
        @DrawableRes icon: Int,
    ) : this(context) {
        image.imageTintList = ColorStateList.valueOf(ColorManager.getColor("comment_text_color"))
        setIcon(icon)
        setPressHighlightColor(ColorManager.getColor("hilited_candidate_button_color"))
        add(image, lParams(wrapContent, wrapContent, gravityCenter))
    }

    fun setIcon(
        @DrawableRes icon: Int,
    ) {
        image.imageResource = icon
    }

    fun setPressHighlightColor(
        @ColorInt color: Int,
    ) {
        background = circlePressHighlightDrawable(color)
    }
}
