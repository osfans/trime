/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

enum class ToolbarPosition(
    override val stringRes: Int,
) : PreferenceDelegateEnum {
    TOP(R.string.toolbar_position_top),
    BOTTOM(R.string.toolbar_position_bottom),
}
