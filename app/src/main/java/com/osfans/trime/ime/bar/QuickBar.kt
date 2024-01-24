package com.osfans.trime.ime.bar

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ViewAnimator
import com.osfans.trime.core.Rime
import com.osfans.trime.databinding.CandidateBarBinding
import com.osfans.trime.databinding.TabBarBinding
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.enums.SymbolKeyboardType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class QuickBar : KoinComponent {
    private val context: Context by inject()
    private val service: Trime by inject()

    val oldCandidateBar by lazy {
        CandidateBarBinding.inflate(LayoutInflater.from(context)).apply {
            with(root) {
                setPageStr(
                    { service.handleKey(KeyEvent.KEYCODE_PAGE_DOWN, 0) },
                    { service.handleKey(KeyEvent.KEYCODE_PAGE_UP, 0) },
                    { service.selectLiquidKeyboard(SymbolKeyboardType.CANDIDATE) },
                )
                visibility = if (Rime.getOption("_hide_candidate")) View.GONE else View.VISIBLE
            }
            with(candidates) {
                setCandidateListener(service.textInputManager)
                setShowComment(!Rime.getOption("_hide_comment"))
                reset()
            }
        }
    }

    val oldTabBar by lazy {
        TabBarBinding.inflate(LayoutInflater.from(context))
    }

    enum class State {
        Candidate,
        Tab,
    }

    fun switchUiByState(state: State) {
        if (view.displayedChild == state.ordinal) return
        view.displayedChild = state.ordinal
    }

    val view by lazy {
        ViewAnimator(context).apply {
            background = oldCandidateBar.root.background
            add(oldCandidateBar.root, lParams(matchParent, matchParent))
            add(oldTabBar.root, lParams(matchParent, matchParent))
        }
    }
}
