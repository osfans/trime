// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

data class SchemaItem(
    val id: String,
    val name: String = "",
)

data class CandidateItem(
    val comment: String,
    val text: String,
)

data class InputStatus(
    val isDisabled: Boolean = true,
    val isComposing: Boolean = false,
    val isAsciiMode: Boolean = true,
    val isFullShape: Boolean = false,
    val isSimplified: Boolean = false,
    val isTraditional: Boolean = false,
    val isAsciiPunch: Boolean = true,
)
