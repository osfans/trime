/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.setPaddingDp

class SimpleItemUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    val textView =
        textView {
            minLines = 1
            maxLines = 4
            textSize = theme.generalStyle.keyLongTextSize
            typeface = FontManager.getTypeface("long_text_font")
            setPaddingDp(8, 4, 8, 4)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ColorManager.getColor("long_text_color"))
        }

    val pin =
        imageView {
            imageDrawable =
                drawable(R.drawable.ic_baseline_push_pin_24)!!.apply {
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
        frameLayout {
            isClickable = true
            minimumHeight = dp(30)
            background =
                ColorManager.getDrawable(
                    "long_text_back_color",
                    "key_long_text_border",
                    dp(theme.generalStyle.keyBorder),
                    dp(theme.generalStyle.roundCorner),
                    cache = false,
                )
            add(layout, lParams(matchParent, matchParent))
        }

    fun setItem(
        text: String,
        pinned: Boolean,
    ) {
        textView.text = text
        pin.visibility = if (pinned) View.VISIBLE else View.GONE
    }
}
