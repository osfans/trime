// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

import kotlinx.serialization.Serializable

@Serializable
data class DataChecksums(
    val sha256: String,
    val files: Map<String, String>,
)
