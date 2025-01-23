/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

enum class PopupCandidatesMode(
    override val stringRes: Int,
) : PreferenceDelegateEnum {
    SYSTEM_DEFAULT(R.string.system_default),
    INPUT_DEVICE(R.string.depends_on_input_device),
    ALWAYS_SHOW(R.string.always_show),
    DISABLED(R.string.disable),
}
