/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.CandidateItemUi
import com.osfans.trime.ime.candidates.CandidateViewHolder

open class PagingCandidateViewAdapter(
    val theme: Theme,
) : PagingDataAdapter<CandidateProto, CandidateViewHolder>(diffCallback) {
    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<CandidateProto>() {
                override fun areItemsTheSame(
                    oldItem: CandidateProto,
                    newItem: CandidateProto,
                ): Boolean = oldItem === newItem

                override fun areContentsTheSame(
                    oldItem: CandidateProto,
                    newItem: CandidateProto,
                ): Boolean = oldItem == newItem
            }
    }

    var offset: Int = 0
        private set

    var highlightedIndex: Int = -1
        private set

    fun refreshWith(offset: Int, highlightedIndex: Int) {
        this.offset = offset
        this.highlightedIndex = highlightedIndex
        refresh()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder = CandidateViewHolder(CandidateItemUi(parent.context, theme))

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
    ) {
        val item = getItem(position) ?: return
        val idx = position + offset
        val highlighted = idx == highlightedIndex
        holder.ui.update(item, highlighted)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = idx
    }
}
