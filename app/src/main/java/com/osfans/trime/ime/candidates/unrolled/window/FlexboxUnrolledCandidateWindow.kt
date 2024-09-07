// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled.window

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.transition.Slide
import androidx.transition.Transition
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.CompactCandidateModule
import com.osfans.trime.ime.candidates.adapter.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxHorizontalDecoration
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import splitties.dimensions.dp
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

class FlexboxUnrolledCandidateWindow(
    context: Context,
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
    bar: QuickBar,
    windowManager: BoardWindowManager,
    compactCandidate: CompactCandidateModule,
) : BaseUnrolledCandidateWindow(context, service, rime, theme, bar, windowManager, compactCandidate) {
    override fun exitAnimation(nextWindow: BoardWindow): Transition =
        Slide().apply {
            slideEdge = Gravity.TOP
        }

    override val adapter by lazy {
        object : PagingCandidateViewAdapter(theme) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): CandidateViewHolder =
                super.onCreateViewHolder(parent, viewType).apply {
                    itemView.apply {
                        minimumWidth = dp(40)
                        val size = theme.generalStyle.candidatePadding
                        setPaddingDp(size, 0, size, 0)
                        layoutParams =
                            FlexboxLayoutManager
                                .LayoutParams(wrapContent, dp(theme.generalStyle.run { candidateViewHeight + commentHeight }))
                                .apply { flexGrow = 1f }
                    }
                }

            override fun onBindViewHolder(
                holder: CandidateViewHolder,
                position: Int,
            ) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    override val layoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            justifyContent = JustifyContent.SPACE_AROUND
            alignItems = AlignItems.FLEX_START
        }
    }

    override fun onCreateCandidateLayout(): UnrolledCandidateLayout =
        UnrolledCandidateLayout(context, theme).apply {
            recyclerView.apply {
                adapter = this@FlexboxUnrolledCandidateWindow.adapter
                layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
                addItemDecoration(FlexboxHorizontalDecoration(separatorDrawable))
            }
        }
}
