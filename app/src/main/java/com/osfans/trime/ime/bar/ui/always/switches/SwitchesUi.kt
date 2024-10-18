// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui.always.switches

import android.content.Context
import android.view.ViewGroup
import com.chad.library.adapter4.util.setOnDebouncedItemClick
import com.osfans.trime.data.schema.Schema
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.symbol.SpacesItemDecoration
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.horizontalLayoutManager

class SwitchesUi(
    override val ctx: Context,
    val theme: Theme,
) : Ui {
    private val switchesAdapter by lazy {
        SwitchesAdapter(theme)
    }

    override val root =
        recyclerView {
            layoutParams = ViewGroup.LayoutParams(matchParent, matchParent)
            layoutManager = horizontalLayoutManager()
            adapter = switchesAdapter
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            addItemDecoration(SpacesItemDecoration(dp(theme.generalStyle.candidateSpacing).toInt()))
        }

    fun setSwitches(list: List<Schema.Switch>) {
        switchesAdapter.submitList(list)
    }

    fun setOnSwitchClick(listener: (Schema.Switch) -> Unit) {
        switchesAdapter.setOnDebouncedItemClick { adapter, _, position ->
            val typed = (adapter as SwitchesAdapter)
            if (typed.items.isEmpty()) return@setOnDebouncedItemClick
            val switch = typed.items[position]
            listener(switch)
        }
    }
}
