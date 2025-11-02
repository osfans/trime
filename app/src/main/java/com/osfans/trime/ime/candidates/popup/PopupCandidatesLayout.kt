/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

enum class PopupCandidatesLayout(override val stringRes: Int) : PreferenceDelegateEnum {
    AUTOMATIC(R.string.automatic),
    HORIZONTAL(R.string.horizontal),
    VERTICAL(R.string.vertical),
}
