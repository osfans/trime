/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.option

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.schema.Schema
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ui.ToolButton
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.gridLayoutManager

class SwitchOptionWindow(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
) : BoardWindow.BarBoardWindow(),
    InputBroadcastReceiver {
    var popupMenu: PopupMenu? = null

    private val adapter: SwitchOptionAdapter by lazy {
        object : SwitchOptionAdapter() {
            override val theme: Theme = this@SwitchOptionWindow.theme

            override fun onItemClick(
                view: View,
                entry: SwitchOptionEntry,
            ) {
                when (entry) {
                    is SwitchOptionEntry.Custom -> {
                        val options = entry.switch.options
                        val oldEnabled = entry.switch.enabledIndex
                        if (options.isEmpty()) {
                            val newEnabled = 1 - oldEnabled
                            entry.switch.enabledIndex = newEnabled
                            rime.launchOnReady { it.setRuntimeOption(entry.switch.name, newEnabled == 1) }
                        } else {
                            val popup = PopupMenu(context, view)
                            val menu = popup.menu
                            entry.switch.states.forEachIndexed { i, state ->
                                menu.add(0, 0, 0, state).apply {
                                    setOnMenuItemClickListener {
                                        rime.launchOnReady {
                                            it.setRuntimeOption(options[oldEnabled], false)
                                            it.setRuntimeOption(options[i], true)
                                        }
                                        true
                                    }
                                }
                            }
                            popupMenu?.dismiss()
                            popupMenu = popup
                            popup.show()
                        }
                    }
                }
            }
        }
    }

    val view by lazy {
        context.recyclerView {
            layoutManager = gridLayoutManager(4)
            adapter = this@SwitchOptionWindow.adapter
        }
    }

    private fun updateSchemaOptionEntries(switches: List<Schema.Switch>) {
        adapter.submitList(switches.map { SwitchOptionEntry.fromSwitch(it) })
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        val data =
            SchemaManager.activeSchema.switches
                .filter { it.states.size > 1 }
        updateSchemaOptionEntries(data)
    }

    override fun onCreateView() = view

    private val settingsButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_settings_24).apply {
            setOnClickListener { AppUtils.launchMainActivity(context) }
        }
    }

    private val barExternalView by lazy {
        context.horizontalLayout {
            val size = dp(theme.generalStyle.run { candidateViewHeight + commentHeight })
            add(settingsButton, lParams(size, size))
        }
    }

    override fun onCreateBarView() = barExternalView

    override fun onAttached() {
        val data =
            SchemaManager.activeSchema.switches
                .filter { it.states.size > 1 }
        service.lifecycleScope.launch {
            updateSchemaOptionEntries(data)
        }
    }

    override fun onDetached() {
        popupMenu?.dismiss()
        popupMenu = null
    }
}
