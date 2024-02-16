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
import com.osfans.trime.util.CollectionUtils
import timber.log.Timber
import java.util.Objects
import kotlin.system.measureTimeMillis

/** 主题和样式配置  */
class Theme() {
    private var generalStyle: Map<String, Any?>? = null
    private var presetKeyboards: Map<String, Any?>? = null
    private var liquidKeyboard: Map<String, Any?>? = null

    var presetKeys: Map<String, Map<String, Any?>?>? = null
        private set
    var fallbackColors: Map<String, String?>? = null
        private set
    lateinit var presetColorSchemes: Map<String, Map<String, Any>?>
        private set
    var allKeyboardIds: List<String> = listOf()
        private set

    val style = Style(this)
    val liquid = Liquid(this)
    val keyboards = Keyboards(this)

    companion object {
        private val prefs = AppPrefs.defaultInstance().theme
        private const val VERSION_KEY = "config_version"
        private const val DEFAULT_THEME_NAME = "trime"
    }

    init {
        init()
    }

    fun init() {
        val active = prefs.selectedTheme
        Timber.i("Initializing theme, currentThemeName=%s ...", active)
        runCatching {
            val themeFileName = "$active.yaml"
            Timber.i("Deploying theme '%s' ...", themeFileName)
            if (!Rime.deployRimeConfigFile(themeFileName, VERSION_KEY)) {
                Timber.w("Deploying theme '%s' failed", themeFileName)
            }
            Timber.d("Fetching global theme config map ...")
            measureTimeMillis {
                var fullThemeConfigMap: Map<String, Any>?
                if (Rime.getRimeConfigMap(active, "").also { fullThemeConfigMap = it } == null) {
                    fullThemeConfigMap = Rime.getRimeConfigMap(DEFAULT_THEME_NAME, "")
                }
                Objects.requireNonNull(fullThemeConfigMap, "The theme file cannot be empty!")
                Timber.d("Fetching done")
                generalStyle = fullThemeConfigMap!!["style"] as Map<String, Any?>?
                fallbackColors = fullThemeConfigMap!!["fallback_colors"] as Map<String, String?>?
                presetKeys = fullThemeConfigMap!!["preset_keys"] as Map<String, Map<String, Any?>?>?
                presetColorSchemes = fullThemeConfigMap!!["preset_color_schemes"] as Map<String, Map<String, Any>?>
                presetKeyboards = fullThemeConfigMap!!["preset_keyboards"] as Map<String, Any?>?
                liquidKeyboard = fullThemeConfigMap!!["liquid_keyboard"] as Map<String, Any?>?
                // 将 presetKeyboards 的所有 key 转为 allKeyboardIds
                allKeyboardIds = presetKeyboards?.keys?.toList()!!
            }.also {
                Timber.d("Setting up all theme config map takes $it ms")
            }
            Timber.i("The theme is initialized")
        }.getOrElse {
            Timber.e("Failed to parse the theme: ${it.message}")
            if (prefs.selectedTheme != DEFAULT_THEME_NAME) {
                prefs.selectedTheme = DEFAULT_THEME_NAME
                init()
            }
        }
    }

    class Style(private val theme: Theme) {
        fun getString(key: String): String {
            return CollectionUtils.obtainString(theme.generalStyle, key, "")
        }

        fun getInt(key: String): Int {
            return CollectionUtils.obtainInt(theme.generalStyle, key, 0)
        }

        fun getFloat(key: String): Float {
            return CollectionUtils.obtainFloat(theme.generalStyle, key, 0f)
        }

        fun getBoolean(key: String): Boolean {
            return CollectionUtils.obtainBoolean(theme.generalStyle, key, false)
        }

        fun getObject(key: String): Any? {
            return CollectionUtils.obtainValue(theme.generalStyle, key)
        }
    }

    class Liquid(private val theme: Theme) {
        fun getObject(key: String): Any? {
            return CollectionUtils.obtainValue(theme.liquidKeyboard, key)
        }

        fun getInt(key: String): Int {
            return CollectionUtils.obtainInt(theme.liquidKeyboard, key, 0)
        }

        fun getFloat(key: String): Float {
            return CollectionUtils.obtainFloat(theme.liquidKeyboard, key, theme.style.getFloat(key))
        }
    }

    class Keyboards(private val theme: Theme) {
        fun getObject(key: String): Any? {
            return CollectionUtils.obtainValue(theme.presetKeyboards, key)
        }
    }
}
