// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ViewAnimator
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeNotification.OptionNotification
import com.osfans.trime.core.RimeProto
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ui.AlwaysUi
import com.osfans.trime.ime.bar.ui.CandidateUi
import com.osfans.trime.ime.bar.ui.TabUi
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CompactCandidateModule
import com.osfans.trime.ime.candidates.unrolled.window.FlexboxUnrolledCandidateWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
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
    lazyCompactCandidate: Lazy<CompactCandidateModule>,
) : InputBroadcastReceiver {
    private val compactCandidate by lazyCompactCandidate

    private val prefs = AppPrefs.defaultInstance()

    private val showSwitchers get() = prefs.keyboard.switchesEnabled

    val themedHeight =
        theme.generalStyle.candidateViewHeight + theme.generalStyle.commentHeight

    private fun evalAlwaysUiState() {
        val newState =
            when {
                showSwitchers -> AlwaysUi.State.Switchers
                else -> AlwaysUi.State.Empty
            }
        if (newState == alwaysUi.currentState) return
        alwaysUi.updateState(newState)
    }

    private val alwaysUi: AlwaysUi by lazy {
        AlwaysUi(context, theme).apply {
            switchesUi.apply {
                setSwitches(SchemaManager.visibleSwitches)
                setOnSwitchClick { switch ->
                    val prevEnabled = switch.enabled
                    switch.enabled =
                        if (switch.options.isNullOrEmpty()) {
                            (1 - prevEnabled).also { newValue ->
                                rime.launchOnReady {
                                    it.setRuntimeOption(switch.name!!, newValue == 1)
                                }
                            }
                        } else {
                            val options = switch.options
                            ((prevEnabled + 1) % options.size).also { newValue ->
                                rime.launchOnReady {
                                    it.setRuntimeOption(options[prevEnabled], false)
                                    it.setRuntimeOption(options[newValue], true)
                                }
                            }
                        }
                }
            }
        }
    }

    private val candidateUi by lazy {
        CandidateUi(context, compactCandidate.view)
    }

    private val tabUi by lazy {
        TabUi(context)
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
            windowManager.attachWindow(
                FlexboxUnrolledCandidateWindow(context, service, rime, theme, this, windowManager, compactCandidate),
            )
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

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
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
            tabUi.removeExternal()
        }
        view.displayedChild = index
    }

    val view by lazy {
        ViewAnimator(context).apply {
            rime.launchOnReady {
                visibility =
                    if (it.getRuntimeOption("_hide_candidate") ||
                        it.getRuntimeOption("_hide_bar")
                    ) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
            }
            background =
                ColorManager.getDrawable(
                    context,
                    "candidate_background",
                    theme.generalStyle.candidateBorder,
                    "candidate_border_color",
                    theme.generalStyle.candidateBorderRound,
                )
            add(alwaysUi.root, lParams(matchParent, matchParent))
            add(candidateUi.root, lParams(matchParent, matchParent))
            add(tabUi.root, lParams(matchParent, matchParent))

            evalAlwaysUiState()
        }
    }

    override fun onStartInput(info: EditorInfo) {
        evalAlwaysUiState()
    }

    override fun onRimeSchemaUpdated(schema: SchemaItem) {
        if (alwaysUi.currentState == AlwaysUi.State.Switchers) {
            service.lifecycleScope.launch {
                SchemaManager.init(schema.id)
                alwaysUi.switchesUi.setSwitches(SchemaManager.visibleSwitches)
            }
        }
    }

    override fun onRimeOptionUpdated(value: OptionNotification.Value) {
        when (value.option) {
            "_hide_comment" -> {
                // candidateUi.candidates.shouldShowComment = !value.value
            }
            "_hide_candidate", "_hide_bar" -> {
                view.visibility = if (value.value) View.GONE else View.VISIBLE
            }
        }
        if (alwaysUi.currentState == AlwaysUi.State.Switchers) {
            service.lifecycleScope.launch {
                SchemaManager.updateSwitchOptions()
                alwaysUi.switchesUi.setSwitches(SchemaManager.visibleSwitches)
            }
        }
    }

    override fun onWindowAttached(window: BoardWindow) {
        if (window is BoardWindow.BarBoardWindow) {
            window.onCreateBarView()?.let { tabUi.addExternal(it) }
            barStateMachine.push(QuickBarStateMachine.TransitionEvent.BarBoardWindowAttached)
        }
    }

    override fun onWindowDetached(window: BoardWindow) {
        barStateMachine.push(QuickBarStateMachine.TransitionEvent.WindowDetached)
    }
}
