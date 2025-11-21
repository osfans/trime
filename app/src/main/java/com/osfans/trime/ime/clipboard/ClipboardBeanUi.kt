/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.setPaddingDp

class ClipboardBeanUi(override val ctx: Context, private val theme: Theme) : Ui {
    val textView =
        textView {
            minLines = 1
            maxLines = 4
            textSize = 14f
            typeface = FontManager.getTypeface("key_font")
            setPaddingDp(8, 4, 8, 4)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ColorManager.getColor("key_text_color"))
        }

    val pin =
        imageView {
            imageDrawable =
                drawable(R.drawable.ic_baseline_push_pin_24)!!.apply {
                    setTint(ColorManager.getColor("key_symbol_color"))
                    setAlpha(0.3f)
                }
        }

    val layout =
        constraintLayout {
            add(
                textView,
                lParams(matchParent, wrapContent) {
                    centerVertically()
                },
            )
            add(
                pin,
                lParams(dp(12), dp(12)) {
                    bottomOfParent(dp(2))
                    endOfParent(dp(2))
                },
            )
        }

    override val root =
        GestureFrame(ctx).apply {
            isClickable = true
            minimumHeight = dp(30)
            background =
                ColorManager.getDecorDrawable(
                    "key_back_color",
                    "key_border_color",
                    dp(theme.generalStyle.keyBorder),
                    dp(theme.generalStyle.roundCorner),
                )
            add(layout, lParams(matchParent, matchParent))
            layoutParams = lParams(matchParent, wrapContent)
        }

    fun setBean(
        text: String,
        pinned: Boolean,
    ) {
        textView.text = text
        pin.visibility = if (pinned) View.VISIBLE else View.GONE
    }
}
