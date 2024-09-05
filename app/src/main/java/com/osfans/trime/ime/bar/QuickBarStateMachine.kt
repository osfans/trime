// Copyright 2021 - 2023 Fcitx5 for Android Contributors
// Copyright 2021-2023 Fcitx5 for Android Contributors
// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-License-Identifier: LGPL-2.1-or-later

package com.osfans.trime.ime.bar

import com.osfans.trime.ime.bar.QuickBarStateMachine.BooleanKey.CandidateEmpty
import com.osfans.trime.ime.bar.QuickBarStateMachine.State.Always
import com.osfans.trime.ime.bar.QuickBarStateMachine.State.Candidate
import com.osfans.trime.ime.bar.QuickBarStateMachine.State.Tab
import com.osfans.trime.util.BuildTransitionEvent
import com.osfans.trime.util.EventStateMachine
import com.osfans.trime.util.TransitionBuildBlock

object QuickBarStateMachine {
    enum class State {
        Always,
        Candidate,
        Tab,
    }

    enum class BooleanKey : EventStateMachine.BooleanStateKey {
        CandidateEmpty,
    }

    enum class TransitionEvent(
        val builder: TransitionBuildBlock<State, BooleanKey>,
    ) : EventStateMachine.TransitionEvent<State, BooleanKey> by BuildTransitionEvent(builder) {
        CandidatesUpdated({
            from(Always) transitTo Candidate on (CandidateEmpty to false)
            from(Candidate) transitTo Always on (CandidateEmpty to true)
        }),
        BarBoardWindowAttached({
            from(Always) transitTo Tab
            from(Candidate) transitTo Tab
        }),
        WindowDetached({
            // candidate state has higher priority so here it goes first
            from(Tab) transitTo Candidate on (CandidateEmpty to false)
            from(Tab) transitTo Always
        }),
    }

    fun new(block: (State) -> Unit) =
        EventStateMachine<State, TransitionEvent, BooleanKey>(
            initialState = Always,
            externalBooleanStates =
                mutableMapOf(
                    CandidateEmpty to true,
                ),
        ).apply {
            onNewStateListener = block
        }
}
