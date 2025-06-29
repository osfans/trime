/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ColorScheme(
    val id: String,
    val colors: Map<String, String>,
) : Parcelable
