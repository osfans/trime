/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.CallSuper
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.ui.components.OnItemChangedListener
import com.osfans.trime.ui.main.MainViewModel

open class SchemaListAdapter(
    override var items: List<SchemaItem>,
) : BaseQuickAdapter<SchemaItem, SchemaListAdapter.ViewHolder>() {
    var multiselect = false
        private set

    private val selected = mutableListOf<SchemaItem>()

    private var listener: OnItemChangedListener<SchemaItem>? = null

    fun removeItemChangedListener() {
        listener = null
    }

    fun addOnItemChangedListener(x: OnItemChangedListener<SchemaItem>) {
        listener = listener?.let { OnItemChangedListener.merge(it, x) } ?: x
    }

    private var onBackPressedCallback: OnBackPressedCallback? = null

    private var mainViewModel: MainViewModel? = null

    fun setViewModel(model: MainViewModel) {
        mainViewModel = model
    }

    inner class ViewHolder(
        ui: SchemaListEntryUi,
    ) : RecyclerView.ViewHolder(ui.root) {
        val checkBox = ui.checkBox
        val nameText = ui.nameText
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(SchemaListEntryUi(context))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: SchemaItem?,
    ) {
        item ?: return
        with(holder) {
            nameText.text = item.name
            checkBox.isVisible = multiselect
            checkBox.isChecked = selected.map(SchemaItem::id).contains(item.id)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                select(item, isChecked)
            }
            nameText.setOnClickListener {
                checkBox.toggle()
            }
        }
    }

    private fun select(
        item: SchemaItem,
        shouldSelect: Boolean,
    ) {
        if (shouldSelect) {
            if (selected.indexOf(item) == -1) selected.add(item)
        } else {
            selected.remove(item)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    open fun enterMultiSelect(onBackPressedDispatcher: OnBackPressedDispatcher) {
        mainViewModel?.let {
            if (multiselect) {
                return
            }
            onBackPressedCallback =
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        exitMultiSelect()
                    }
                }
            onBackPressedDispatcher.addCallback(onBackPressedCallback!!)
            it.enableToolbarDeleteButton {
                deleteSelected()
                exitMultiSelect()
            }
            it.hideToolbarEditButton()
            multiselect = true
            notifyDataSetChanged()
        }
    }

    private fun deleteSelected() {
        if (!multiselect || selected.isEmpty()) {
            return
        }
        selected.forEach { remove(it) }
        listener?.onItemRemovedBatch(selected)
    }

    @SuppressLint("NotifyDataSetChanged")
    open fun exitMultiSelect() {
        mainViewModel?.let {
            if (!multiselect) {
                return
            }
            onBackPressedCallback?.remove()
            it.disableToolbarDeleteButton()
            multiselect = false
            selected.clear()
            notifyDataSetChanged()
            if (items.isNotEmpty()) {
                it.showToolbarEditButton()
            }
        }
    }

    private fun addInternal(
        idx: Int,
        item: SchemaItem,
    ) {
        listener?.onItemAdded(idx, item)
        mainViewModel?.showToolbarEditButton()
    }

    @CallSuper
    override fun add(data: SchemaItem) {
        super.add(data)
        addInternal(items.size, data)
    }

    override fun add(
        position: Int,
        data: SchemaItem,
    ) {
        super.add(position, data)
        addInternal(position, data)
    }

    /** This also valid for [BaseQuickAdapter.remove] */
    @CallSuper
    override fun removeAt(position: Int) {
        listener?.onItemRemoved(position, items[position])
        super.removeAt(position)
        if (items.isEmpty()) {
            mainViewModel?.hideToolbarEditButton()
        }
    }
}
