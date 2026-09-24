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
import com.osfans.trime.util.sequence
import com.osfans.trime.util.string
import kotlinx.parcelize.Parcelize

@Parcelize
data class ToolBar(
    val primaryButton: Button? = null,
    val buttons: List<Button> = emptyList(),
    val buttonSpacing: Int = 18,
    val buttonFont: List<String> = emptyList(),
    val backStyle: String = "ic@arrow-left",
) : Parcelable {

    @Parcelize
    data class Button(
        val background: Background = Background(),
        val foreground: Foreground = Foreground(),
        val action: String = "",
        val longPressAction: String = "",
        val size: List<Int> = emptyList(),
    ) : Parcelable {

        @Parcelize
        data class Background(
            val type: Type = Type.RECTANGLE,
            val cornerRadius: Float = 10f,
            val normal: String = "",
            val highlight: String = "",
            val verticalInset: Int = 4,
            val horizontalInset: Int = 4,
        ) : Parcelable {
            enum class Type {
                RECTANGLE,
                CIRCLE,
            }
            companion object {
                fun decode(node: YamlMap): Background = Background(
                    type = runCatching {
                        val value = node.pairs["type"]?.string ?: "RECTANGLE"
                        Type.valueOf(value.uppercase())
                    }.getOrDefault(Type.RECTANGLE),
                    cornerRadius = node.pairs["corner_radius"]?.float ?: 10f,
                    normal = node.pairs["normal"]?.string ?: "",
                    highlight = node.pairs["highlight"]?.string ?: "",
                    verticalInset = node.pairs["vertical_inset"]?.int ?: 4,
                    horizontalInset = node.pairs["horizontal_inset"]?.int ?: 4,
                )
            }
        }

        @Parcelize
        data class Foreground(
            val style: String = "",
            val optionStyles: List<String> = emptyList(),
            val normal: String = "",
            val highlight: String = "",
            val fontSize: Float = 18f,
            val padding: Int = 4,
        ) : Parcelable {
            companion object {
                fun decode(node: YamlMap): Foreground = Foreground(
                    style = node.pairs["style"]?.string ?: "",
                    optionStyles = node.pairs["option_styles"]?.sequence?.items
                        ?.mapNotNull(YamlNode::string) ?: emptyList(),
                    normal = node.pairs["normal"]?.string ?: "",
                    highlight = node.pairs["highlight"]?.string ?: "",
                    fontSize = node.pairs["font_size"]?.float ?: 18f,
                    padding = node.pairs["padding"]?.int ?: 4,
                )
            }
        }

        companion object {
            fun decode(node: YamlMap): Button = Button(
                background = node.pairs["background"]?.mapping?.let {
                    Background.decode(it)
                } ?: Background(),
                foreground = node.pairs["foreground"]?.mapping?.let {
                    Foreground.decode(it)
                } ?: Foreground(),
                action = node.pairs["action"]?.string ?: "",
                longPressAction = node.pairs["long_press_action"]?.string ?: "",
                size = node.pairs["size"]?.sequence?.items?.mapNotNull { it.int } ?: emptyList(),
            )
        }
    }

    companion object {
        fun decode(node: YamlMap?): ToolBar = ToolBar(
            primaryButton = node?.pairs?.get("primary_button")?.mapping?.let { Button.decode(it) },
            buttons = node?.pairs?.get("buttons")?.sequence?.items?.map { Button.decode(it.mapping!!) } ?: emptyList(),
            buttonSpacing = node?.pairs?.get("button_spacing")?.int ?: 18,
            buttonFont = node?.pairs?.get("button_font")?.sequence?.items
                ?.mapNotNull(YamlNode::string) ?: emptyList(),
            backStyle = node?.pairs?.get("back_style")?.string ?: "ic@arrow-left",
        )
    }
}
