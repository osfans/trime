/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.broadcast

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.RimeProto
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.ime.window.BoardWindow

interface InputBroadcastReceiver {
    fun onStartInput(info: EditorInfo) {}

    fun onSelectionUpdate(
        start: Int,
        end: Int,
    ) {}

    fun onRimeSchemaUpdated(schema: SchemaItem) {}

    fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {}

    fun onCandidateListUpdate(candidates: Array<CandidateItem>) {}

    fun onCompositionUpdate(data: RimeProto.Context.Composition) {}

    fun onCandidateMenuUpdate(data: RimeProto.Context.Menu) {}

    fun onInputStatusUpdate(value: RimeProto.Status) {}

    fun onWindowAttached(window: BoardWindow) {}

    fun onWindowDetached(window: BoardWindow) {}

    fun onEnterKeyLabelUpdate(label: String) {}

    fun onInlineSuggestions(suggestions: List<InlineSuggestion>) {}
}
