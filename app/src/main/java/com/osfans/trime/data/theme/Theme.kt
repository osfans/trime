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
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.sound.SoundThemeManager
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.util.CollectionUtils
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.bitmapDrawable
import com.osfans.trime.util.dp2px
import timber.log.Timber
import java.io.File
import java.util.Objects
import kotlin.system.measureTimeMillis

/** 主题和样式配置  */
class Theme(private var isDarkMode: Boolean) {
    private var currentColorSchemeId: String? = null
    private var generalStyle: Map<String, Any?>? = null
    private var fallbackColors: Map<String, String>? = null
    private var presetColorSchemes: Map<String, Map<String, Any>?>? = null
    private var presetKeyboards: Map<String, Any?>? = null
    private var liquidKeyboard: Map<String, Any?>? = null

    // 遍历当前配色方案的值、fallback的值，从而获得当前方案的全部配色Map
    private val currentColors: MutableMap<String, Any> = hashMapOf()

    @JvmField
    val style = Style(this)

    @JvmField
    val liquid = Liquid(this)

    @JvmField
    val colors = Colors(this)

    @JvmField
    val keyboards = Keyboards(this)

    companion object {
        private const val VERSION_KEY = "config_version"
        private val appPrefs = AppPrefs.defaultInstance()

        private const val DEFAULT_THEME_NAME = "trime"

        fun isFileString(str: String?): Boolean {
            return str?.contains(Regex("""\.[a-z]{3,4}$""")) == true
        }
    }

    init {
        init()
        Timber.d("Setting sound from color ...")
        SoundThemeManager.switchSound(colors.getString("sound"))
        Timber.d("Initialization finished")
    }

    fun init() {
        val active = appPrefs.themeAndColor.selectedTheme
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
                fallbackColors = fullThemeConfigMap!!["fallback_colors"] as Map<String, String>?
                Key.presetKeys = fullThemeConfigMap!!["preset_keys"] as Map<String, Map<String, Any?>?>?
                presetColorSchemes = fullThemeConfigMap!!["preset_color_schemes"] as Map<String, Map<String, Any>?>?
                presetKeyboards = fullThemeConfigMap!!["preset_keyboards"] as Map<String, Any?>?
                liquidKeyboard = fullThemeConfigMap!!["liquid_keyboard"] as Map<String, Any?>?
            }.also { Timber.d("Setting up all theme config map takes $it ms") }
            measureTimeMillis {
                refreshColorCaches(isDarkMode)
            }.also { Timber.d("Initializing cache takes $it ms") }
            Timber.i("The theme is initialized")
        }.getOrElse {
            Timber.e("Failed to parse the theme: ${it.message}")
            if (appPrefs.themeAndColor.selectedTheme != DEFAULT_THEME_NAME) {
                ThemeManager.switchTheme(DEFAULT_THEME_NAME)
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

    class Colors(private val theme: Theme) {
        fun getString(key: String): String {
            return CollectionUtils.obtainString(theme.presetColorSchemes, key, "")
        }

        // API 2.0
        fun getColor(key: String?): Int? {
            val o = theme.currentColors[key]
            return if (o is Int) o else null
        }

        fun getColor(
            m: Map<String, Any?>,
            key: String?,
        ): Int? {
            var value = theme.getColorValue(m[key] as String?)
            // 回退到配色
            if (value == null) value = theme.currentColors[key]
            return if (value is Int) value else null
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
        fun getDrawable(
            m: Map<String, Any?>,
            key: String,
        ): Drawable? {
            var value = theme.getColorValue(m[key] as String?)
            // 回退到配色
            if (value == null) value = theme.currentColors[key]
            if (value is Int) {
                return GradientDrawable().apply { setColor(value) }
            } else if (value is String) {
                return bitmapDrawable(value)
            }
            return null
        }

        //  返回图片或背景的drawable,支持null参数。 Config 2.0
        fun getDrawable(
            key: String?,
            borderKey: String?,
            borderColorKey: String?,
            roundCornerKey: String,
            alphaKey: String?,
        ): Drawable? {
            val value = theme.getColorValue(key)
            if (value is String) {
                val bitmap = bitmapDrawable(value)
                if (bitmap != null) {
                    if (!alphaKey.isNullOrEmpty() && theme.style.getObject(alphaKey) != null) {
                        bitmap.alpha = MathUtils.clamp(theme.style.getInt(alphaKey), 0, 255)
                    }
                    return bitmap
                }
            }

            if (value is Int) {
                val gradient = GradientDrawable().apply { setColor(value) }
                if (roundCornerKey.isNotEmpty()) {
                    gradient.cornerRadius = theme.style.getFloat(roundCornerKey)
                }
                if (!borderColorKey.isNullOrEmpty() && !borderKey.isNullOrEmpty()) {
                    val border = dp2px(theme.style.getFloat(borderKey))
                    val stroke = getColor(borderColorKey)
                    if (stroke != null && border > 0) {
                        gradient.setStroke(border.toInt(), stroke)
                    }
                }
                if (!alphaKey.isNullOrEmpty() && theme.style.getObject(alphaKey) != null) {
                    gradient.alpha = MathUtils.clamp(theme.style.getInt(alphaKey), 0, 255)
                }
                return gradient
            }
            return null
        }
    }

    class Keyboards(private val theme: Theme) {
        fun getObject(key: String): Any? {
            return CollectionUtils.obtainValue(theme.presetKeyboards, key)
        }

        fun remapKeyboardId(name: String): String {
            val remapped =
                if (".default" == name) {
                    val currentSchemaId = Rime.getCurrentRimeSchema()
                    if (theme.presetKeyboards!!.containsKey(currentSchemaId)) {
                        return currentSchemaId
                    } else {
                        val alphabet = SchemaManager.getActiveSchema().alphabet
                        val twentySix = "qwerty"
                        if (!alphabet.isNullOrEmpty() && theme.presetKeyboards!!.containsKey(alphabet)) {
                            return alphabet
                        } else {
                            if (!alphabet.isNullOrEmpty() && (alphabet.contains(",") || alphabet.contains(";"))) {
                                twentySix + "_"
                            } else if (!alphabet.isNullOrEmpty() && (alphabet.contains("0") || alphabet.contains("1"))) {
                                twentySix + "0"
                            } else {
                                twentySix
                            }
                        }
                    }
                } else {
                    name
                }
            if (!theme.presetKeyboards!!.containsKey(remapped)) {
                Timber.w("Cannot find keyboard definition %s, fallback ...", remapped)
                val defaultMap =
                    theme.presetKeyboards!!["default"] as Map<String, Any>?
                        ?: throw IllegalStateException("The default keyboard definition is missing!")
                if (defaultMap.containsKey("import_preset")) {
                    return defaultMap["import_preset"] as? String ?: "default"
                } else {
                    return "default"
                }
            }
            return remapped
        }
    }

    var hasDarkLight = false
        private set

    /**
     * 获取暗黑模式/明亮模式下配色方案的名称
     *
     * @param isDarkMode 是否暗黑模式
     * @return 配色方案名称
     */
    private fun getColorSchemeName(): String? {
        var scheme = appPrefs.themeAndColor.selectedColor
        if (!presetColorSchemes!!.containsKey(scheme)) scheme = style.getString("color_scheme") // 主題中指定的配色
        if (!presetColorSchemes!!.containsKey(scheme)) scheme = "default" // 主題中的default配色
        val colorMap = presetColorSchemes!![scheme] as Map<String, Any>
        if (colorMap.containsKey("dark_scheme") || colorMap.containsKey("light_scheme")) hasDarkLight = true
        if (isDarkMode) {
            if (colorMap.containsKey("dark_scheme")) {
                return colorMap["dark_scheme"] as String?
            }
        } else {
            if (colorMap.containsKey("light_scheme")) {
                return colorMap["light_scheme"] as String?
            }
        }
        return scheme
    }

    private fun joinToFullImagePath(value: String): String {
        val defaultPath = File(userDataDir, "backgrounds/${style.getString("background_folder")}/$value")
        if (!defaultPath.exists()) {
            val fallbackPath = File(userDataDir, "backgrounds/$value")
            if (fallbackPath.exists()) return fallbackPath.path
        }
        return defaultPath.path
    }

    fun getPresetColorSchemes(): List<Pair<String, String>> {
        return if (presetColorSchemes == null) {
            arrayListOf()
        } else {
            presetColorSchemes!!.map { (key, value) ->
                Pair(key, value!!["name"] as String)
            }
        }
    }

    // 当切换暗黑模式时，刷新键盘配色方案
    fun refreshColorCaches(isDarkMode: Boolean) {
        this.isDarkMode = isDarkMode
        currentColorSchemeId = getColorSchemeName()
        Timber.d(
            "Caching color values (currentColorSchemeId=$currentColorSchemeId, isDarkMode=$isDarkMode) ...",
        )
        refreshColorValues()
    }

    private fun refreshColorValues() {
        currentColors.clear()
        val colorMap = presetColorSchemes!![currentColorSchemeId]
        if (colorMap == null) {
            Timber.w("Color scheme id not found: %s", currentColorSchemeId)
            return
        }

        for ((key, value) in colorMap) {
            if (key == "name" || key == "author" || key == "light_scheme" || key == "dark_scheme") continue
            currentColors[key] = value
        }
        for ((key, value) in fallbackColors!!) {
            if (!currentColors.containsKey(key)) {
                currentColors[key] = value
            }
        }

        for ((key, value) in currentColors) {
            val parsedValue = getColorValue(value)
            if (parsedValue != null) {
                currentColors[key] = parsedValue
            } else {
                Timber.w("Cannot parse color key: %s, value: %s", key, value)
            }
        }
    }

    // 处理 value 值，转为颜色(Int)或图片path string 或者 fallback
    // 处理失败返回 null
    private fun getColorValue(value: Any?): Any? {
        val parsedValue = parseColorValue(value)
        if (parsedValue != null) {
            return parsedValue
        }
        var newKey = value
        var newValue: Any?
        val limit = currentColors.size
        for (i in 0 until limit) {
            newValue = currentColors[newKey]
            if (newValue == null) return null
            if (newValue is Int) return newValue
            if (isFileString(newValue as String)) return newValue

            val parsedNewValue = parseColorValue(newValue)
            if (parsedNewValue != null) {
                return parsedNewValue
            }
            newKey = newValue
        }
        return null
    }

    // 获取参数的真实value，Config 2.0
    // 如果是色彩返回int，如果是背景图返回path string，如果处理失败返回null
    private fun parseColorValue(value: Any?): Any? {
        if (value is String) {
            if (isFileString(value)) {
                // 获取图片的真实地址
                val fullPath = joinToFullImagePath(value)
                if (File(fullPath).exists()) {
                    return fullPath
                }
            }
            // 不包含下划线才进行颜色解析
            if (!value.contains("_")) {
                return ColorUtils.parseColor(value)
            }
        }
        return null
    }
}
