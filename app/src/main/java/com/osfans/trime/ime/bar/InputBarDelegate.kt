/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar

import android.os.Build
import android.util.Size
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ViewAnimator
import android.widget.inline.InlineContentView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.ui.AlwaysUi
import com.osfans.trime.ime.bar.ui.CandidateUi
import com.osfans.trime.ime.bar.ui.TabUi
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.unrolled.window.FlexboxUnrolledCandidateWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.switches.SwitchOptionWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class InputBarDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    private val context: ContextThemeWrapper by instance()
    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()
    private val theme: Theme get() = scope.theme
    private val windowManager: BoardWindowManager by instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by instance()
    private val candidate: CompactCandidateDelegate by instance()
    private val rime: RimeSession by instance()

    val themedHeight = theme.generalStyle.run { candidateViewHeight + commentHeight }

    private val prefs = AppPrefs.defaultInstance()

    private val hideQuickBar by prefs.keyboard.hideInputBar

    private val clipboardSuggestion by prefs.clipboard.clipboardSuggestion

    private val clipboardSuggestionTimeout by prefs.clipboard.clipboardSuggestionTimeout

    private var clipboardTimeoutJob: Job? = null

    private var isClipboardFresh: Boolean = false
    private var isInlineSuggestionPresent: Boolean = false

    @Keep
    private val onClipboardUpdateListener = ClipboardHelper.OnClipboardUpdateListener {
        if (!clipboardSuggestion) return@OnClipboardUpdateListener
        service.lifecycleScope.launch {
            if (it.text.isNullOrEmpty()) {
                isClipboardFresh = false
            } else {
                alwaysUi.clipboardUi.text.text = it.text.take(42)
                isClipboardFresh = true
                launchClipboardTimeoutJob()
            }
            evalAlwaysUiState()
        }
    }

    private fun launchClipboardTimeoutJob() {
        clipboardTimeoutJob?.cancel()
        val timeout = clipboardSuggestionTimeout * 1000L
        if (timeout < 0L) return
        clipboardTimeoutJob = service.lifecycleScope.launch {
            delay(timeout)
            isClipboardFresh = false
            clipboardTimeoutJob = null
            evalAlwaysUiState()
        }
    }

    private fun evalAlwaysUiState() {
        val newState =
            when {
                isClipboardFresh -> AlwaysUi.State.Clipboard
                isInlineSuggestionPresent -> AlwaysUi.State.InlineSuggestion
                else -> AlwaysUi.State.Toolbar
            }
        if (newState == alwaysUi.currentState) return
        alwaysUi.updateState(newState)
    }

    private val swipeDownHideKeyboardCallback: ((KeyBehavior) -> Unit) = { d ->
        if (d == KeyBehavior.SWIPE_DOWN) {
            service.requestHideSelf(0)
        }
    }

    private val alwaysUi: AlwaysUi by lazy {
        AlwaysUi(context, scope) { action ->
            if (action.isNotEmpty()) {
                commonKeyboardActionListener.listener.onAction(KeyActionManager.getAction(action))
            } else {
                windowManager.attachWindow(SwitchOptionWindow(di))
            }
        }.apply {
            hideKeyboardButton.apply {
                setOnClickListener { service.requestHideSelf(0) }
                onSwipe = swipeDownHideKeyboardCallback
            }
            clipboardUi.suggestionView.apply {
                setOnClickListener {
                    val content = ClipboardHelper.lastBean?.text
                    content?.let { service.commitText(it) }
                    dismissClipboardSuggestion()
                }
                setOnLongClickListener {
                    ClipboardHelper.lastBean?.let {
                        AppUtils.launchClipEdit(context, it.id, ClipEditActivity.FROM_CLIPBOARD)
                    }
                    true
                }
            }
            clipboardUi.dismiss.setOnClickListener {
                dismissClipboardSuggestion()
            }
        }
    }

    private fun dismissClipboardSuggestion() {
        clipboardTimeoutJob?.cancel()
        clipboardTimeoutJob = null
        isClipboardFresh = false
        evalAlwaysUiState()
    }

    private val candidateUi by lazy {
        CandidateUi(context, scope, candidate.view).apply {
            unrollButton.apply {
                onSwipe = swipeDownHideKeyboardCallback
            }
        }
    }

    private val tabUi by lazy {
        TabUi(context, scope)
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
        candidateUi.unrollButton.setOnClickListener {
            windowManager.attachWindow(FlexboxUnrolledCandidateWindow(di))
        }
        candidateUi.unrollButton.setIcon(R.drawable.ic_baseline_expand_more_24)
    }

    private fun setUnrollButtonToDetach() {
        candidateUi.unrollButton.setOnClickListener {
            windowManager.attachWindow(KeyboardWindow)
        }
        candidateUi.unrollButton.setIcon(R.drawable.ic_baseline_expand_less_24)
    }

    private fun setUnrollButtonEnabled(enabled: Boolean) {
        candidateUi.unrollButton.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
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
            tabUi.setTitle("")
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
                scope.decorDrawable(
                    "candidate_background",
                    "candidate_border_color",
                    dp(theme.generalStyle.candidateBorder),
                    dp(theme.generalStyle.candidateBorderRound),
                )
            add(alwaysUi.root, lParams(matchParent, matchParent))
            add(candidateUi.root, lParams(matchParent, matchParent))
            add(tabUi.root, lParams(matchParent, matchParent))

            evalAlwaysUiState()
            ClipboardHelper.addOnUpdateListener(onClipboardUpdateListener)
            syncToolbarOptionStates()
        }
    }

    /** Restyles the bar after a scheme switch without rebuilding its views. */
    fun refreshColors() {
        view.background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                context.dp(theme.generalStyle.candidateBorder),
                context.dp(theme.generalStyle.candidateBorderRound),
            )
        alwaysUi.refreshColors()
        candidateUi.refreshColors()
        // the compact candidate rows live inside candidateUi and re-bind via the delegate
        candidate.refreshColors()
        tabUi.refreshColors()
    }

    override fun onStartInput(info: EditorInfo) {
        evalAlwaysUiState()
    }

    override fun onWindowAttached(window: BoardWindow) {
        if (window is BoardWindow.BarBoardWindow) {
            tabUi.setTitle(window.title)
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

    private val suggestionSize by lazy {
        Size(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(themedHeight))
    }

    private val directExecutor by lazy {
        Executor { it.run() }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            isInlineSuggestionPresent = false
            return true
        }
        var pinned: InlineSuggestion? = null
        val scrollable = mutableListOf<InlineSuggestion>()
        var extraPinnedCount = 0
        suggestions.forEach {
            if (it.info.isPinned) {
                if (pinned == null) {
                    pinned = it
                } else {
                    scrollable.add(extraPinnedCount++, it)
                }
            } else {
                scrollable.add(it)
            }
        }
        service.lifecycleScope.launch {
            alwaysUi.inlineSuggestionsUi.setPinnedView(
                pinned?.let { inflateInlineContentView(it) },
            )
        }
        service.lifecycleScope.launch {
            val views = scrollable.map { s ->
                service.lifecycleScope.async {
                    inflateInlineContentView(s)
                }
            }.awaitAll()
            alwaysUi.inlineSuggestionsUi.setScrollableViews(views)
        }
        isInlineSuggestionPresent = true
        evalAlwaysUiState()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inflateInlineContentView(suggestion: InlineSuggestion): InlineContentView? = suspendCancellableCoroutine { c ->
        // callback view might be null
        suggestion.inflate(context, suggestionSize, directExecutor) { v ->
            c.resume(v)
        }
    }

    /**
     * Seed the toolbar toggle buttons with the current value of their rime
     * options. Rime access stays here in the delegate: the buttons themselves
     * are pure views and only react to [updateButtonsStyle].
     */
    private fun syncToolbarOptionStates() {
        val options = alwaysUi.toggleOptions()
        if (options.isEmpty()) return
        rime.launchOnReady { api ->
            val states = options.associateWith { api.getRuntimeOption(it) }
            ContextCompat.getMainExecutor(context).execute {
                states.forEach { (option, enabled) ->
                    alwaysUi.updateButtonsStyle(option, enabled)
                }
            }
        }
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        alwaysUi.updateButtonsStyle(value.option, value.value)
    }
}
