/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.horizontalLayoutManager

class SuggestionCandidateModule(
    val context: Context,
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
    val bar: QuickBar,
) : InputBroadcastReceiver {
    val adapter by lazy {
        SuggestionCandidateViewAdapter(theme)
    }

    val view by lazy {
        context.recyclerView(R.id.suggestion_view) {
            adapter = this@SuggestionCandidateModule.adapter
            layoutManager = horizontalLayoutManager()
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
    }

    override fun onInlineSuggestion(views: List<View>) {
        adapter.updateCandidates(
            views.map {
                SuggestionViewItem(it)
            },
            true,
            0,
            -1,
        )
    }
}
