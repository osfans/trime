/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ThemeStub(
    @Transient
    var configId: String = "",
    val name: String,
)
