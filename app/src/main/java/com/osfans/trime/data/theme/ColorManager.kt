// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.core.math.MathUtils
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.isNightMode
import timber.log.Timber
import java.io.File

object ColorManager {
    private lateinit var theme: Theme
    private val prefs = ThemeManager.prefs
    private var normalModeColor by prefs.normalModeColor
    private val followSystemDayNight by prefs.followSystemDayNight
    private val backgroundFolder get() = theme.generalStyle.backgroundFolder

    private var isNightMode = false

    private val allFallbackColors get() = theme.fallbackColors + BuiltinFallbackColors

    private lateinit var _activeColorScheme: ColorScheme

    var activeColorScheme: ColorScheme
        get() = _activeColorScheme
        set(value) {
            if (this::_activeColorScheme.isInitialized && _activeColorScheme == value) return
            _activeColorScheme = value
            lightModeColorScheme =
                runCatching {
                    _activeColorScheme["light_scheme"]?.let { colorScheme(it) }
                }.getOrNull()
            darkModeColorScheme =
                runCatching {
                    _activeColorScheme["dark_scheme"]?.let { colorScheme(it) }
                }.getOrNull()
            fireChange()
        }

    private var lightModeColorScheme: ColorScheme? = null

    private var darkModeColorScheme: ColorScheme? = null

    private val BuiltinFallbackColors =
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

    fun colorScheme(id: String) = theme.colorSchemes[id]

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        activeColorScheme = evaluateActiveColorScheme()
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
        } ?: colorScheme(normalModeColor) ?: colorScheme("default")
            ?: throw IllegalArgumentException("Failed to evaluate valid color scheme")

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun switchTheme(theme: Theme) {
        freeCaches()
        this.theme = theme
        val newScheme = evaluateActiveColorScheme()
        activeColorScheme = newScheme
        normalModeColor =
            theme.colorSchemes.entries
                .first { it.value == newScheme }
                .key
    }

    fun setColorScheme(schemeId: String) {
        freeCaches()
        theme.colorSchemes[schemeId]?.let {
            activeColorScheme = it
            normalModeColor = schemeId
        }
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
            try {
                resolveValue(key) { value ->
                    parseColor(value)
                }
            } catch (_: IllegalArgumentException) {
                parseColor(key)
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
            try {
                resolveValue(key) { value ->
                    parseDrawable(value)
                }
            } catch (_: IllegalArgumentException) {
                parseDrawable(key)
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
                activeColorScheme.containsKey(currentKey) -> {
                    return parser(activeColorScheme[currentKey]!!)
                }
                allFallbackColors.containsKey(currentKey) -> {
                    currentKey =
                        allFallbackColors[currentKey]!!.also {
                            check(visitedKeys.add(it)) { "Circular fallback: $key" }
                        }
                }
                else -> throw IllegalArgumentException("Color not found: $key")
            }
        }
    }

    private fun parseColor(value: String): Int =
        try {
            Color.parseColor(value)
        } catch (e: Exception) {
            Timber.w(e, "Cannot parse color $value, fallback to TRANSPARENT")
            Color.TRANSPARENT
        }

    private fun parseDrawable(value: String): Drawable? {
        if (value.isEmpty()) return null
        if (SUPPORTED_IMG_FORMATS.any { value.endsWith(it) }) {
            val path = resolveImageFilePath(value)
            val file = File(path)
            val bitmap = BitmapFactory.decodeStream(file.inputStream()) ?: return null
            if (path.endsWith(".9.png")) {
                val chunk = bitmap.ninePatchChunk
                return if (NinePatch.isNinePatchChunk(chunk)) {
                    // for compiled nine patch image
                    NinePatchDrawable(Resources.getSystem(), bitmap, chunk, Rect(), null)
                } else {
                    // for source nine patch image
                    NinePatchBitmapFactory.createNinePatchDrawable(Resources.getSystem(), bitmap)
                }
            }
            return BitmapDrawable(Resources.getSystem(), bitmap)
        } else {
            val color =
                try {
                    Color.parseColor(value)
                } catch (_: Exception) {
                    return null
                }
            return GradientDrawable().apply { setColor(color) }
        }
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
