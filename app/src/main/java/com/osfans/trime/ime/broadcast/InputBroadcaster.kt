// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.broadcast

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.RimeProto
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.window.BoardWindow
import me.tatarka.inject.annotations.Inject
import java.util.concurrent.ConcurrentLinkedQueue

@InputScope
@Inject
class InputBroadcaster : InputBroadcastReceiver {
    private val receivers = ConcurrentLinkedQueue<InputBroadcastReceiver>()

    fun <T> addReceiver(receiver: T) {
        if (receiver is InputBroadcastReceiver && receiver !is InputBroadcaster) {
            if (!receivers.contains(receiver)) {
                receivers.add(receiver)
            }
        }
    }

    fun <T> removeReceiver(receiver: T) {
        if (receiver is InputBroadcastReceiver && receiver !is InputBroadcaster) {
            receivers.remove(receiver)
        }
    }

    fun clear() {
        receivers.clear()
    }

    override fun onStartInput(info: EditorInfo) {
        receivers.forEach { it.onStartInput(info) }
    }

    override fun onSelectionUpdate(
        start: Int,
        end: Int,
    ) {
        receivers.forEach { it.onSelectionUpdate(start, end) }
    }

    override fun onRimeSchemaUpdated(schema: SchemaItem) {
        receivers.forEach { it.onRimeSchemaUpdated(schema) }
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        receivers.forEach { it.onRimeOptionUpdated(value) }
    }

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        receivers.forEach { it.onInputContextUpdate(ctx) }
    }

    override fun onWindowAttached(window: BoardWindow) {
        receivers.forEach { it.onWindowAttached(window) }
    }

    override fun onWindowDetached(window: BoardWindow) {
        receivers.forEach { it.onWindowDetached(window) }
    }

    override fun onEnterKeyLabelUpdate(label: String) {
        receivers.forEach { it.onEnterKeyLabelUpdate(label) }
    }

    override fun onInlineSuggestions(suggestions: List<InlineSuggestion>) {
        receivers.forEach { it.onInlineSuggestions(suggestions) }
    }
}
