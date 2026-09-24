/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.util.float
import com.osfans.trime.util.int
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import kotlinx.parcelize.Parcelize

@Parcelize
data class Preedit(
    val horizontalPadding: Int = 8,
    val topStartRadius: Float = 0f,
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
                fontSize = node?.pairs?.get("font_size")?.float ?: 16f,
            )
        }
    }

    companion object {
        fun decode(node: YamlMap?): Preedit = Preedit(
            horizontalPadding = node?.pairs?.get("horizontal_padding")?.int ?: 8,
            topStartRadius = node?.pairs?.get("top_start_radius")?.float ?: 0f,
            topEndRadius = node?.pairs?.get("top_end_radius")?.float ?: 0f,
            alpha = node?.pairs?.get("alpha")?.float ?: 0.8f,
            foreground = Foreground.decode(node?.pairs?.get("foreground")?.mapping),
        )
    }
}
