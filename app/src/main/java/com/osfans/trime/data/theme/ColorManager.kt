// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.core.math.MathUtils
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.bitmapDrawable
import com.osfans.trime.util.isNightMode
import timber.log.Timber

object ColorManager {
    private lateinit var theme: Theme
    private val prefs = ThemeManager.prefs
    private var normalModeColor by prefs.normalModeColor
    private val followSystemDayNight by prefs.followSystemDayNight
    private val backgroundFolder get() = theme.generalStyle.backgroundFolder

    private var isNightMode = false

    val presetColorSchemes: List<ColorScheme>
        get() = theme.presetColorSchemes

    private val customFallbackRules: Map<String, String>
        get() = theme.fallbackColors

    private val fullFallbackRules get() = customFallbackRules + defaultFallbackColors

    private lateinit var _activeColorScheme: ColorScheme

    var activeColorScheme: ColorScheme
        get() = _activeColorScheme
        set(value) {
            if (_activeColorScheme == value) return
            _activeColorScheme = value
            fireChange()
        }

    private val lightModeColorScheme: ColorScheme? =
        runCatching {
            _activeColorScheme.values["light_scheme"]?.let { colorScheme(it) }
        }.getOrNull()

    private val darkModeColorScheme: ColorScheme? =
        runCatching {
            _activeColorScheme.values["dark_scheme"]?.let { colorScheme(it) }
        }.getOrNull()

    private val defaultFallbackColors =
        mapOf(
            "candidate_text_color" to "text_color",
            "comment_text_color" to "candidate_text_color",
            "border_color" to "back_color",
            "candidate_separator_color" to "border_color",
            "hilited_text_color" to "text_color",
            "hilited_back_color" to "back_color",
            "hilited_candidate_text_color" to "hilited_text_color",
            "hilited_candidate_back_color" to "hilited_back_color",
            "hilited_candidate_button_color" to "hilited_candidate_back_color",
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
            "keyboard_background" to "keyboard_back_color",
            "liquid_keyboard_background" to "keyboard_back_color",
            "text_back_color" to "back_color",
            "long_text_color" to "key_text_color",
            "long_text_back_color" to "key_back_color",
        )

    private val colorCache = LruCache<String, Int>(10)
    private val drawableCache = LruCache<String, Drawable>(10)

    fun interface OnColorChangeListener {
        fun onColorChange(theme: Theme)
    }

    private val onChangeListeners = WeakHashSet<OnColorChangeListener>()

    fun addOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onColorChange(theme) }
    }

    private fun colorScheme(id: String) = presetColorSchemes.find { it.id == id }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        _activeColorScheme = evaluateActiveColorScheme()
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        freeCaches()
        isNightMode = isNight
        activeColorScheme = evaluateActiveColorScheme()
    }

    private fun evaluateActiveColorScheme(): ColorScheme =
        when {
            followSystemDayNight -> if (isNightMode) darkModeColorScheme else lightModeColorScheme
            else -> null
        } ?: colorScheme(normalModeColor) ?: colorScheme("default")!!

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun switchTheme(theme: Theme) {
        freeCaches()
        this.theme = theme
        val newScheme = evaluateActiveColorScheme()
        _activeColorScheme = newScheme
        normalModeColor = newScheme.id
    }

    fun setColorScheme(scheme: ColorScheme) {
        freeCaches()
        activeColorScheme = scheme
        normalModeColor = scheme.id
    }

    private fun freeCaches() {
        colorCache.evictAll()
        drawableCache.evictAll()
    }

    @ColorInt
    fun resolveColor(
        key: String,
        putCache: Boolean = true,
    ): Int {
        val color =
            resolveValue(key) { value ->
                runCatching {
                    ColorUtils.parseColor(value)
                }.getOrElse {
                    Timber.e(it, "Cannot parse color $key=$value, fallback to 0x00")
                    Color.TRANSPARENT
                }
            }
        if (putCache) {
            synchronized(colorCache) { colorCache.put(key, color) }
        }
        return color
    }

    fun resolveDrawable(
        key: String,
        putCache: Boolean = true,
    ): Drawable? {
        val drawable =
            resolveValue(key) { value ->
                if (SUPPORTED_IMG_FORMATS.any { value.endsWith(it) }) {
                    parseFileDrawable(value)
                } else {
                    parseColorDrawable(value)
                }
            }
        if (putCache && drawable != null) {
            synchronized(drawableCache) { drawableCache.put(key, drawable) }
        }
        return drawable
    }

    private inline fun <T> resolveValue(
        key: String,
        parser: (String) -> T,
    ): T {
        var currentKey = key
        val visitedKeys = mutableSetOf<String>()

        while (true) {
            when {
                activeColorScheme.values.containsKey(currentKey) -> {
                    return parser(activeColorScheme.values[currentKey]!!)
                }
                fullFallbackRules.containsKey(currentKey) -> {
                    currentKey =
                        fullFallbackRules[currentKey]!!.also {
                            check(visitedKeys.add(it)) { "Circular fallback: $key" }
                        }
                }
                else -> throw IllegalArgumentException("Color not found: $key")
            }
        }
    }

    private fun parseColorDrawable(value: String): Drawable {
        val color = ColorUtils.parseColor(value)
        return GradientDrawable().apply { setColor(color) }
    }

    private fun parseFileDrawable(value: String): Drawable? {
        val imgPath = resolveImageFilePath(value)
        return bitmapDrawable(imgPath)
    }

    private fun resolveImageFilePath(value: String): String {
        val default = DataManager.userDataDir.resolve("backgrounds/$backgroundFolder/$value")
        if (!default.exists()) {
            val fallback = DataManager.userDataDir.resolve("backgrounds/$value")
            if (fallback.exists()) return fallback.absolutePath
        }
        return default.absolutePath
    }

    @ColorInt
    fun getColor(key: String): Int = colorCache[key] ?: resolveColor(key)

    fun getDrawable(key: String): Drawable? = drawableCache[key] ?: resolveDrawable(key)

    fun getDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? =
        when (val drawable = getDrawable(colorKey)) {
            is BitmapDrawable -> drawable.also { it.alpha = MathUtils.clamp(alpha, 0, 255) }
            is GradientDrawable ->
                drawable.also {
                    it.cornerRadius = cornerRadius
                    it.alpha = MathUtils.clamp(alpha, 0, 255)
                    if (!borderColorKey.isNullOrEmpty()) {
                        try {
                            val borderColor = getColor(borderColorKey)
                            it.setStroke(borderPx, borderColor)
                        } catch (_: Exception) {
                        }
                    }
                }
            else -> null
        }

    private val SUPPORTED_IMG_FORMATS = arrayOf(".png", ".webp", ".jpg", ".gif")
}
