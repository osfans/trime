// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyCode

object KeyActionManager {
    private val actionCache = mutableMapOf<KeyActionToken, KeyAction>()

    fun getAction(token: String) = getAction(KeyActionToken.Plain(token))

    fun getAction(token: KeyActionToken): KeyAction = actionCache.getOrPut(token) {
        KeyAction(token, ThemeManager.activeTheme.presetKeys)
    }

    fun resetCache() = actionCache.clear()

    /**
     * Lists presets whose send value can never resolve to a key, so that a
     * theme is checked once at activation time instead of on first use.
     */
    fun presetDiagnostics(presetKeys: Map<String, PresetKey>): List<String> = presetKeys.mapNotNull { (name, preset) ->
        val (keycode, modifiers) = KeyCode.parse(preset.send)
        if (preset.send.isNotEmpty() && keycode == 0 && modifiers == 0) {
            "preset '$name' has an unrecognized send '${preset.send}'"
        } else {
            null
        }
    }
}
