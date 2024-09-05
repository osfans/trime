// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateViewHolder
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

open class CompactCandidateViewAdapter(
    val theme: Theme,
) : BaseQuickAdapter<CandidateItem, CandidateViewHolder>() {
    var sticky: Int = 0
        private set

    var isLastPage: Boolean = false
        private set

    var previous: Int = 0
        private set

    var highlightedIdx: Int = -1
        private set

    val before: Int
        get() = sticky + previous

    fun updateCandidates(
        list: List<CandidateItem>,
        isLastPage: Boolean,
        previous: Int,
        highlightedIdx: Int,
        sticky: Int = 0,
    ) {
        this.isLastPage = isLastPage
        this.previous = previous
        this.sticky = sticky
        this.highlightedIdx = highlightedIdx
        super.submitList(list.drop(sticky))
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder {
        val ui = CandidateItemUi(context, theme)
        ui.root.apply {
            minimumWidth = dp(40)
            val size = theme.generalStyle.candidatePadding
            setPaddingDp(size, 0, size, 0)
            layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
        item: CandidateItem?,
    ) {
        val (comment, text) = item!!
        val idx = sticky + position
        holder.ui.run {
            label.text = text
            altLabel.text = comment
            highlight(theme.generalStyle.candidateUseCursor && idx == highlightedIdx)
        }
        holder.text = text
        holder.comment = comment
        holder.idx = before + position // unused
        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
            minWidth = 0
            flexGrow = 0f
        }
    }
}
