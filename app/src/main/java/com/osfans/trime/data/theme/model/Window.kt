/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable
data class Window(
    val insets: Padding = Padding(4, 4),
    val itemPadding: Padding = Padding(2, 4),
    val minWidth: Int = 0,
    val cornerRadius: Float = 0f,
    val border: Int = 0,
    val shadow: Float = 0f,
    val alpha: Float = 1f,
    val foreground: Foreground = Foreground(),
) {

    @Serializable
    data class Padding(
        val vertical: Int = 0,
        val horizontal: Int = 0,
    )

    @Serializable
    data class Foreground(
        val labelFontSize: Float = 20f,
        val textFontSize: Float = 20f,
        val commentFontSize: Float = 20f,
    )
}
