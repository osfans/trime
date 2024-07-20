// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.osfans.trime.core.RimeNotification.OptionNotification
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import me.tatarka.inject.annotations.Inject
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

@InputScope
@Inject
class KeyboardWindow(
    private val context: Context,
    private val service: TrimeInputMethodService,
) : BoardWindow.NoBarBoardWindow(), ResidentWindow, InputBroadcastReceiver {
    val mainKeyboardView by lazy { KeyboardView(context) }

    private lateinit var keyboardView: FrameLayout

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = KeyboardWindow

    override fun onCreateView(): View {
        keyboardView = context.frameLayout()
        keyboardView.apply { add(mainKeyboardView, lParams(matchParent, matchParent)) }
        return keyboardView
    }

    override fun onRimeOptionUpdated(value: OptionNotification.Value) {
        when (value.option) {
            "_hide_key_hint" -> mainKeyboardView.showKeyHint = !value.value
            "_hide_key_symbol" -> mainKeyboardView.showKeySymbol = !value.value
        }
        mainKeyboardView.invalidateAllKeys()
    }

    override fun onAttached() {
        mainKeyboardView.onKeyboardActionListener = service.textInputManager
    }

    override fun onDetached() {
        mainKeyboardView.onKeyboardActionListener = null
    }
}
