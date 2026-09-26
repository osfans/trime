/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable
data class Preedit(
    val horizontalPadding: Int = 8,
    val topStartRadius: Float = 0f,
    val topEndRadius: Float = 0f,
    val alpha: Float = 0.8f,
    val foreground: Foreground = Foreground(),
) {

    @Serializable
    data class Foreground(
        val fontSize: Float = 16f,
    )
}
