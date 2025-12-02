/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.view.View
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.ui.common.OnItemChangedListener
import com.osfans.trime.ui.main.settings.ProgressFragment
import com.osfans.trime.util.NaiveDustman
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

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

    private val dustman = NaiveDustman<SchemaItem>()

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
        resetDustman()
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
        persistSchemaList()
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
        dustman.addOrUpdate(item.toString(), item)
    }

    override fun onItemRemoved(
        idx: Int,
        item: SchemaItem,
    ) {
        dustman.remove(item.toString())
    }

    override fun onItemAddedBatch(items: List<SchemaItem>) {
        items.forEach { dustman.addOrUpdate(it.toString(), it) }
    }

    override fun onItemRemovedBatch(items: List<SchemaItem>) {
        items.forEach { dustman.remove(it.toString()) }
    }

    private fun persistSchemaList() {
        if (!dustman.dirty) return
        resetDustman()
        updateSchemaState()
        lifecycleScope.launch(NonCancellable + Dispatchers.Default) {
            rime.runOnReady { deploy() }
        }
    }

    private fun resetDustman() {
        dustman.reset(ui.adapter.items.associateBy { it.toString() })
    }
}
