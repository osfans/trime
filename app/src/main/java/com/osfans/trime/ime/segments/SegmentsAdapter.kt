/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.ThemeScope

class SegmentsAdapter(
    private val scope: ThemeScope,
    private val onItemClick: () -> Unit,
    private val rawText: String = "",
) : BaseQuickAdapter<String, SegmentsAdapter.ViewHolder>() {

    class ViewHolder(val ui: SegmentUi) : RecyclerView.ViewHolder(ui.root)

    private val selection = mutableSetOf<Int>()

    private val separators: List<String> by lazy {
        if (rawText.isEmpty() || items.isEmpty()) emptyList() else computeSeparators()
    }

    private fun computeSeparators(): List<String> {
        val result = mutableListOf<String>()
        var idx = 0
        for (item in items) {
            val sb = StringBuilder()
            var pos = idx + item.length
            while (pos < rawText.length) {
                val c = rawText[pos]
                if (c == ' ' || c == '\n' || c == '\t' || c == '\r') {
                    sb.append(c)
                    pos++
                } else {
                    break
                }
            }
            result.add(sb.toString())
            idx = pos
        }
        return result
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(SegmentUi(context, scope))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: String?,
    ) {
        val text = item ?: return
        holder.ui.refreshColors()
        holder.ui.textView.text = text
        val isSelected = selection.contains(position)
        holder.ui.update(isSelected)
        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                val newState = !isSegmentSelected(currentPos)
                setSegmentSelected(currentPos, newState)
                onItemClick.invoke()
            }
        }
    }

    val selectionSnapshot: Set<Int>
        get() = selection.toSet()

    fun isSegmentSelected(position: Int): Boolean = selection.contains(position)

    fun setSegmentSelected(position: Int, isSelected: Boolean) {
        if (isSelected) {
            selection.add(position)
        } else {
            selection.remove(position)
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll() {
        if (isAllSelected) return
        selection.clear()
        selection.addAll(items.indices)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun deselect() {
        if (selection.isEmpty()) return
        selection.clear()
        notifyDataSetChanged()
    }

    val isAllSelected: Boolean
        get() = selection.size == items.size

    val joinedSegments: String
        get() = buildString {
            items.forEachIndexed { i, item ->
                if (isSegmentSelected(i)) {
                    append(item)
                    if (isSegmentSelected(i + 1)) {
                        append(separators.getOrElse(i) { "" })
                    }
                }
            }
        }
}
