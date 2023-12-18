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
class Theme private constructor(isDarkMode: Boolean) {
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
        private var self: Theme? = null
        private val appPrefs = AppPrefs.defaultInstance()

        @JvmStatic
        fun get(): Theme {
            if (self == null) self = Theme(false)
            return self as Theme
        }

        @JvmStatic
        fun get(isDarkMode: Boolean): Theme {
            if (self == null) self = Theme(isDarkMode)
            return self as Theme
        }

        private val defaultThemeName = "trime"

        fun isImageString(str: String?): Boolean {
            return str?.contains(Regex(".(png|jpg)")) == true
        }
    }

    init {
        self = this
        ThemeManager.init()
        init(isDarkMode)
        Timber.d("Setting sound from color ...")
        SoundThemeManager.switchSound(colors.getString("sound"))
        Timber.d("Initialization finished")
    }

    fun init(isDarkMode: Boolean) {
        val active = ThemeManager.getActiveTheme()
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
                    fullThemeConfigMap = Rime.getRimeConfigMap(defaultThemeName, "")
                }
                Objects.requireNonNull(fullThemeConfigMap, "The theme file cannot be empty!")
                Timber.d("Fetching done")
                generalStyle = fullThemeConfigMap!!["style"] as Map<String, Any?>?
                fallbackColors = fullThemeConfigMap!!["fallback_colors"] as Map<String, String>?
                Key.presetKeys = fullThemeConfigMap!!["preset_keys"] as Map<String?, Map<String?, Any?>?>?
                presetColorSchemes = fullThemeConfigMap!!["preset_color_schemes"] as Map<String, Map<String, Any>?>?
                presetKeyboards = fullThemeConfigMap!!["preset_keyboards"] as Map<String, Any?>?
                liquidKeyboard = fullThemeConfigMap!!["liquid_keyboard"] as Map<String, Any?>?
            }.also { Timber.d("Setting up all theme config map takes $it ms") }
            measureTimeMillis {
                initCurrentColors(isDarkMode)
            }.also { Timber.d("Initializing cache takes $it ms") }
            Timber.i("The theme is initialized")
        }.getOrElse {
            Timber.e("Failed to parse the theme: ${it.message}")
            if (ThemeManager.getActiveTheme() != defaultThemeName) {
                ThemeManager.switchTheme(defaultThemeName)
                init(isDarkMode)
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
            m: Map<String?, Any?>,
            key: String?,
        ): Int? {
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
        fun getDrawable(
            m: Map<String?, Any?>,
            key: String,
        ): Drawable? {
            m[key] ?: return null
            val value = m[key] as String?
            val override = ColorUtils.parseColor(value)
            if (override != null) {
                return GradientDrawable().apply { setColor(override) }
            }

            // value maybe a color label or a image path
            return if (isImageString(value)) {
                val path = theme.getImagePath(value!!)
                if (path.isNotEmpty()) {
                    bitmapDrawable(path)
                } else {
                    // fallback if image not found
                    getDrawable(key)
                }
            } else if (theme.currentColors.containsKey(value)) {
                // use custom color label
                getDrawable(value!!)
            } else {
                // fallback color
                getDrawable(key)
            }
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
            var color = o
            if (o is String) {
                if (isImageString(o)) {
                    val bitmap = bitmapDrawable(o)
                    if (bitmap != null) {
                        if (!alphaKey.isNullOrEmpty() && theme.style.getObject(alphaKey) != null) {
                            bitmap.alpha = MathUtils.clamp(theme.style.getInt(alphaKey), 0, 255)
                        }
                        return bitmap
                    }
                } else {
                    // it is html hex color string (e.g. #ff0000)
                    color = ColorUtils.parseColor(o)
                }
            }

            if (color is Int) {
                val gradient = GradientDrawable().apply { setColor(color) }
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

    fun destroy() {
        self = null
    }

    lateinit var keyboardPadding: IntArray
        private set

    fun getKeyboardPadding(landMode: Boolean): IntArray {
        Timber.i("update KeyboardPadding: getKeyboardPadding(boolean land_mode) ")
        return getKeyboardPadding(oneHandMode, landMode)
    }

    private var oneHandMode = 0

    fun getKeyboardPadding(
        oneHandMode1: Int,
        landMode: Boolean,
    ): IntArray {
        keyboardPadding = IntArray(3)
        this.oneHandMode = oneHandMode1
        if (landMode) {
            keyboardPadding[0] = dp2px(style.getFloat("keyboard_padding_land")).toInt()
            keyboardPadding[1] = keyboardPadding[0]
            keyboardPadding[2] = dp2px(style.getFloat("keyboard_padding_land_bottom")).toInt()
        } else {
            when (oneHandMode1) {
                0 -> {
                    // 普通键盘 预留，目前未实装
                    keyboardPadding[0] = dp2px(style.getFloat("keyboard_padding")).toInt()
                    keyboardPadding[1] = keyboardPadding[0]
                    keyboardPadding[2] = dp2px(style.getFloat("keyboard_padding_bottom")).toInt()
                }

                1 -> {
                    // 左手键盘
                    keyboardPadding[0] = dp2px(style.getFloat("keyboard_padding_left")).toInt()
                    keyboardPadding[1] = dp2px(style.getFloat("keyboard_padding_right")).toInt()
                    keyboardPadding[2] = dp2px(style.getFloat("keyboard_padding_bottom")).toInt()
                }

                2 -> {
                    // 右手键盘
                    keyboardPadding[1] = dp2px(style.getFloat("keyboard_padding_left")).toInt()
                    keyboardPadding[0] = dp2px(style.getFloat("keyboard_padding_right")).toInt()
                    keyboardPadding[2] = dp2px(style.getFloat("keyboard_padding_bottom")).toInt()
                }
            }
        }
        Timber.d(
            "update KeyboardPadding: %s %s %s one_hand_mode=%s",
            keyboardPadding[0],
            keyboardPadding[1],
            keyboardPadding[2],
            oneHandMode1,
        )
        return keyboardPadding
    }

    //  获取当前配色方案的key的value，或者从fallback获取值。
    private fun getColorValue(key: String?): Any? {
        val map = presetColorSchemes!![currentColorSchemeId] ?: return null
        var value: Any?
        var newKey = key
        val limit = fallbackColors!!.size * 2
        for (i in 0 until limit) {
            value = map[newKey]
            if (value != null || !fallbackColors!!.containsKey(newKey)) return value
            newKey = fallbackColors!![newKey]
        }
        return null
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
        if (!presetColorSchemes!!.containsKey(scheme)) scheme = style.getString("color_scheme") // 主題中指定的配色
        if (!presetColorSchemes!!.containsKey(scheme)) scheme = "default" // 主題中的default配色
        val colorMap = presetColorSchemes!![scheme] as Map<String, Any>
        if (colorMap.containsKey("dark_scheme") || colorMap.containsKey("light_scheme")) hasDarkLight = true
        if (darkMode) {
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

        for ((key, value1) in colorMap) {
            if (key == "name" || key == "author" || key == "light_scheme" || key == "dark_scheme") continue
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
                return getImagePath(value)
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

    private fun getImagePath(value: String): String {
        return if (value.matches(".*[.\\\\/].*".toRegex())) {
            val fullPath = joinToFullImagePath(value)
            if (File(fullPath).exists()) fullPath else ""
        } else {
            ""
        }
    }
}
