/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import androidx.annotation.StringRes
import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum

enum class InlinePreeditMode(
    @StringRes override val stringRes: Int,
) : PreferenceDelegateEnum {
    DISABLE(R.string.disable),
    COMPOSING_TEXT(R.string.composing_text),
    COMMIT_TEXT_PREVIEW(R.string.commit_text_preview),
}
