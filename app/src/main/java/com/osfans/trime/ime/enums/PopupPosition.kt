// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.enums

/**
 * 悬浮窗。候选栏文字，按键文字，按键气泡文字的定位方式
 */
enum class PopupPosition {
    // 跟随光标
    LEFT,
    LEFT_UP,
    RIGHT,
    RIGHT_UP,

    // 固定位置
    DRAG,
    FIXED,

    // 相对位置
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_LEFT,
    TOP_RIGHT,

    // 暂未实现的相对位置
    TOP_CENTER,
    BOTTOM_CENTER,
    CENTER,
    ;

    companion object {
        fun fromString(
            code: String,
            default: PopupPosition = FIXED,
        ): PopupPosition =
            runCatching {
                valueOf(code.uppercase())
            }.getOrDefault(default)
    }
}
