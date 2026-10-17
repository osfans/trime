/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates.compact

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager

/** A single scrolling row retains its item/offset anchor through ordinary layout passes. */
object CompactCandidateLayoutManagers {
    fun create(
        context: Context,
        horizontal: Boolean,
        onCompleted: (RecyclerView.LayoutManager) -> Unit = {},
    ): RecyclerView.LayoutManager = if (horizontal) {
        object : LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                onCompleted(this)
            }
        }
    } else {
        object : FlexboxLayoutManager(context) {
            override fun canScrollHorizontally(): Boolean = false

            override fun canScrollVertically(): Boolean = false

            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                onCompleted(this)
            }
        }.apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
    }

    fun firstVisibleIndex(manager: RecyclerView.LayoutManager): Int = when (manager) {
        is LinearLayoutManager -> manager.findFirstVisibleItemPosition()
        is FlexboxLayoutManager -> manager.findFirstVisibleItemPosition()
        else -> RecyclerView.NO_POSITION
    }
}
