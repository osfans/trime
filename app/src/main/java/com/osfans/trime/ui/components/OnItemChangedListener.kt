/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package com.osfans.trime.ui.components

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

            override fun onItemRemovedBatch(items: List<T>) {
                l1.onItemRemovedBatch(items)
                l2.onItemRemovedBatch(items)
            }
        }
    }
}
