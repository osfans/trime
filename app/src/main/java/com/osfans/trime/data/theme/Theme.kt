/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.data.theme

import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.util.config.Config
import com.osfans.trime.util.config.ConfigItem
import com.osfans.trime.util.config.ConfigList
import com.osfans.trime.util.config.ConfigMap
import timber.log.Timber
import kotlin.system.measureTimeMillis

/** 主题和样式配置  */
class Theme(name: String) {
    val style: Style
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
        private val prefs = AppPrefs.defaultInstance().theme
        private const val VERSION_KEY = "config_version"
        private const val DEFAULT_THEME_NAME = "trime"

        private fun deploy(active: String): Config? {
            val nameWithExtension = "$active.yaml"
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
            return Config.create(active)
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

        style = Style(config)
        liquid = Liquid(config)
        keyboards = Keyboards(config)
        fallbackColors = config.getMap("fallback_colors")
        presetKeys = config.getMap("preset_keys")
        presetColorSchemes = config.getMap("preset_color_schemes")
        presetKeyboards = config.getMap("preset_keyboards")
        Timber.i("The theme is initialized")
        prefs.selectedTheme = name
    }

    class Style(private val config: Config) {
        fun getString(key: String): String = config.getString("style/$key")

        fun getInt(key: String): Int = config.getInt("style/$key")

        fun getFloat(key: String): Float = config.getFloat("style/$key")

        fun getBoolean(key: String): Boolean = config.getBool("style/$key")

        fun getList(key: String): ConfigList? = config.getList("style/$key")

        fun getMap(key: String): ConfigMap? = config.getMap("style/$key")

        fun getItem(key: String): ConfigItem? = config.getItem("style/$key")
    }

    class Liquid(private val config: Config) {
        fun getInt(key: String): Int = config.getInt("liquid_keyboard/$key")

        fun getFloat(key: String): Float = config.getFloat("liquid_keyboard/$key")

        fun getList(key: String): ConfigList? = config.getList("liquid_keyboard/$key")

        fun getMap(key: String): ConfigMap? = config.getMap("liquid_keyboard/$key")
    }

    class Keyboards(private val config: Config) {
        fun getMap(key: String): ConfigMap? = config.getMap("preset_keyboards/$key")
    }
}
