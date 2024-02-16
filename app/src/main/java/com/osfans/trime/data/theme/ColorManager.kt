package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.math.MathUtils
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.data.sound.SoundEffectManager
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.bitmapDrawable
import com.osfans.trime.util.dp2px
import com.osfans.trime.util.isNightMode
import timber.log.Timber
import java.io.File
import java.lang.IllegalArgumentException

object ColorManager {
    private val theme get() = ThemeManager.activeTheme
    private val prefs = AppPrefs.defaultInstance().theme
    private val backgroundFolder get() = theme.style.getString("background_folder")

    var selectedColor = "default" // 当前配色 id
    private var lastLightColorSchemeId: String? = null // 上一个 light 配色
    private var lastDarkColorSchemeId: String? = null // 上一个 dark 配色
    private var isNightMode = false

    // 遍历当前配色方案的值、fallback的值，从而获得当前方案的全部配色Map
    private val currentColors: MutableMap<String, Any> = hashMapOf()

    /** 获取当前主题所有配色 */
    fun getPresetColorSchemes(): List<Pair<String, String>> {
        return theme.presetColorSchemes.map { (key, value) ->
            Pair(key, value!!["name"] as String)
        }
    }

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
        ThemeManager.init()
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        if (isNightMode == isNight) return
        isNightMode = isNight
        switchNightMode(isNightMode)
    }

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun refresh() {
        lastDarkColorSchemeId = null
        lastLightColorSchemeId = null

        var colorScheme = prefs.selectedColor
        if (!theme.presetColorSchemes.containsKey(colorScheme)) colorScheme = theme.style.getString("color_scheme") // 主題中指定的配色
        if (!theme.presetColorSchemes.containsKey(colorScheme)) colorScheme = "default" // 主題中的default配色
        // 配色表中没有这个 id
        if (!theme.presetColorSchemes.containsKey(colorScheme)) {
            Timber.e("Color scheme %s not found", colorScheme)
            throw IllegalArgumentException("Color scheme $colorScheme not found!")
        }
        selectedColor = colorScheme
        switchNightMode(isNightMode)
    }

    /**
     * 切换到指定配色，切换成功后写入 AppPrefs
     * @param colorSchemeId 配色 id
     * */
    fun setColorScheme(colorSchemeId: String) {
        if (!theme.presetColorSchemes.containsKey(colorSchemeId)) {
            Timber.w("Color scheme %s not found", colorSchemeId)
            return
        }
        Timber.d("switch color scheme from %s to %s", selectedColor, colorSchemeId)
        selectedColor = colorSchemeId
        // 刷新配色
        refreshColorValues()
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
        prefs.selectedColor = colorSchemeId
        fireChange()
    }

    /** 切换深色/亮色模式 */
    private fun switchNightMode(isNightMode: Boolean) {
        this.isNightMode = isNightMode
        val newId = getColorSchemeId()
        if (newId != null) setColorScheme(newId)
        Timber.d(
            "System changing color, current ColorScheme: $selectedColor, isDarkMode=$isNightMode",
        )
    }

    /**
     * @return 切换深色/亮色模式后配色的 id
     */
    private fun getColorSchemeId(): String? {
        val colorMap = theme.presetColorSchemes[selectedColor] as Map<String, Any>
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

    private fun refreshColorValues() {
        currentColors.clear()
        val colorMap = theme.presetColorSchemes[selectedColor] as Map<String, Any>
        for ((key, value) in colorMap) {
            when (key) {
                "name", "author", "light_scheme", "dark_scheme", "sound" -> continue
                else -> currentColors[key] = value
            }
        }
        for ((key, value) in theme.fallbackColors!!) {
            if (!currentColors.containsKey(key)) {
                if (value != null) currentColors[key] = value
            }
        }

        // 先遍历一次，处理一下颜色和图片
        // 防止回退时获取到错误值
        for ((key, value) in currentColors) {
            val parsedValue = parseColorValue(value)
            if (parsedValue != null) {
                currentColors[key] = parsedValue
            }
        }

        // fallback
        for ((key, value) in currentColors) {
            if (value is Int || value is Drawable) continue
            val parsedValue = getColorValue(value)
            if (parsedValue != null) {
                currentColors[key] = parsedValue
            } else {
                Timber.w("Cannot parse color key: %s, value: %s", key, value)
            }
        }

        // sound
        if (colorMap.containsKey("sound")) currentColors["sound"] = colorMap["sound"]!!
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

    private fun isFileString(str: String?): Boolean {
        return str?.contains(Regex("""\.[a-z]{3,4}$""")) == true
    }

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
        val value = getColorValue(m[key] as String?)
        return if (value is Int) value else null
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
        val value = getColorValue(m[key] as String?)
        if (value is Int) {
            return GradientDrawable().apply { setColor(value) }
        } else if (value is Drawable) {
            return value
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
        val value = getColorValue(key)
        if (value is Drawable) {
            if (!alphaKey.isNullOrEmpty() && theme.style.getObject(alphaKey) != null) {
                value.alpha = MathUtils.clamp(theme.style.getInt(alphaKey), 0, 255)
            }
            return value
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
