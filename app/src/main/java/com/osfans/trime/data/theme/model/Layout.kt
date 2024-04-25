// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.model

data class Layout(
    val position: String,
    val minLength: Int,
    val maxLength: Int,
    val stickyLines: Int,
    val stickyLinesLand: Int,
    val maxEntries: Int,
    val minCheck: Int,
    val allPhrases: Boolean,
    val border: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val minWidth: Int,
    val minHeight: Int,
    val marginX: Int,
    val marginY: Int,
    val marginBottom: Int,
    val lineSpacing: Int,
    val lineSpacingMultiplier: Float,
    val realMargin: Int,
    val spacing: Int,
    val roundCorner: Int,
    val alpha: Int,
    val elevation: Int,
    val movable: String,
)
