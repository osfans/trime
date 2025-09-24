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
data class Window(
    val insets: Padding = Padding(4, 4),
    val itemPadding: Padding = Padding(2, 4),
    val minWidth: Int = 0,
    val cornerRadius: Float = 0f,
    val alpha: Float = 1f,
    val foreground: Foreground = Foreground(),
) : Parcelable {

    @Serializable
    @Parcelize
    data class Padding(
        val vertical: Int = 0,
        val horizontal: Int = 0,
    ) : Parcelable

    @Serializable
    @Parcelize
    data class Foreground(
        val labelFontSize: Float = 20f,
        val textFontSize: Float = 20f,
        val commentFontSize: Float = 20f,
    ) : Parcelable
}
