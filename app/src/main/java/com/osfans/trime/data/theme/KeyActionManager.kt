// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.keyboard.KeyAction

object KeyActionManager {
    private val actionCache = mutableMapOf<KeyActionToken, KeyAction>()

    fun getAction(token: String) = getAction(KeyActionToken.Plain(token))

    fun getAction(token: KeyActionToken): KeyAction = actionCache.getOrPut(token) { KeyAction(token) }

    fun resetCache() = actionCache.clear()
}
