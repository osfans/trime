// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.view.KeyEvent
import com.osfans.trime.ime.keyboard.KeyAction

object KeyActionManager {
    private val actionCache = mutableMapOf<String, KeyAction>()

    fun getAction(actionId: String): KeyAction = actionCache[actionId]
        ?: KeyAction(actionId).also {
            // 空格的 label 需要根据方案动态显示，所以不加入缓存
            if (it.code != KeyEvent.KEYCODE_SPACE) {
                actionCache[actionId] = it
            }
        }

    fun resetCache() = actionCache.clear()
}
