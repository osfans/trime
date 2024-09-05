// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.math.MathUtils
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.sound.SoundEffectManager
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.appContext
import com.osfans.trime.util.bitmapDrawable
import com.osfans.trime.util.isNightMode
import splitties.dimensions.dp
import timber.log.Timber
import java.io.File

object ColorManager {
    private val theme get() = ThemeManager.activeTheme
    private val prefs = AppPrefs.defaultInstance().theme
    private val backgroundFolder get() = theme.generalStyle.backgroundFolder

    var selectedColor = "default" // 当前配色 id
    private var lastLightColorSchemeId: String? = null // 上一个 light 配色
    private var lastDarkColorSchemeId: String? = null // 上一个 dark 配色
    private var isNightMode = false

    private val defaultFallbackColors =
        hashMapOf(
            "candidate_text_color" to "text_color",
            "comment_text_color" to "candidate_text_color",
            "border_color" to "back_color",
            "candidate_separator_color" to "border_color",
            "hilited_text_color" to "text_color",
            "hilited_back_color" to "back_color",
            "hilited_candidate_text_color" to "hilited_text_color",
            "hilited_candidate_back_color" to "hilited_back_color",
            "hilited_label_color" to "hilited_candidate_text_color",
            "hilited_comment_text_color" to "comment_text_color",
            "hilited_key_back_color" to "hilited_candidate_back_color",
            "hilited_key_text_color" to "hilited_candidate_text_color",
            "hilited_key_symbol_color" to "hilited_comment_text_color",
            "hilited_off_key_back_color" to "hilited_key_back_color",
            "hilited_on_key_back_color" to "hilited_key_back_color",
            "hilited_off_key_text_color" to "hilited_key_text_color",
            "hilited_on_key_text_color" to "hilited_key_text_color",
            "key_back_color" to "back_color",
            "key_border_color" to "border_color",
            "key_text_color" to "candidate_text_color",
            "key_symbol_color" to "comment_text_color",
            "label_color" to "candidate_text_color",
            "off_key_back_color" to "key_back_color",
            "off_key_text_color" to "key_text_color",
            "on_key_back_color" to "hilited_key_back_color",
            "on_key_text_color" to "hilited_key_text_color",
            "preview_back_color" to "key_back_color",
            "preview_text_color" to "key_text_color",
            "shadow_color" to "border_color",
            "root_background" to "back_color",
            "candidate_background" to "back_color",
            "keyboard_back_color" to "border_color",
            "liquid_keyboard_background" to "keyboard_back_color",
            "text_back_color" to "back_color",
            "long_text_back_color" to "key_back_color",
        )

    // 遍历当前配色方案的值、fallback的值，从而获得当前方案的全部配色Map
    private val currentColors: MutableMap<String, Any> = hashMapOf()

    /** 获取当前主题所有配色 */
    val presetColorSchemes get() =
        theme.presetColorSchemes?.entries?.associate { (k, v) ->
            k to v!!.configMap.entries.associate { (s, n) -> s to n!!.configValue.getString() }
        } ?: mapOf()

    fun interface OnColorChangeListener {
        fun onColorChange()
    }

    private val onChangeListeners = WeakHashSet<OnColorChangeListener>()

    fun addOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onColorChange() }
    }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        runCatching {
            ThemeManager.init()
        }.getOrElse {
            Timber.e(it, "Setting up theme failed!")
        }
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        if (!prefs.autoDark) return
        if (isNightMode == isNight) return
        isNightMode = isNight
        switchNightMode(isNightMode)
    }

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun refresh() {
        lastDarkColorSchemeId = null
        lastLightColorSchemeId = null

        val selected = prefs.selectedColor
        val fromStyle = theme.generalStyle.colorScheme
        val default = "default" // 主題中的 default 配色

        selectedColor = arrayOf(selected, fromStyle, default)
            .firstOrNull { presetColorSchemes.containsKey(it) }
            ?: throw NoSuchElementException("No such color scheme found!")

        switchNightMode(isNightMode)
    }

    /**
     * 切换到指定配色后，写入 AppPrefs
     * @param colorSchemeId 配色 id
     * */
    fun setColorScheme(colorSchemeId: String) {
        switchColorScheme(colorSchemeId)
        selectedColor = colorSchemeId
        prefs.selectedColor = colorSchemeId
    }

    /**
     * 切换到指定配色
     * @param colorSchemeId 配色 id
     * */
    private fun switchColorScheme(colorSchemeId: String) {
        if (!presetColorSchemes.containsKey(colorSchemeId)) {
            Timber.w("Color scheme %s not found", colorSchemeId)
            return
        }
        Timber.d("switch color scheme from %s to %s", selectedColor, colorSchemeId)
        // 刷新配色
        val isFirst = currentColors.isEmpty()
        refreshColorValues(colorSchemeId)
        if (isNightMode) {
            lastDarkColorSchemeId = colorSchemeId
        } else {
            lastLightColorSchemeId = colorSchemeId
        }
        // 切换音效
        currentColors["sound"].let {
            if (it is String) {
                Timber.d("Setting sound from color ...")
                SoundEffectManager.switchSound(it)
                Timber.d("Initialization finished")
            }
        }
        if (!isFirst) fireChange()
    }

    /** 切换深色/亮色模式 */
    private fun switchNightMode(isNightMode: Boolean) {
        this.isNightMode = isNightMode
        val newId = getColorSchemeId()
        if (newId != null) switchColorScheme(newId)
        Timber.d(
            "System changing color, current ColorScheme: $selectedColor, isDarkMode=$isNightMode",
        )
    }

    /**
     * @return 切换深色/亮色模式后配色的 id
     */
    private fun getColorSchemeId(): String? {
        val colorMap = presetColorSchemes[selectedColor] as Map<String, Any>
        if (isNightMode) {
            if (colorMap.containsKey("dark_scheme")) {
                return colorMap["dark_scheme"] as String?
            }
            if (lastDarkColorSchemeId != null) {
                return lastDarkColorSchemeId
            }
        } else {
            if (colorMap.containsKey("light_scheme")) {
                return colorMap["light_scheme"] as String?
            }
            if (lastLightColorSchemeId != null) {
                return lastLightColorSchemeId
            }
        }
        return selectedColor
    }

    private fun refreshColorValues(colorSchemeId: String) {
        currentColors.clear()
        val colorMap = presetColorSchemes[colorSchemeId]
        colorMap?.forEach { (key, value) ->
            when (key) {
                "name", "author", "light_scheme", "dark_scheme", "sound" -> {}
                else -> currentColors[key] = value
            }
        }
        theme.fallbackColors?.forEach { (key, value) ->
            if (!currentColors.containsKey(key)) {
                if (value != null) currentColors[key] = value.configValue.getString()
            }
        }
        defaultFallbackColors.forEach { (key, value) ->
            if (!currentColors.containsKey(key)) {
                currentColors[key] = value
            }
        }

        // 先遍历一次，处理一下颜色和图片
        // 防止回退时获取到错误值
        currentColors.forEach { (key, value) ->
            val parsedValue = parseColorValue(value)
            if (parsedValue != null) {
                currentColors[key] = parsedValue
            }
        }

        // fallback
        currentColors.forEach { (key, value) ->
            if (value is Int || value is Drawable) return@forEach
            val parsedValue = getColorValue(value)
            if (parsedValue != null) {
                currentColors[key] = parsedValue
            } else {
                Timber.w("Cannot parse color key: %s, value: %s", key, value)
            }
        }

        // sound
        if (colorMap?.containsKey("sound") == true) currentColors["sound"] = colorMap["sound"]!!
    }

    /** 获取参数的真实value，如果是色彩返回int，如果是背景图返回drawable，都不是则进行 fallback
     * @return Int/Drawable/null
     * */
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
            if (newValue !is String) return newValue
            // fallback
            val parsedNewValue = parseColorValue(newValue)
            if (parsedNewValue != null) {
                return parsedNewValue
            }
            newKey = newValue
        }
        return null
    }

    /** 获取参数的真实value，如果是色彩返回int，如果是背景图返回drawable，如果处理失败返回null
     * @return Int/Drawable/null
     * */
    private fun parseColorValue(value: Any?): Any? {
        if (value !is String) return null
        if (isFileString(value)) {
            // 获取图片的真实地址
            val fullPath = joinToFullImagePath(value)
            if (File(fullPath).exists()) {
                return bitmapDrawable(fullPath)
            }
        }
        // 只对不包含下划线的字符串进行颜色解析
        if (!value.contains("_")) {
            return ColorUtils.parseColor(value)
        }
        return null
    }

    private fun joinToFullImagePath(value: String): String {
        val defaultPath = File(DataManager.userDataDir, "backgrounds/$backgroundFolder/$value")
        if (!defaultPath.exists()) {
            val fallbackPath = File(DataManager.userDataDir, "backgrounds/$value")
            if (fallbackPath.exists()) return fallbackPath.path
        }
        return defaultPath.path
    }

    private fun isFileString(str: String?): Boolean = str?.contains(Regex("""\.[a-z]{3,4}$""")) == true

    // API 2.0
    @ColorInt
    fun getColor(key: String?): Int? {
        val o = currentColors[key]
        return if (o is Int) o else null
    }

    @ColorInt
    fun getColor(
        m: Map<String, Any?>,
        key: String?,
    ): Int? {
        m[key].let {
            if (it == null) return null
            val value = getColorValue(it.toString())
            return if (value is Int) value else null
        }
    }

    //  返回drawable。  Config 2.0
    //  参数可以是颜色或者图片。如果参数缺失，返回null
    fun getDrawable(key: String): Drawable? {
        val o = currentColors[key]
        if (o is Int) {
            return GradientDrawable().apply { setColor(o) }
        } else if (o is Drawable) {
            return o
        }
        return null
    }

    // API 2.0
    fun getDrawable(
        m: Map<String, Any?>,
        key: String,
    ): Drawable? {
        m[key].let {
            if (it == null) return null
            val value = getColorValue(it.toString())
            if (value is Int) {
                return GradientDrawable().apply { setColor(value) }
            } else if (value is Drawable) {
                return value
            }
            return null
        }
    }

    //  返回图片或背景的drawable,支持null参数。 Config 2.0
    fun getDrawable(
        context: Context = appContext,
        key: String,
        border: Int = 0,
        borderColorKey: String = "",
        roundCorner: Int = 0,
        alpha: Int = 255,
    ): Drawable? {
        val value = getColorValue(key)
        if (value is Drawable) {
            value.alpha = MathUtils.clamp(alpha, 0, 255)
            return value
        }

        if (value is Int) {
            val gradient = GradientDrawable().apply { setColor(value) }
            if (roundCorner > 0) {
                gradient.cornerRadius = roundCorner.toFloat()
            }
            if (borderColorKey.isNotEmpty() && border > 0) {
                val borderPx = context.dp(border)
                val stroke = getColor(borderColorKey)
                if (stroke != null && borderPx > 0) {
                    gradient.setStroke(borderPx, stroke)
                }
            }
            gradient.alpha = MathUtils.clamp(alpha, 0, 255)
            return gradient
        }
        return null
    }
}
