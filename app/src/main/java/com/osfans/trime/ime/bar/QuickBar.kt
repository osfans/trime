// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ViewAnimator
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ui.AlwaysUi
import com.osfans.trime.ime.bar.ui.CandidateUi
import com.osfans.trime.ime.bar.ui.ClipboardSuggestionUi
import com.osfans.trime.ime.bar.ui.InlineSuggestionUi
import com.osfans.trime.ime.bar.ui.TabUi
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateModule
import com.osfans.trime.ime.candidates.unrolled.window.FlexboxUnrolledCandidateWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.switches.SwitchOptionWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.systemservices.clipboardManager
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
    lazyCommonKeyboardActionListener: Lazy<CommonKeyboardActionListener>,
) : InputBroadcastReceiver {

    @Keep
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip
        val content = clip?.getItemAt(0)?.text?.toString()?.takeIf { it.isNotBlank() }
        content?.let { handleClipboardContent(it) }
    }

    private var clipboardTimeoutJob: Job? = null

    private val candidate by lazyCandidate

    private val commonKeyboardActionListener by lazyCommonKeyboardActionListener

    private val prefs = AppPrefs.defaultInstance()

    private val hideQuickBar by prefs.keyboard.hideQuickBar

    private val clipboardSuggestion by prefs.clipboard.clipboardSuggestion

    private val clipboardSuggestionTimeout by prefs.clipboard.clipboardSuggestionTimeout

    val themedHeight =
        theme.generalStyle.candidateViewHeight + theme.generalStyle.commentHeight

    private fun evalAlwaysUiState() {
        val newState =
            when {
                else -> AlwaysUi.State.Toolbar
            }
        if (newState == alwaysUi.currentState) return
        alwaysUi.updateState(newState)
    }

    private val swipeDownHideKeyboardCallback: ((GestureFrame.SwipeDirection) -> Unit) = { d ->
        if (d == GestureFrame.SwipeDirection.Down) {
            service.requestHideSelf(0)
        }
    }

    private val alwaysUi: AlwaysUi by lazy {
        AlwaysUi(context, theme) { action ->
            action?.let { commonKeyboardActionListener.listener.onAction(KeyActionManager.getAction(it)) }
                ?: windowManager.attachWindow(SwitchOptionWindow(context, service, rime, theme))
        }.apply {
            hideKeyboardButton.apply {
                setOnClickListener { service.requestHideSelf(0) }
                onSwipeListener = swipeDownHideKeyboardCallback
            }
        }
    }

    private val candidateUi by lazy {
        CandidateUi(context, candidate.compactCandidateModule.view).apply {
            unrollButton.apply {
                onSwipeListener = swipeDownHideKeyboardCallback
            }
        }
    }

    private val inlineSuggestionUi by lazy {
        InlineSuggestionUi(context, candidate.suggestionCandidateModule.view)
    }

    private val tabUi by lazy {
        TabUi(context, theme)
    }

    private val clipboardSuggestionUi by lazy {
        ClipboardSuggestionUi(context).apply {
            root.setOnClickListener {
                val content = text.text.toString()
                if (content.isNotEmpty()) {
                    service.commitText(content)
                    handleClipboardContent(null)
                }
            }
        }
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

    override fun onCandidateListUpdate(data: RimeMessage.CandidateListMessage.Data) {
        barStateMachine.push(
            QuickBarStateMachine.TransitionEvent.CandidatesUpdated,
            QuickBarStateMachine.BooleanKey.CandidateEmpty to data.candidates.isEmpty(),
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
                ColorManager.getDecorDrawable(
                    "candidate_background",
                    "candidate_border_color",
                    dp(theme.generalStyle.candidateBorder),
                    dp(theme.generalStyle.candidateBorderRound),
                )
            add(alwaysUi.root, lParams(matchParent, matchParent))
            add(candidateUi.root, lParams(matchParent, matchParent))
            add(tabUi.root, lParams(matchParent, matchParent))
            add(inlineSuggestionUi.root, lParams(matchParent, matchParent))
            add(clipboardSuggestionUi.root, lParams(matchParent, matchParent))

            evalAlwaysUiState()

            clipboardManager.addPrimaryClipChangedListener(clipboardListener)
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

    fun handleClipboardContent(content: String?) {
        val isEmpty = content.isNullOrEmpty() || !clipboardSuggestion

        if (!isEmpty) {
            clipboardSuggestionUi.text.text = content
            clipboardTimeoutJob?.cancel()
            clipboardTimeoutJob = service.lifecycleScope.launch {
                delay(clipboardSuggestionTimeout * 1000L)
                handleClipboardContent(null)
            }
        }

        barStateMachine.push(
            QuickBarStateMachine.TransitionEvent.ClipboardUpdated,
            QuickBarStateMachine.BooleanKey.ClipboardEmpty to isEmpty,
        )
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        alwaysUi.updateButtonsStyle()
    }
}
