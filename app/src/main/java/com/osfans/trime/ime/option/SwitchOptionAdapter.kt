/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.option

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.Theme

abstract class SwitchOptionAdapter : BaseQuickAdapter<SwitchOptionEntry, SwitchOptionAdapter.ViewHolder>() {
    inner class ViewHolder(
        val ui: SwitchOptionEntryUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(SwitchOptionEntryUi(context, theme))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: SwitchOptionEntry?,
    ) {
        item ?: return
        holder.ui.setEntry(item)
        holder.ui.root.setOnClickListener {
            onItemClick(it, item)
        }
    }

    abstract val theme: Theme

    abstract fun onItemClick(
        view: View,
        entry: SwitchOptionEntry,
    )
}
