// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ViewAnimator
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeNotification.OptionNotification
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.CandidateBarBinding
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.ime.window.BoardWindow
import me.tatarka.inject.annotations.Inject
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

@InputScope
@Inject
class QuickBar(context: Context, service: TrimeInputMethodService, theme: Theme) : InputBroadcastReceiver {
    val oldCandidateBar by lazy {
        CandidateBarBinding.inflate(LayoutInflater.from(context)).apply {
            with(root) {
                setPageStr(
                    { service.handleKey(KeyEvent.KEYCODE_PAGE_DOWN, 0) },
                    { service.handleKey(KeyEvent.KEYCODE_PAGE_UP, 0) },
                    { service.selectLiquidKeyboard(SymbolBoardType.CANDIDATE) },
                )
            }
            with(candidates) {
                setCandidateListener(service.textInputManager)
                shouldShowComment = !Rime.getOption("_hide_comment")
            }
        }
    }

    private val tabUi by lazy {
        TabUi(context)
    }

    enum class State {
        Candidate,
        Tab,
    }

    private fun switchUiByState(state: State) {
        val index = state.ordinal
        if (view.displayedChild == index) return
        val new = view.getChildAt(index)
        if (new != tabUi.root) {
            tabUi.removeExternal()
        }
        view.displayedChild = index
    }

    val view by lazy {
        ViewAnimator(context).apply {
            visibility =
                if (Rime.getOption("_hide_candidate") ||
                    Rime.getOption("_hide_bar")
                ) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            background =
                ColorManager.getDrawable(
                    context,
                    "candidate_background",
                    theme.generalStyle.candidateBorder,
                    "candidate_border_color",
                    theme.generalStyle.candidateBorderRound,
                )
            add(oldCandidateBar.root, lParams(matchParent, matchParent))
            add(tabUi.root, lParams(matchParent, matchParent))
        }
    }

    override fun onRimeOptionUpdated(value: OptionNotification.Value) {
        when (value.option) {
            "_hide_comment" -> {
                oldCandidateBar.candidates.shouldShowComment = !value.value
            }
            "_hide_candidate", "_hide_bar" -> {
                view.visibility = if (value.value) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onWindowAttached(window: BoardWindow) {
        if (window is BoardWindow.BarBoardWindow) {
            window.onCreateBarView()?.let { tabUi.addExternal(it) }
            switchUiByState(State.Tab)
        }
    }

    override fun onWindowDetached(window: BoardWindow) {
        switchUiByState(State.Candidate)
    }
}
