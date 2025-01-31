// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

data class SchemaItem(
    val id: String,
    val name: String = "",
)

data class CandidateItem(
    val text: String,
    val comment: String = "",
)
