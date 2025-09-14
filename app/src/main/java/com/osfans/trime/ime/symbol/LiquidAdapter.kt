/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard

class LiquidAdapter(
    private val theme: Theme,
    private val onItemClick: LiquidKeyboard.KeyItem.(Int) -> Unit,
) : BaseQuickAdapter<LiquidKeyboard.KeyItem, LiquidAdapter.ViewHolder>() {
    inner class ViewHolder(
        val ui: LiquidItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(LiquidItemUi(context, theme))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: LiquidKeyboard.KeyItem?,
    ) {
        item ?: return
        holder.ui.mainText.text = item.text
        holder.ui.root.setOnClickListener {
            onItemClick.invoke(item, position)
        }
    }
}
