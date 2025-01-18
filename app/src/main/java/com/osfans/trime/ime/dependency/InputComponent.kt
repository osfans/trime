// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.dependency

import android.content.Context
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.EnterKeyLabelModule
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.CandidateModule
import com.osfans.trime.ime.composition.PreeditModule
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.preview.KeyPreviewChoreographer
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.ime.window.BoardWindowManager
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@InputScope
@Component
abstract class InputComponent(
    @get:InputScope @get:Provides val inputView: InputView,
    @get:InputScope @get:Provides val themedContext: Context,
    @get:InputScope @get:Provides val theme: Theme,
    @get:InputScope @get:Provides val service: TrimeInputMethodService,
    @get:InputScope @get:Provides val rime: RimeSession,
) {
    abstract val broadcaster: InputBroadcaster
    abstract val enterKeyLabel: EnterKeyLabelModule
    abstract val quickBar: QuickBar
    abstract val preedit: PreeditModule
    abstract val windowManager: BoardWindowManager
    abstract val preview: KeyPreviewChoreographer
    abstract val keyboardWindow: KeyboardWindow
    abstract val commonKeyboardActionListener: CommonKeyboardActionListener
    abstract val liquidKeyboard: LiquidKeyboard
    abstract val candidate: CandidateModule
}
