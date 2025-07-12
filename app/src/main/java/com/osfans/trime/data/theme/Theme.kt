// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.theme.mapper.GeneralStyleMapper
import com.osfans.trime.data.theme.mapper.LiquidKeyboardMapper
import com.osfans.trime.data.theme.mapper.TextKeyboardMapper
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.getString
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.File

/** 主题和样式配置  */
@Parcelize
data class Theme(
    val configId: String,
    val name: String,
    val generalStyle: GeneralStyle,
    val liquidKeyboard: LiquidKeyboard,
    val presetKeys: Map<String, PresetKey>,
    val presetKeyboards: Map<String, TextKeyboard>,
    val colorSchemes: List<ColorScheme>,
    val fallbackColors: Map<String, String>,
) : Parcelable {
    companion object {
        private const val CONFIG_VERSION_KEY = "config_version"

        fun decodeByConfigId(configId: String): Theme {
            if (!Rime.deployRimeConfigFile(configId, CONFIG_VERSION_KEY)) {
                Timber.w("Failed to deploy theme config file '$configId.yaml'")
            }
            val yaml = ThemeFilesManager.yaml
            val file = File(DataManager.resolveDeployedResourcePath(configId))
            val root = yaml.parseToYamlNode(file.readText()).yamlMap
            return Theme(
                configId = configId,
                name = root.getString("name"),
                generalStyle = GeneralStyleMapper(root.get<YamlMap>("style")!!).map(),
                liquidKeyboard =
                    when (val node = root.get<YamlMap>("liquid_keyboard")) {
                        null -> LiquidKeyboard()
                        else -> LiquidKeyboardMapper(node).map()
                    },
                presetKeys =
                    when (val map = root.get<YamlMap>("preset_keys")) {
                        null -> emptyMap()
                        else -> yaml.decodeFromYamlNode(map)
                    },
                presetKeyboards =
                    root.get<YamlMap>("preset_keyboards")?.entries?.entries?.associate {
                        it.key.content to TextKeyboardMapper(it.value.yamlMap).map()
                    } ?: emptyMap(),
                colorSchemes =
                    root.get<YamlMap>("preset_color_schemes")?.entries?.entries?.map {
                        ColorScheme(
                            it.key.content,
                            it.value.yamlMap.entries.entries.associate { (k, v) ->
                                k.content to v.yamlScalar.content
                            },
                        )
                    } ?: emptyList(),
                fallbackColors =
                    when (val map = root.get<YamlMap>("fallback_colors")) {
                        null -> emptyMap()
                        else -> yaml.decodeFromYamlNode(map)
                    },
            )
        }
    }
}
