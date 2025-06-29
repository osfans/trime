// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SimpleKeyBean(
    val text: String = "",
    private val _label: String = "",
) : Parcelable {
    val label = _label.ifEmpty { text }
}
