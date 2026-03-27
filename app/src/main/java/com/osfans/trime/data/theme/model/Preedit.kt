/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.osfans.trime.util.getFloat
import com.osfans.trime.util.getInt
import kotlinx.parcelize.Parcelize

@Parcelize
data class Preedit(
    val horizontalPadding: Int = 8,
    val topEndRadius: Float = 0f,
    val alpha: Float = 0.8f,
    val foreground: Foreground = Foreground(),
) : Parcelable {

    @Parcelize
    data class Foreground(
        val fontSize: Float = 16f,
    ) : Parcelable {
        companion object {
            fun decode(node: YamlMap?): Foreground = Foreground(
                fontSize = node.getFloat("font_size", 16f),
            )
        }
    }

    companion object {
        fun decode(node: YamlMap?): Preedit = Preedit(
            horizontalPadding = node.getInt("horizontal_padding", 8),
            topEndRadius = node.getFloat("top_end_radius"),
            alpha = node.getFloat("alpha", 0.8f),
            foreground = Foreground.decode(node?.get<YamlMap>("foreground")),
        )
    }
}
