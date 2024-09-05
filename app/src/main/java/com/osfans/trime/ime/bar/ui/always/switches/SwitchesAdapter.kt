// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui.always.switches

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.schema.Schema
import com.osfans.trime.data.theme.Theme

class SwitchesAdapter(
    private val theme: Theme,
) : BaseQuickAdapter<Schema.Switch, SwitchesAdapter.Holder>() {
    private val showArrow = AppPrefs.defaultInstance().keyboard.switchArrowEnabled

    inner class Holder(
        val ui: SwitchUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): Holder = Holder(SwitchUi(context, theme))

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
        item: Schema.Switch?,
    ) {
        holder.ui.apply {
            val enabled = item!!.enabled
            setLabel(item.states!![enabled])
            val altText =
                if (item.options.isNullOrEmpty()) {
                    item.states[1 - enabled].let {
                        if (showArrow) {
                            "→ $it"
                        } else {
                            it
                        }
                    }
                } else {
                    ""
                }
            setAltLabel(altText)
        }
    }
}
