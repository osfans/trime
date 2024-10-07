// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.mapper.GeneralStyleMapper
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.util.config.Config
import com.osfans.trime.util.config.ConfigList
import com.osfans.trime.util.config.ConfigMap
import timber.log.Timber
import kotlin.system.measureTimeMillis

/** 主题和样式配置  */
class Theme(
    name: String,
) {
    val generalStyle: GeneralStyle
    val liquid: Liquid
    val keyboards: Keyboards

    var presetKeys: ConfigMap? = null
        private set
    var fallbackColors: ConfigMap? = null
        private set
    var presetColorSchemes: ConfigMap? = null
        private set
    var presetKeyboards: ConfigMap? = null
        private set

    companion object {
        private const val VERSION_KEY = "config_version"
        private const val DEFAULT_THEME_NAME = "trime"

        private fun deploy(active: String): Config? {
            val ext = if (active == DEFAULT_THEME_NAME) ".yaml" else ".trime.yaml"
            val nameWithExtension = "$active$ext"
            val isDeployed: Boolean
            measureTimeMillis {
                isDeployed = Rime.deployRimeConfigFile(nameWithExtension, VERSION_KEY)
            }.also {
                if (isDeployed) {
                    Timber.i("Deployed theme file '$nameWithExtension' in $it ms")
                } else {
                    Timber.w("Failed to deploy theme file '$nameWithExtension'")
                }
            }
            return Config.create(nameWithExtension.removeSuffix(".yaml"))
        }
    }

    init {
        Timber.i("Initializing current theme '$name'")
        val config =
            if (name != DEFAULT_THEME_NAME) {
                deploy(name) ?: deploy(DEFAULT_THEME_NAME)
            } else {
                deploy(name)
            } ?: Config()

        liquid = Liquid(config)
        keyboards = Keyboards(config)
        generalStyle = mapToGeneralStyle(config)
        fallbackColors = config.getMap("fallback_colors")
        presetKeys = config.getMap("preset_keys")
        presetColorSchemes = config.getMap("preset_color_schemes")
        presetKeyboards = config.getMap("preset_keyboards")
        Timber.i("The theme is initialized")
    }

    private fun mapToGeneralStyle(config: Config): GeneralStyle {
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

    class Liquid(
        private val config: Config,
    ) {
        fun getInt(key: String): Int = config.getInt("liquid_keyboard/$key")

        fun getFloat(key: String): Float = config.getFloat("liquid_keyboard/$key")

        fun getList(key: String): ConfigList? = config.getList("liquid_keyboard/$key")

        fun getMap(key: String): ConfigMap? = config.getMap("liquid_keyboard/$key")
    }

    class Keyboards(
        private val config: Config,
    ) {
        fun getMap(key: String): ConfigMap? = config.getMap("preset_keyboards/$key")
    }
}
