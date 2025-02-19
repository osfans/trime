// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.mapper.GeneralStyleMapper
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.util.config.Config
import com.osfans.trime.util.config.ConfigItem
import timber.log.Timber

/** 主题和样式配置  */
class Theme(
    val configId: String,
) {
    private val config = Config.create(configId)

    val name = config.getString("name")
    val generalStyle = mapToGeneralStyle()
    val liquidKeyboards: Map<String, ConfigItem> =
        config.getMap("liquid_keyboard") ?: mapOf()
    val presetKeys: Map<String, Map<String, ConfigItem>> =
        config
            .getMap("preset_keys")
            ?.entries
            ?.associate { (k, v) ->
                k to
                    v.configMap.entries.associate { (s, n) ->
                        s to n
                    }
            } ?: mapOf()
    val presetKeyboards: Map<String, ConfigItem> =
        config.getMap("preset_keyboards") ?: mapOf()
    val presetColorSchemes: List<ColorScheme> =
        config
            .getMap("preset_color_schemes")
            ?.entries
            ?.map {
                ColorScheme(
                    it.key,
                    it.value.configMap.entries.associate { (k, v) ->
                        k to v.configValue.getString()
                    },
                )
            }?.toList() ?: listOf()
    val fallbackColors: Map<String, String> =
        config
            .getMap("fallback_colors")
            ?.mapValues { it.value.configValue.getString() } ?: mapOf()

    private fun mapToGeneralStyle(): GeneralStyle {
        val generalStyleMap = config.getMap("style")
        val mapper = GeneralStyleMapper(generalStyleMap)
        val generalStyle = mapper.map()

        Timber.w(
            "GeneralStyleMapper (%d) Warnings: %s",
            mapper.errors.size,
            mapper.errors.joinToString(","),
        )
        return generalStyle
    }
}
