// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

data class KeyboardSize(
    val rowWidthTotalWeight: Map<Int, Float>,
    val defaultWidth: Float,
    val multiplier: Float,
    val scaledHeight: List<Int>,
    val scaledVerticalGap: Int,
)
