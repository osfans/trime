/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.util.float
import com.osfans.trime.util.get
import com.osfans.trime.util.int
import com.osfans.trime.util.pairs
import kotlinx.parcelize.Parcelize

@Parcelize
data class Window(
    val insets: Padding = Padding(4, 4),
    val itemPadding: Padding = Padding(2, 4),
    val minWidth: Int = 0,
    val cornerRadius: Float = 0f,
    val border: Int = 0,
    val shadow: Float = 0f,
    val alpha: Float = 1f,
    val foreground: Foreground = Foreground(),
) : Parcelable {

    @Parcelize
    data class Padding(
        val vertical: Int = 0,
        val horizontal: Int = 0,
    ) : Parcelable {
        companion object {
            fun decode(node: YamlNode?): Padding = Padding(
                vertical = node?.pairs?.get("vertical")?.int ?: 0,
                horizontal = node?.pairs?.get("horizontal")?.int ?: 0,
            )
        }
    }

    @Parcelize
    data class Foreground(
        val labelFontSize: Float = 20f,
        val textFontSize: Float = 20f,
        val commentFontSize: Float = 20f,
    ) : Parcelable {
        companion object {
            fun decode(node: YamlNode?): Foreground = Foreground(
                labelFontSize = node?.pairs?.get("label_font_size")?.float ?: 20f,
                textFontSize = node?.pairs?.get("text_font_size")?.float ?: 20f,
                commentFontSize = node?.pairs?.get("comment_font_size")?.float ?: 20f,
            )
        }
    }

    companion object {
        fun decode(node: YamlNode?): Window = Window(
            insets = node?.pairs?.get("insets")?.let {
                Padding.decode(it)
            } ?: Padding(4, 4),
            itemPadding = node?.pairs?.get("item_padding")?.let {
                Padding.decode(it)
            } ?: Padding(2, 4),
            minWidth = node?.pairs?.get("min_width")?.int ?: 0,
            cornerRadius = node?.pairs?.get("corner_radius")?.float ?: 0f,
            border = node?.pairs?.get("border")?.int ?: 0,
            shadow = node?.pairs?.get("shadow")?.float ?: 0f,
            alpha = node?.pairs?.get("alpha")?.float ?: 1f,
            foreground = Foreground.decode(node?.pairs?.get("foreground")),
        )
    }
}
