/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.CandidateIdentity
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateRow
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.candidateStableId
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

open class CompactCandidateViewAdapter(
    val scope: ThemeScope,
) : BaseQuickAdapter<CandidateRow, CandidateViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.let { candidateStableId(it.index) } ?: -1L

    var total: Int = -1
        private set

    var highlightedIdx: Int = -1
        private set

    var snapshot = CandidateSnapshot()
        private set

    var layoutMinWidth: Int = 0
        private set

    var layoutFlexGrow: Float = 0f
        private set

    var layoutFlexShrink: Float = 1f

    var horizontalScroll: Boolean = false

    fun updateLayoutParams(minWidth: Int, flexGrow: Float) {
        layoutMinWidth = minWidth
        layoutFlexGrow = flexGrow
    }

    fun updateCandidates(
        data: Array<CandidateProto>,
        total: Int,
        highlightedIndex: Int,
        snapshot: CandidateSnapshot,
    ) {
        this.total = total
        this.highlightedIdx = highlightedIndex
        this.snapshot = snapshot
        super.submitList(data.mapIndexed { index, candidate -> CandidateRow(candidate, index, snapshot) }, null)
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder {
        val ui = CandidateItemUi(context, scope)
        ui.root.apply {
            minimumWidth = dp(48)
            layoutParams = if (horizontalScroll) RecyclerView.LayoutParams(wrapContent, matchParent) else FlexboxLayoutManager.LayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
        item: CandidateRow?,
    ) {
        item ?: return
        val isHighlighted = position == highlightedIdx
        holder.text = item.candidate.text
        holder.comment = item.candidate.comment
        holder.idx = item.index
        holder.snapshot = item.snapshot
        holder.ui.root.identity = CandidateIdentity(item.snapshot, item.index)
        holder.ui.update(item.candidate, isHighlighted, item.snapshot.isPrediction)
        val root = holder.ui.root
        if (horizontalScroll) {
            if (root.layoutParams is FlexboxLayoutManager.LayoutParams) root.layoutParams = RecyclerView.LayoutParams(root.layoutParams)
        } else {
            if (root.layoutParams !is FlexboxLayoutManager.LayoutParams) root.layoutParams = FlexboxLayoutManager.LayoutParams(root.layoutParams)
            root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                minWidth = this@CompactCandidateViewAdapter.layoutMinWidth
                flexGrow = this@CompactCandidateViewAdapter.layoutFlexGrow
                flexShrink = this@CompactCandidateViewAdapter.layoutFlexShrink
            }
        }
    }
}
