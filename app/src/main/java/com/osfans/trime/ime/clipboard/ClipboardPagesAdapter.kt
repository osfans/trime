/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.collection.LongSparseArray
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.RecyclerView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

abstract class ClipboardPagesAdapter : RecyclerView.Adapter<ClipboardPagesAdapter.ViewHolder>() {
    class ViewHolder(container: FrameLayout) : RecyclerView.ViewHolder(container)

    val pages = LongSparseArray<ClipboardPageUi>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val container = FrameLayout(parent.context).apply {
            id = View.generateViewId()
            layoutParams = lParams(matchParent, matchParent)
            isSaveEnabled = false
        }
        return ViewHolder(container)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        ensurePage(position)
        val container = holder.itemView as FrameLayout
        if (container.isAttachedToWindow) {
            placePageInViewHolder(holder)
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        placePageInViewHolder(holder)
    }

    private fun ensurePage(position: Int) {
        val itemId = getItemId(position)
        if (!pages.containsKey(itemId)) {
            val newPage = onCreatePage(position)
            pages.put(itemId, newPage)
        }
    }

    private fun placePageInViewHolder(holder: ViewHolder) {
        val page = pages[holder.itemId]
            ?: throw IllegalStateException("No such page view")
        val container = holder.itemView as FrameLayout
        if (container.isNotEmpty()) {
            container.removeAllViews()
        }
        container.addView(page.root)
    }

    abstract fun onCreatePage(position: Int): ClipboardPageUi
}
