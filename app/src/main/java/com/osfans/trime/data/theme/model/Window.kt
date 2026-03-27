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
data class Window(
    val insets: Padding = Padding(4, 4),
    val itemPadding: Padding = Padding(2, 4),
    val minWidth: Int = 0,
    val cornerRadius: Float = 0f,
    val alpha: Float = 1f,
    val foreground: Foreground = Foreground(),
) : Parcelable {

    @Parcelize
    data class Padding(
        val vertical: Int = 0,
        val horizontal: Int = 0,
    ) : Parcelable {
        companion object {
            fun decode(node: YamlMap?): Padding = Padding(
                vertical = node.getInt("vertical"),
                horizontal = node.getInt("horizontal"),
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
            fun decode(node: YamlMap?): Foreground = Foreground(
                labelFontSize = node.getFloat("label_font_size", 20f),
                textFontSize = node.getFloat("text_font_size", 20f),
                commentFontSize = node.getFloat("comment_font_size", 20f),
            )
        }
    }

    companion object {
        fun decode(node: YamlMap?): Window = Window(
            insets = node?.get<YamlMap>("insets")?.let {
                Padding.decode(it)
            } ?: Padding(4, 4),
            itemPadding = node?.get<YamlMap>("item_padding")?.let {
                Padding.decode(it)
            } ?: Padding(2, 4),
            minWidth = node.getInt("min_width"),
            cornerRadius = node.getFloat("corner_radius"),
            alpha = node.getFloat("alpha", 1f),
            foreground = Foreground.decode(node?.get<YamlMap>("foreground")),
        )
    }
}
