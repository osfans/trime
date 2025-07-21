// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar

import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ViewAnimator
import androidx.annotation.RequiresApi
import com.osfans.trime.R
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ui.AlwaysUi
import com.osfans.trime.ime.bar.ui.CandidateUi
import com.osfans.trime.ime.bar.ui.SuggestionUi
import com.osfans.trime.ime.bar.ui.TabUi
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateModule
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.candidates.unrolled.window.FlexboxUnrolledCandidateWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.option.SwitchOptionWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

@InputScope
@Inject
class QuickBar(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
    private val windowManager: BoardWindowManager,
    lazyCandidate: Lazy<CandidateModule>,
) : InputBroadcastReceiver {
    private val candidate by lazyCandidate

    private val prefs = AppPrefs.defaultInstance()

    private val hideQuickBar by prefs.keyboard.hideQuickBar

    val themedHeight =
        theme.generalStyle.candidateViewHeight + theme.generalStyle.commentHeight

    private fun evalAlwaysUiState() {
        val newState =
            when {
                else -> AlwaysUi.State.Empty
            }
        if (newState == alwaysUi.currentState) return
        alwaysUi.updateState(newState)
    }

    private val alwaysUi: AlwaysUi by lazy {
        AlwaysUi(context, theme).apply {
            moreButton.apply {
                setOnClickListener {
                    windowManager.attachWindow(SwitchOptionWindow(context, service, rime, theme))
                }
            }
            hideKeyboardButton.apply {
                setOnClickListener { service.requestHideSelf(0) }
            }
        }
    }

    private val candidateUi by lazy {
        CandidateUi(context, candidate.compactCandidateModule.view)
    }

    private val suggestionUi by lazy {
        SuggestionUi(context, candidate.suggestionCandidateModule.view).apply {
            homeButton.setOnClickListener {
                barStateMachine.push(
                    QuickBarStateMachine.TransitionEvent.SuggestionUpdated,
                    QuickBarStateMachine.BooleanKey.SuggestionEmpty to true,
                )
            }
        }
    }

    private val tabUi by lazy {
        TabUi(context, theme)
    }

    private val barStateMachine =
        QuickBarStateMachine.new {
            switchUiByState(it)
        }

    val unrollButtonStateMachine =
        UnrollButtonStateMachine.new {
            when (it) {
                UnrollButtonStateMachine.State.ClickToAttachWindow -> {
                    setUnrollButtonToAttach()
                    setUnrollButtonEnabled(true)
                }
                UnrollButtonStateMachine.State.ClickToDetachWindow -> {
                    setUnrollButtonToDetach()
                    setUnrollButtonEnabled(true)
                }
                UnrollButtonStateMachine.State.Hidden -> {
                    setUnrollButtonEnabled(false)
                }
            }
        }

    private fun setUnrollButtonToAttach() {
        candidateUi.unrollButton.setOnClickListener { view ->
            InputFeedbackManager.keyPressVibrate(view)
            windowManager.attachWindow(
                FlexboxUnrolledCandidateWindow(context, service, rime, theme, this, windowManager, candidate.compactCandidateModule),
            )
        }
        candidateUi.unrollButton.setIcon(R.drawable.ic_baseline_expand_more_24)
    }

    private fun setUnrollButtonToDetach() {
        candidateUi.unrollButton.setOnClickListener { view ->
            InputFeedbackManager.keyPressVibrate(view)
            windowManager.attachWindow(KeyboardWindow)
        }
        candidateUi.unrollButton.setIcon(R.drawable.ic_baseline_expand_less_24)
    }

    private fun setUnrollButtonEnabled(enabled: Boolean) {
        candidateUi.unrollButton.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        // TODO: 临时修复状态栏与悬浮窗同时显示，后续需优化：考虑分离数据或寻找更好的实现方式
        if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) return

        barStateMachine.push(
            QuickBarStateMachine.TransitionEvent.CandidatesUpdated,
            QuickBarStateMachine.BooleanKey.CandidateEmpty to ctx.menu.candidates.isEmpty(),
        )
    }

    private fun switchUiByState(state: QuickBarStateMachine.State) {
        val index = state.ordinal
        if (view.displayedChild == index) return
        val new = view.getChildAt(index)
        if (new != tabUi.root) {
            tabUi.setBackButtonOnClickListener { }
            tabUi.removeExternal()
        }
        view.displayedChild = index
    }

    val view by lazy {
        ViewAnimator(context).apply {
            visibility =
                if (hideQuickBar) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            background =
                ColorManager.getDrawable(
                    "candidate_background",
                    "candidate_border_color",
                    dp(theme.generalStyle.candidateBorder),
                    dp(theme.generalStyle.candidateBorderRound),
                )
            add(alwaysUi.root, lParams(matchParent, matchParent))
            add(candidateUi.root, lParams(matchParent, matchParent))
            add(tabUi.root, lParams(matchParent, matchParent))
            add(suggestionUi.root, lParams(matchParent, matchParent))

            evalAlwaysUiState()
        }
    }

    override fun onStartInput(info: EditorInfo) {
        evalAlwaysUiState()
    }

    override fun onWindowAttached(window: BoardWindow) {
        if (window is BoardWindow.BarBoardWindow) {
            window.onCreateBarView()?.let { tabUi.addExternal(it, window.showTitle) }
            tabUi.setBackButtonOnClickListener {
                windowManager.attachWindow(KeyboardWindow)
            }
            barStateMachine.push(QuickBarStateMachine.TransitionEvent.BarBoardWindowAttached)
        }
    }

    override fun onWindowDetached(window: BoardWindow) {
        barStateMachine.push(QuickBarStateMachine.TransitionEvent.WindowDetached)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(isEmpty: Boolean) {
        barStateMachine.push(
            QuickBarStateMachine.TransitionEvent.SuggestionUpdated,
            QuickBarStateMachine.BooleanKey.SuggestionEmpty to isEmpty,
        )
    }
}
