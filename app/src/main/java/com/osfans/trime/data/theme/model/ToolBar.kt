/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
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
            val type: String = "rectangle",
            val cornerRadius: Float = 10f,
            val normal: String = "",
            val highlight: String = "",
            val verticalInset: Int = 4,
            val horizontalInset: Int = 4,
        ) : Parcelable {
            companion object {
                fun decode(node: Node.Mapping): Background = Background(
                    type = node["type"]?.string ?: "rectangle",
                    cornerRadius = node["corner_radius"]?.float ?: 10f,
                    normal = node["normal"]?.string ?: "",
                    highlight = node["highlight"]?.string ?: "",
                    verticalInset = node["vertical_inset"]?.int ?: 4,
                    horizontalInset = node["horizontal_inset"]?.int ?: 4,
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
                fun decode(node: Node.Mapping): Foreground = Foreground(
                    style = node["style"]?.string ?: "",
                    optionStyles = node["option_styles"]?.sequence
                        ?.mapNotNull(Node::string) ?: emptyList(),
                    normal = node["normal"]?.string ?: "",
                    highlight = node["highlight"]?.string ?: "",
                    fontSize = node["font_size"]?.float ?: 18f,
                    padding = node["padding"]?.int ?: 4,
                )
            }
        }

        companion object {
            fun decode(node: Node.Mapping): Button = Button(
                background = node["background"]?.mapping?.let {
                    Background.decode(it)
                } ?: Background(),
                foreground = node["foreground"]?.mapping?.let {
                    Foreground.decode(it)
                } ?: Foreground(),
                action = node["action"]?.string ?: "",
                longPressAction = node["long_press_action"]?.string ?: "",
                size = node["size"]?.sequence?.mapNotNull { it.int } ?: emptyList(),
            )
        }
    }

    companion object {
        fun decode(node: Node.Mapping?): ToolBar = ToolBar(
            primaryButton = node?.get("primary_button")?.mapping?.let { Button.decode(it) },
            buttons = node?.get("buttons")?.sequence?.map { Button.decode(it.mapping!!) } ?: emptyList(),
            buttonSpacing = node?.get("button_spacing")?.int ?: 18,
            buttonFont = node?.get("button_font")?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            backStyle = node?.get("back_style")?.string ?: "ic@arrow-left",
        )
    }
}
