// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

class SuggestionViewAdapter(
    private val theme: Theme,
) : BaseQuickAdapter<SuggestionViewItem, SuggestionViewAdapter.ViewHolder>() {
    inner class ViewHolder(
        val ui: SuggestionItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val ui = SuggestionItemUi(context)
        ui.root.apply {
            minimumWidth = dp(40)
            val size = theme.generalStyle.candidatePadding
            setPaddingDp(size, 0, size, 0)
            layoutParams = RecyclerView.LayoutParams(wrapContent, matchParent)
        }
        return ViewHolder(ui)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: SuggestionViewItem?,
    ) {
        item?.view ?: return
        holder.ui.addView(item.view)
    }
}
