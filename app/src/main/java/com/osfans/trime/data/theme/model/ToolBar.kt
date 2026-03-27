/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import com.osfans.trime.util.getFloat
import com.osfans.trime.util.getInt
import com.osfans.trime.util.getString
import com.osfans.trime.util.getStringList
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
                fun decode(node: YamlMap): Background = Background(
                    type = node.getString("type", "rectangle"),
                    cornerRadius = node.getFloat("corner_radius", 10f),
                    normal = node.getString("normal"),
                    highlight = node.getString("highlight"),
                    verticalInset = node.getInt("vertical_inset", 4),
                    horizontalInset = node.getInt("horizontal_inset", 4),
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
                    style = node.getString("style"),
                    optionStyles = node.getStringList("option_styles") ?: emptyList(),
                    normal = node.getString("normal"),
                    highlight = node.getString("highlight"),
                    fontSize = node.getFloat("font_size", 18f),
                    padding = node.getInt("padding", 4),
                )
            }
        }

        companion object {
            fun decode(node: YamlMap): Button = Button(
                background = node.get<YamlMap>("background")?.let {
                    Background.decode(it)
                } ?: Background(),
                foreground = node.get<YamlMap>("foreground")?.let {
                    Foreground.decode(it)
                } ?: Foreground(),
                action = node.getString("action"),
                longPressAction = node.getString("long_press_action"),
                size = node.get<YamlList>("size")?.items?.mapNotNull { it.yamlScalar.content.toIntOrNull() } ?: emptyList(),
            )
        }
    }

    companion object {
        fun decode(node: YamlMap?): ToolBar = ToolBar(
            primaryButton = node?.get<YamlMap>("primary_button")?.let { Button.decode(it) },
            buttons = node?.get<YamlList>("buttons")?.items?.map { Button.decode(it.yamlMap) } ?: emptyList(),
            buttonSpacing = node.getInt("button_spacing", 18),
            buttonFont = node?.getStringList("button_font") ?: emptyList(),
            backStyle = node.getString("back_style", "ic@arrow-left"),
        )
    }
}
