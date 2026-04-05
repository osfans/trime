/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.int
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
            fun decode(node: Node?): Padding = Padding(
                vertical = node?.get("vertical")?.int ?: 0,
                horizontal = node?.get("horizontal")?.int ?: 0,
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
            fun decode(node: Node?): Foreground = Foreground(
                labelFontSize = node?.get("label_font_size")?.float ?: 20f,
                textFontSize = node?.get("text_font_size")?.float ?: 20f,
                commentFontSize = node?.get("comment_font_size")?.float ?: 20f,
            )
        }
    }

    companion object {
        fun decode(node: Node?): Window = Window(
            insets = node?.get("insets")?.let {
                Padding.decode(it)
            } ?: Padding(4, 4),
            itemPadding = node?.get("item_padding")?.let {
                Padding.decode(it)
            } ?: Padding(2, 4),
            minWidth = node?.get("min_width")?.int ?: 0,
            cornerRadius = node?.get("corner_radius")?.float ?: 0f,
            alpha = node?.get("alpha")?.float ?: 1f,
            foreground = Foreground.decode(node?.get("foreground")),
        )
    }
}
