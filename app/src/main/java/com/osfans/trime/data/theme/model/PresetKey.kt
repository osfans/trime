/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class PresetKey(
    val command: String = "",
    val option: String = "",
    val select: String = "",
    val toggle: String = "",
    val label: String = "",
    val preview: String = "",
    val shiftLock: String = "",
    val commit: String = "",
    val text: String = "",
    val sticky: Boolean = false,
    val repeatable: Boolean = false,
    val functional: Boolean = false,
    val states: List<String> = emptyList(),
    val send: String = "",
) : Parcelable
