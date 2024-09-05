// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.enums

// 按键的特殊命令枚举（仅用于新增的liquidKeyboard）
enum class KeyCommandType {
    NULL,
    LEFT,
    RIGHT,
    DEL_LEFT,
    DEL_RIGHT,
    UNDO,
    REDO,
    ;

    companion object {
        @JvmStatic
        fun fromString(code: String): KeyCommandType =
            runCatching {
                valueOf(code.uppercase())
            }.getOrDefault(NULL)
    }
}
