/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.text.TextUtils
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.rippleDrawable
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalMargin
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

class ClipboardSuggestionUi(
    override val ctx: Context,
) : Ui {
    private val icon =
        imageView {
            imageDrawable =
                drawable(R.drawable.ic_clipboard_24)!!.apply {
                    setTint(ColorManager.getColor("candidate_text_color"))
                }
        }

    val text =
        textView {
            isSingleLine = true
            maxWidth = dp(240)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ColorManager.getColor("candidate_text_color"))
        }

    private val layout =
        constraintLayout {
            val spacing = dp(4)
            add(
                icon,
                lParams(dp(20), dp(20)) {
                    startOfParent(spacing)
                    before(text)
                    centerVertically()
                },
            )
            add(
                text,
                lParams(wrapContent, wrapContent) {
                    after(icon, spacing)
                    endOfParent(spacing)
                    centerVertically()
                },
            )
        }

    val suggestionView = GestureFrame(ctx).apply {
        add(layout, lParams(wrapContent, matchParent))
        background = rippleDrawable(ColorManager.getColor("hilited_candidate_back_color"))
    }

    override val root =
        constraintLayout {
            add(
                suggestionView,
                lParams(wrapContent, matchConstraints) {
                    centerInParent()
                    verticalMargin = dp(4)
                },
            )
        }
}
