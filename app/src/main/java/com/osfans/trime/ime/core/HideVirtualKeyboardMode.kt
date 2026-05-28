/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

enum class HideVirtualKeyboardMode(
    override val stringRes: Int,
) : PreferenceDelegateEnum {
    NEVER(R.string.hide_virtual_keyboard_never),
    AUTO(R.string.hide_virtual_keyboard_auto),
    ALWAYS(R.string.hide_virtual_keyboard_always),
}
