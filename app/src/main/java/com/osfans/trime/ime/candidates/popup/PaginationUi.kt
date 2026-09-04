/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.osfans.trime.R
import com.osfans.trime.core.Candidates
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.util.styledFloat
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.imageDrawable

class PaginationUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private fun createIcon(
        @DrawableRes icon: Int,
    ) = imageView {
        imageTintList = ColorStateList.valueOf(scope.colors.keyTextColor)
        imageDrawable = drawable(icon)
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val prevIcon = createIcon(R.drawable.ic_baseline_arrow_left_24)
    val nextIcon = createIcon(R.drawable.ic_baseline_arrow_right_24)

    private val disabledAlpha = ctx.styledFloat(android.R.attr.disabledAlpha)

    override val root =
        constraintLayout {
            val w = dp(10)
            val h = dp(20)
            add(
                nextIcon,
                lParams(w, h) {
                    centerVertically()
                    endOfParent()
                },
            )
            add(
                prevIcon,
                lParams(w, h) {
                    centerVertically()
                    before(nextIcon)
                },
            )
        }

    fun update(paged: Candidates.Paged) {
        prevIcon.alpha = if (paged.hasPrevPage) 1f else disabledAlpha
        nextIcon.alpha = if (paged.hasNextPage) 1f else disabledAlpha
    }
}
