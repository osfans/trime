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

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.core.math.MathUtils
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager.userDataDir
import com.osfans.trime.data.sound.SoundThemeManager
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.util.CollectionUtils
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.bitmapDrawable
import com.osfans.trime.util.config.Config
import com.osfans.trime.util.dp2px
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import timber.log.Timber
import java.io.File
import kotlin.system.measureTimeMillis

/** 主题和样式配置  */
class Theme(val themeId: String) {
    private val config = Config.create(themeId)
        ?: throw IllegalArgumentException("Nonexistent theme config file $themeId.yaml")
    private var currentColorSchemeId: String? = null
    private val fallbackColors = config.getMap("fallback_colors")
        ?.decode(MapSerializer(String.serializer(), String.serializer()))
    private val presetColorSchemes = config.getMap("preset_color_schemes")
        ?.decode(MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())))

    // 遍历当前配色方案的值、fallback的值，从而获得当前方案的全部配色Map
    private val currentColors: MutableMap<String, Any> = hashMapOf()

    @JvmField
    val colors = Colors(this)

    companion object {
        private const val VERSION_KEY = "config_version"
        private val appPrefs = AppPrefs.defaultInstance()
    }

    init {
        Rime.getInstance()
        measureTimeMillis {
            Rime.deployRimeConfigFile("$themeId.yaml", VERSION_KEY)
            Key.presetKeys = Rime.getRimeConfigMap(themeId, "preset_keys") as? Map<String?, Map<String?, Any?>?>
        }.also { Timber.d("Setting up all theme config map takes $it ms") }
        measureTimeMillis {
            initCurrentColors()
        }.also { Timber.d("Initializing cache takes $it ms") }
        Timber.d("Setting sound from color ...")
        SoundThemeManager.switchSound(colors.getString("sound"))
    }

    fun s(key: String) = config.getString(key) ?: ""

    fun i(key: String) = config.getInt(key) ?: 0

    fun f(key: String) = config.getFloat(key) ?: 0f

    fun b(key: String) = config.getBool(key) ?: false

    fun o(key: String) = config.getItem(key)

    fun sE(key: String, defValue: String) = config.getString(key) ?: defValue

    fun iE(key: String, defValue: Int) = config.getInt(key) ?: defValue

    fun fE(key: String, defValue: Float) = config.getFloat(key) ?: defValue

    fun bE(key: String, defValue: Boolean) = config.getBool(key) ?: defValue

    class Colors(private val theme: Theme) {
        fun getString(key: String): String {
            return CollectionUtils.obtainString(theme.presetColorSchemes!![theme.currentColorSchemeId], key, "")
        }

        // API 2.0
        fun getColor(key: String?): Int? {
            val o = theme.currentColors[key]
            return if (o is Int) o else null
        }

        fun getColor(m: Map<String?, Any?>, key: String?): Int? {
            if (m[key] == null) return null
            return ColorUtils.parseColor(m[key] as String?) ?: getColor(m[key] as String?)
        }

        //  返回drawable。  Config 2.0
        //  参数可以是颜色或者图片。如果参数缺失，返回null
        fun getDrawable(key: String): Drawable? {
            val o = theme.currentColors[key]
            if (o is Int) {
                return GradientDrawable().apply { setColor(o) }
            } else if (o is String) {
                return bitmapDrawable(o)
            }
            return null
        }

        // API 2.0
        fun getDrawable(m: Map<String?, Any?>, key: String): Drawable? {
            m[key] ?: return null
            val value = m[key] as String?
            val override = ColorUtils.parseColor(value)
            if (override != null) {
                return GradientDrawable().apply { setColor(override) }
            }
            return if (theme.currentColors.containsKey(value)) getDrawable(value!!) else getDrawable(key)
        }

        //  返回图片或背景的drawable,支持null参数。 Config 2.0
        fun getDrawable(
            key: String?,
            borderKey: String?,
            borderColorKey: String?,
            roundCornerKey: String,
            alphaKey: String?,
        ): Drawable? {
            if (key == null) return null
            val o = theme.currentColors[key]
            if (o is String) {
                val bitmap = bitmapDrawable(o)
                if (bitmap != null) {
                    if (!alphaKey.isNullOrEmpty() && theme.o("style/$alphaKey") != null) {
                        bitmap.alpha = MathUtils.clamp(theme.i("style/$alphaKey"), 0, 255)
                    }
                    return bitmap
                }
            } else if (o is Int) {
                val gradient = GradientDrawable().apply { setColor(o) }
                if (roundCornerKey.isNotEmpty()) {
                    gradient.cornerRadius = theme.f("style/$roundCornerKey")
                }
                if (!borderColorKey.isNullOrEmpty() && !borderKey.isNullOrEmpty()) {
                    val border = dp2px(theme.f("style/$borderKey"))
                    val stroke = getColor(borderColorKey)
                    if (stroke != null && border > 0) {
                        gradient.setStroke(border.toInt(), stroke)
                    }
                }
                if (!alphaKey.isNullOrEmpty() && theme.o("style/$alphaKey") != null) {
                    gradient.alpha = MathUtils.clamp(theme.i("style/$alphaKey"), 0, 255)
                }
                return gradient
            }
            return null
        }
    }

    // private var oneHandMode = 0

    //  获取当前配色方案的key的value，或者从fallback获取值。
    private fun getColorValue(key: String?): Any? {
        val map = presetColorSchemes?.get(currentColorSchemeId!!) ?: return null
        var value: Any?
        var newKey = key
        val limit = fallbackColors!!.size * 2
        for (i in 0 until limit) {
            value = map[newKey]
            if (value != null || !fallbackColors.containsKey(newKey)) return value
            newKey = fallbackColors[newKey]
        }
        return null
    }

    /**
     * 获取配色方案名
     *
     * 优先级：设置>color_scheme>default
     *
     * 避免直接读取 default
     *
     * @return 首个已配置的主题方案名
     */
    private val colorSchemeName: String
        get() {
            var schemeId = appPrefs.themeAndColor.selectedColor
            if (!presetColorSchemes!!.containsKey(schemeId)) schemeId = s("style/color_scheme") // 主題中指定的配色
            if (!presetColorSchemes.containsKey(schemeId)) schemeId = "default" // 主題中的default配色
            val colorMap = presetColorSchemes[schemeId]
            if (colorMap!!.containsKey("dark_scheme") || colorMap.containsKey("light_scheme")) hasDarkLight = true
            return schemeId
        }

    var hasDarkLight = false
        private set

    /**
     * 获取暗黑模式/明亮模式下配色方案的名称
     *
     * @param darkMode 是否暗黑模式
     * @return 配色方案名称
     */
    private fun getColorSchemeName(darkMode: Boolean): String? {
        var scheme = appPrefs.themeAndColor.selectedColor
        if (!presetColorSchemes!!.containsKey(scheme)) scheme = s("style/color_scheme") // 主題中指定的配色
        if (!presetColorSchemes.containsKey(scheme)) scheme = "default" // 主題中的default配色
        val colorMap = presetColorSchemes[scheme]
        if (darkMode) {
            if (colorMap!!.containsKey("dark_scheme")) {
                return colorMap["dark_scheme"]
            }
        } else {
            if (colorMap!!.containsKey("light_scheme")) {
                return colorMap["light_scheme"]
            }
        }
        return scheme
    }

    private fun joinToFullImagePath(value: String): String {
        val defaultPath = File(userDataDir, "backgrounds/${s("style/background_folder")}/$value")
        if (!defaultPath.exists()) {
            val fallbackPath = File(userDataDir, "backgrounds/$value")
            if (fallbackPath.exists()) return fallbackPath.path
        }
        return defaultPath.path
    }

    fun getPresetColorSchemes(): List<Pair<String, String>> {
        return presetColorSchemes?.entries?.map { (key, value) ->
            Pair(key, value["name"] ?: "")
        } ?: arrayListOf()
    }

    // 初始化当前配色 Config 2.0
    fun initCurrentColors() {
        currentColorSchemeId = colorSchemeName
        Timber.i("Caching color values (currentColorSchemeId=%s) ...", currentColorSchemeId)
        cacheColorValues()
    }

    // 当切换暗黑模式时，刷新键盘配色方案
    fun initCurrentColors(darkMode: Boolean) {
        currentColorSchemeId = getColorSchemeName(darkMode)
        Timber.i(
            "Caching color values (currentColorSchemeId=%s, isDarkMode=%s) ...",
            currentColorSchemeId,
            darkMode,
        )
        cacheColorValues()
    }

    private fun cacheColorValues() {
        currentColors.clear()
        val colorMap = presetColorSchemes!![currentColorSchemeId]
        if (colorMap == null) {
            Timber.w("Color scheme id not found: %s", currentColorSchemeId)
            return
        }
        appPrefs.themeAndColor.selectedColor = currentColorSchemeId!!
        for ((key, value1) in colorMap) {
            if (key == "name" || key == "author") continue
            val value = parseColorValue(value1)
            if (value != null) currentColors[key] = value
        }
        for ((key) in fallbackColors!!) {
            if (!currentColors.containsKey(key)) {
                val value = parseColorValue(getColorValue(key))
                if (value != null) currentColors[key] = value
            }
        }
    }

    // 获取参数的真实value，Config 2.0
    // 如果是色彩返回int，如果是背景图返回path string，如果处理失败返回null
    private fun parseColorValue(value: Any?): Any? {
        value ?: return null
        if (value is String) {
            if (value.matches(".*[.\\\\/].*".toRegex())) {
                val fullPath = joinToFullImagePath(value)
                return if (File(fullPath).exists()) fullPath else ""
            } else {
                runCatching {
                    return ColorUtils.parseColor(value)
                }.getOrElse {
                    Timber.e("Unknown color value $value: ${it.message}")
                }
            }
        }
        return null
    }
}
