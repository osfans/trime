/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.os.Parcelable
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.data.theme.model.Window
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
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
        fun decode(node: Node.Mapping): Theme = Theme(
            name = node["name"]?.string!!,
            generalStyle = GeneralStyle.decode(node["style"]!!),
            preedit = Preedit.decode(node["preedit"]?.mapping),
            window = Window.decode(node["window"]?.mapping),
            liquidKeyboard = LiquidKeyboard.decode(node["liquid_keyboard"]?.mapping),
            toolBar = ToolBar.decode(node["tool_bar"]?.mapping),
            presetKeys = node["preset_keys"]?.mapping?.entries?.associate {
                it.key.string!! to PresetKey.decode(it.value.mapping!!)
            } ?: emptyMap(),
            presetKeyboards =
            node["preset_keyboards"]?.mapping?.entries?.associate {
                it.key.string!! to TextKeyboard.decode(it.value.mapping!!)
            } ?: emptyMap(),
            colorSchemes =
            node["preset_color_schemes"]?.mapping?.map {
                ColorScheme(
                    it.key.string!!,
                    it.value.mapping!!.entries.associate { (k, v) ->
                        k.string!! to v.string!!
                    },
                )
            } ?: emptyList(),
            fallbackColors = node["fallback_colors"]?.mapping?.entries?.associate {
                it.key.string!! to it.value.string!!
            } ?: emptyMap(),
        )
    }
}
