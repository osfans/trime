/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.view.View
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.ui.components.OnItemChangedListener
import com.osfans.trime.ui.main.settings.ProgressFragment

class SchemaListFragment :
    ProgressFragment(),
    OnItemChangedListener<SchemaItem> {
    private fun updateSchemaState() {
        if (isInitialized) {
            rime.launchOnReady { r ->
                r.setEnabledSchemata(
                    ui.adapter.items
                        .map { it.id }
                        .toTypedArray(),
                )
            }
        }
    }

    private lateinit var ui: SchemaListUi

    override suspend fun initialize(): View {
        val available = rime.runOnReady { availableSchemata().toSet() }
        val enabled = rime.runOnReady { enabledSchemata().map { it.id } }
        val entries = available.filter { enabled.contains(it.id) }
        ui =
            SchemaListUi(
                requireContext(),
                initialEntries = entries,
                contentSource = { (available - adapter.items.toSet()).toTypedArray() },
            )
        ui.adapter.setViewModel(viewModel)
        ui.adapter.addOnItemChangedListener(this)
        viewModel.enableToolbarEditButton(enabled.isNotEmpty()) {
            ui.adapter.enterMultiSelect(requireActivity().onBackPressedDispatcher)
        }
        return ui.root
    }

    override fun onStart() {
        super.onStart()
        if (::ui.isInitialized) {
            viewModel.enableToolbarEditButton(ui.adapter.items.isNotEmpty()) {
                ui.adapter.enterMultiSelect(requireActivity().onBackPressedDispatcher)
            }
        }
    }

    override fun onStop() {
        if (::ui.isInitialized) {
            ui.adapter.exitMultiSelect()
        }
        viewModel.disableToolbarEditButton()
        super.onStop()
    }

    override fun onDestroy() {
        if (::ui.isInitialized) {
            ui.adapter.removeItemChangedListener()
        }
        super.onDestroy()
    }

    override fun onItemAdded(
        idx: Int,
        item: SchemaItem,
    ) {
        updateSchemaState()
    }

    override fun onItemRemoved(
        idx: Int,
        item: SchemaItem,
    ) {
        updateSchemaState()
    }

    override fun onItemRemovedBatch(items: List<SchemaItem>) {
        updateSchemaState()
    }
}
