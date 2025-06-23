// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.mapper.GeneralStyleMapper
import com.osfans.trime.data.theme.mapper.LiquidKeyboardMapper
import com.osfans.trime.data.theme.mapper.PresetKeyMapper
import com.osfans.trime.data.theme.mapper.TextKeyboardMapper
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.config.Config
import timber.log.Timber

/** 主题和样式配置  */
data class Theme(
    val configId: String,
    val name: String,
    val generalStyle: GeneralStyle,
    val liquidKeyboard: LiquidKeyboard,
    val presetKeys: Map<String, PresetKey>,
    val presetKeyboards: Map<String, TextKeyboard>,
    val colorSchemes: List<ColorScheme>,
    val fallbackColors: Map<String, String>,
) {
    companion object {
        private const val CONFIG_VERSION_KEY = "config_version"

        fun open(configId: String): Theme {
            if (!Rime.deployRimeConfigFile(configId, CONFIG_VERSION_KEY)) {
                Timber.w("Failed to deploy theme config file '$configId.yaml'")
            }
            return Config.openConfig(configId).let { c ->
                Theme(
                    configId = configId,
                    name = c.getString("name"),
                    generalStyle = GeneralStyleMapper("style", c).map(),
                    liquidKeyboard = LiquidKeyboardMapper("liquid_keyboard", c).map(),
                    presetKeys =
                        c.getMap("preset_keys").mapValues {
                            PresetKeyMapper("preset_keys/${it.key}", c).map()
                        },
                    presetKeyboards =
                        c.getMap("preset_keyboards").mapValues {
                            TextKeyboardMapper("preset_keyboards/${it.key}", c).map()
                        },
                    colorSchemes =
                        c
                            .getMap("preset_color_schemes")
                            .map { ColorScheme(it.key, it.value.getStringValueMap("")) },
                    fallbackColors = c.getStringValueMap("fallback_colors"),
                ).also {
                    Timber.d("color_schemes: ${it.colorSchemes}")
                    Timber.d("fallback: ${it.fallbackColors.entries.joinToString()}")
                }
            }
        }
    }
}
