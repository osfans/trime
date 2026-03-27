/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.data.theme.model.Window
import com.osfans.trime.util.getString
import kotlinx.parcelize.Parcelize

/** 主题和样式配置  */
@Parcelize
data class Theme(
    val name: String,
    val generalStyle: GeneralStyle,
    val preedit: Preedit,
    val window: Window,
    val liquidKeyboard: LiquidKeyboard,
    val presetKeys: Map<String, PresetKey>,
    val presetKeyboards: Map<String, TextKeyboard>,
    val colorSchemes: List<ColorScheme>,
    val fallbackColors: Map<String, String>,
    val toolBar: ToolBar,
) : Parcelable {
    companion object {
        fun decode(node: YamlMap): Theme = Theme(
            name = node.getString("name"),
            generalStyle = GeneralStyle.decode(node.get<YamlMap>("style")!!),
            preedit = Preedit.decode(node.get<YamlMap>("preedit")),
            window = Window.decode(node.get<YamlMap>("window")),
            liquidKeyboard = LiquidKeyboard.decode(node.get<YamlMap>("liquid_keyboard")),
            toolBar = ToolBar.decode(node.get<YamlMap>("tool_bar")),
            presetKeys = node.get<YamlMap>("preset_keys")?.entries?.entries?.associate {
                it.key.content to PresetKey.decode(it.value.yamlMap)
            } ?: emptyMap(),
            presetKeyboards =
            node.get<YamlMap>("preset_keyboards")?.entries?.entries?.associate {
                it.key.content to TextKeyboard.decode(it.value.yamlMap)
            } ?: emptyMap(),
            colorSchemes =
            node.get<YamlMap>("preset_color_schemes")?.entries?.entries?.map {
                ColorScheme(
                    it.key.content,
                    it.value.yamlMap.entries.entries.associate { (k, v) ->
                        k.content to v.yamlScalar.content
                    },
                )
            } ?: emptyList(),
            fallbackColors = node.get<YamlMap>("fallback_colors")?.entries?.entries?.associate {
                it.key.content to it.value.yamlScalar.content
            } ?: emptyMap(),
        )
    }
}
