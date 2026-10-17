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
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.Candidates
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.BoundActionImageView
import com.osfans.trime.ime.candidates.VersionedClickBinding
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
import splitties.views.dsl.core.view
import splitties.views.imageDrawable

class PaginationUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private fun createIcon(
        @DrawableRes icon: Int,
    ) = view(::BoundActionImageView) {
        imageTintList = ColorStateList.valueOf(scope.colors.keyTextColor)
        imageDrawable = drawable(icon)
        scaleType = ImageView.ScaleType.CENTER
    }

    val prevIcon: ImageView = createIcon(R.drawable.ic_baseline_arrow_left_24)
    val nextIcon: ImageView = createIcon(R.drawable.ic_baseline_arrow_right_24)
    private val prevAction = VersionedClickBinding<CandidateSnapshot>(prevIcon, R.string.a11y_previous_candidates)
    private val nextAction = VersionedClickBinding<CandidateSnapshot>(nextIcon, R.string.a11y_next_candidates)

    init {
        prevIcon.contentDescription = ctx.getString(R.string.a11y_previous_candidates)
        nextIcon.contentDescription = ctx.getString(R.string.a11y_next_candidates)
    }

    private val disabledAlpha = ctx.styledFloat(android.R.attr.disabledAlpha)

    override val root =
        constraintLayout {
            val w = dp(48)
            val h = dp(48)
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

    fun update(paged: Candidates.Paged, onPrev: () -> Unit, onNext: () -> Unit) {
        // Re-apply the tint as well: a scheme switch rebinds the pagination holder
        // (see PagedCandidatesUi.refreshColors), which only calls update().
        val tint = ColorStateList.valueOf(scope.colors.keyTextColor)
        prevIcon.imageTintList = tint
        nextIcon.imageTintList = tint
        prevIcon.alpha = if (paged.hasPrevPage) 1f else disabledAlpha
        nextIcon.alpha = if (paged.hasNextPage) 1f else disabledAlpha
        prevIcon.isEnabled = paged.hasPrevPage
        nextIcon.isEnabled = paged.hasNextPage
        prevAction.bind(paged.snapshot.takeIf { it.isValid && paged.hasPrevPage }, onPrev)
        nextAction.bind(paged.snapshot.takeIf { it.isValid && paged.hasNextPage }, onNext)
    }
}
