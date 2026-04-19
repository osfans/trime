/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.content.Context
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.R
import com.osfans.trime.ime.bar.ui.ToolButton
import com.osfans.trime.ime.symbol.SpacesItemDecoration
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView

class SegmentsUi(override val ctx: Context) : Ui {
    val recyclerView = recyclerView {
        itemAnimator = null
        addItemDecoration(SpacesItemDecoration(dp(4)))
    }

    val selectButton = ToolButton(ctx, R.drawable.ic_baseline_select_all_24)

    val shareButton = ToolButton(ctx, R.drawable.ic_baseline_share_24)

    val searchButton = ToolButton(ctx, R.drawable.ic_baseline_search_24)

    val starButton = ToolButton(ctx, R.drawable.ic_baseline_star_24)

    val copyButton = ToolButton(ctx, R.drawable.ic_baseline_content_copy_24)

    private val buttons = view(::FlexboxLayout) {
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.SPACE_AROUND

        val size = dp(48)
        add(selectButton, FlexboxLayout.LayoutParams(size, size))
        add(shareButton, FlexboxLayout.LayoutParams(size, size))
        add(searchButton, FlexboxLayout.LayoutParams(size, size))
        add(starButton, FlexboxLayout.LayoutParams(size, size))
        add(copyButton, FlexboxLayout.LayoutParams(size, size))
    }

    override val root = constraintLayout {
        add(
            recyclerView,
            lParams(matchParent, matchConstraints) {
                topOfParent(dp(4))
                centerHorizontally(dp(8))
                bottomToTopOf(buttons)
            },
        )
        add(
            buttons,
            lParams(matchParent, wrapContent) {
                topToBottomOf(recyclerView)
                startOfParent(dp(8))
                bottomOfParent(dp(4))
            },
        )
    }

    fun setSelectButtonToSelectAll() {
        selectButton.setIcon(R.drawable.ic_baseline_select_all_24)
    }

    fun setSelectButtonToDeselect() {
        selectButton.setIcon(R.drawable.ic_baseline_deselect_24)
    }
}
