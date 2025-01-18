// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

/** Manages [Keyboard]s and their status. **/
@Deprecated("Migrate into KeyboardWindow")
object KeyboardSwitcher {
    lateinit var currentKeyboard: Keyboard
    var currentKeyboardView: KeyboardView? = null
}
