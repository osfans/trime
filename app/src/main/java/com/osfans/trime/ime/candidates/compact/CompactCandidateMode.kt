/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

enum class CompactCandidateMode(
    override val stringRes: Int,
) : PreferenceDelegateEnum {
    NEVER_FILL(R.string.horizontal_candidate_never_fill),
    AUTO_FILL(R.string.horizontal_candidate_auto_fill),
    ALWAYS_FILL(R.string.horizontal_candidate_always_fill),
}
