/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.dependency

import android.content.Context
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindowManager
import org.kodein.di.DI
import org.kodein.di.allInstances
import org.kodein.di.bindSingleton
import org.kodein.di.instance

class InputDependencyManager(
    context: Context,
    theme: Theme,
    service: TrimeInputMethodService,
    rime: RimeSession,
) {
    val inputModule = DI.Module("input") {
        bindSingleton { context }
        bindSingleton { theme }
        bindSingleton { service }
        bindSingleton { rime }
        bindSingleton { InputBroadcaster() }
        bindSingleton { PopupDelegate() }
        bindSingleton { EnterKeyDisplayDelegate() }
        bindSingleton { PreeditDelegate() }
        bindSingleton { CommonKeyboardActionListener() }
        bindSingleton { BoardWindowManager() }
        bindSingleton { InputBarDelegate() }
        bindSingleton { CompactCandidateDelegate() }
        bindSingleton { KeyboardWindow() }
        bindSingleton { LiquidWindow() }
    }

    val di = DI {
        import(inputModule)
    }

    private val broadcaster: InputBroadcaster by di.instance()

    fun start() {
        val receivers: List<InputBroadcastReceiver> by di.allInstances()
        receivers.forEach { broadcaster.addReceiver(it) }
    }

    fun stop() {
        broadcaster.clear()
    }

    companion object Factory {
        private var instance: InputDependencyManager? = null

        fun initialize(
            context: Context,
            theme: Theme,
            service: TrimeInputMethodService,
            rime: RimeSession,
        ): InputDependencyManager = InputDependencyManager(context, theme, service, rime).also {
            instance = it
        }

        fun getInstance(): InputDependencyManager = instance ?: throw IllegalStateException(
            "InputDependencyManager is not initialized. Call InputDependencyManager.initialize(...) before getInstance().",
        )
    }
}
