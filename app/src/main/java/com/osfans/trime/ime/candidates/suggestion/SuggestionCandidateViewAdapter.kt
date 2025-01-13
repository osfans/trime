// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

open class SuggestionCandidateViewAdapter(
    val theme: Theme,
) : BaseQuickAdapter<SuggestionViewItem, SuggestionViewHolder>() {
    var isLastPage: Boolean = false
        private set

    var previous: Int = 0
        private set

    var highlightedIdx: Int = -1
        private set

    fun updateCandidates(
        list: List<SuggestionViewItem>,
        isLastPage: Boolean,
        previous: Int,
        highlightedIdx: Int,
    ) {
        this.isLastPage = isLastPage
        this.previous = previous
        this.highlightedIdx = highlightedIdx
        super.submitList(list)
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): SuggestionViewHolder {
        val ui = SuggestionItemUi(context, theme)
        ui.root.apply {
            minimumWidth = dp(40)
            val size = theme.generalStyle.candidatePadding
            setPaddingDp(size, 0, size, 0)
            layoutParams = RecyclerView.LayoutParams(wrapContent, matchParent)
        }
        return SuggestionViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: SuggestionViewHolder,
        position: Int,
        item: SuggestionViewItem?,
    ) {
        item ?: return
        val isHighlighted = theme.generalStyle.candidateUseCursor && position == highlightedIdx
        holder.ui.update(item, isHighlighted)
    }
}
