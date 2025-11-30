/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ui.common

/**
 * Functions are called after the container changed
 */
interface OnItemChangedListener<T> {
    fun onItemAdded(
        idx: Int,
        item: T,
    ) {}

    fun onItemRemoved(
        idx: Int,
        item: T,
    ) {}

    fun onItemAddedBatch(items: List<T>) {}

    fun onItemRemovedBatch(items: List<T>) {}

    companion object {
        /**
         * Merge two listeners
         */
        fun <T> merge(
            l1: OnItemChangedListener<T>,
            l2: OnItemChangedListener<T>,
        ) = object : OnItemChangedListener<T> {
            override fun onItemAdded(
                idx: Int,
                item: T,
            ) {
                l1.onItemAdded(idx, item)
                l2.onItemAdded(idx, item)
            }

            override fun onItemRemoved(
                idx: Int,
                item: T,
            ) {
                l1.onItemRemoved(idx, item)
                l2.onItemRemoved(idx, item)
            }

            override fun onItemAddedBatch(items: List<T>) {
                l1.onItemAddedBatch(items)
                l2.onItemAddedBatch(items)
            }

            override fun onItemRemovedBatch(items: List<T>) {
                l1.onItemRemovedBatch(items)
                l2.onItemRemovedBatch(items)
            }
        }
    }
}
