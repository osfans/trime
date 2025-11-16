// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.isNightMode
import timber.log.Timber

object ColorManager {
    private lateinit var theme: Theme
    private val prefs = ThemeManager.prefs
    private var normalModeColor by prefs.normalModeColor
    private val followSystemDayNight by prefs.followSystemDayNight
    private val backgroundFolder get() = theme.generalStyle.backgroundFolder

    private var isNightMode = false

    // 添加一个变量来保存用户实际选择的配色方案ID
    private var userSelectedColorSchemeId: String? = null

    private lateinit var _activeColorScheme: ColorScheme

    var activeColorScheme: ColorScheme
        get() = _activeColorScheme
        set(value) {
            if (this::_activeColorScheme.isInitialized && _activeColorScheme == value) return
            _activeColorScheme = value
            lightModeColorScheme =
                runCatching {
                    _activeColorScheme.colors["light_scheme"]?.let { colorScheme(it) }
                }.getOrNull()
            darkModeColorScheme =
                runCatching {
                    _activeColorScheme.colors["dark_scheme"]?.let { colorScheme(it) }
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
            "popup_back_color" to "key_back_color",
            "popup_text_color" to "key_text_color",
            "hilited_popup_back_color" to "hilited_key_back_color",
            "hilited_popup_text_color" to "hilited_key_text_color",
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

    private var bitmapCache: LruCache<String, Bitmap>? = null

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

    private fun colorScheme(id: String) = theme.colorSchemes.find { it.id == id }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        // 初始化时保存用户的实际配色选择
        userSelectedColorSchemeId = normalModeColor
        activeColorScheme = evaluateActiveColorScheme()

        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val cacheSize = maxMemory / 8
        bitmapCache =
            object : LruCache<String, Bitmap>(cacheSize.toInt()) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = value.byteCount / 1024
            }
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        isNightMode = isNight

        // 获取当前用户选择的基础配色
        val userBaseSchemeId = userSelectedColorSchemeId ?: normalModeColor
        val userBaseScheme = colorScheme(userBaseSchemeId)

        // 如果用户有选择基础配色，先确保activeColorScheme被设置为该配色
        if (userBaseScheme != null && _activeColorScheme.id != userBaseScheme.id) {
            activeColorScheme = userBaseScheme
        }

        // 然后再根据夜间模式设置选择对应的配色
        activeColorScheme = evaluateActiveColorScheme()
    }

    private fun evaluateActiveColorScheme(): ColorScheme {
        if (followSystemDayNight) {
            // 先获取用户选择的基础配色方案
            val userBaseScheme = colorScheme(userSelectedColorSchemeId ?: normalModeColor)
                ?: colorScheme("default")
                ?: theme.colorSchemes.first()

            // 确保activeColorScheme被设置为用户基础配色，这样可以正确初始化lightModeColorScheme和darkModeColorScheme
            if (!this::_activeColorScheme.isInitialized || _activeColorScheme.id != userBaseScheme.id) {
                activeColorScheme = userBaseScheme
            }

            // 然后根据当前模式选择对应的配色
            val targetScheme = if (isNightMode) darkModeColorScheme else lightModeColorScheme
            // 如果有对应的明暗模式配色，则使用它；否则使用用户选择的基础配色
            if (targetScheme != null) return targetScheme
        }
        // 当不跟随系统或没有对应的明暗模式配色时，使用用户选择的配色
        return colorScheme(userSelectedColorSchemeId ?: normalModeColor)
            ?: colorScheme("default")
            ?: theme.colorSchemes.first()
    }

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun switchTheme(theme: Theme) {
        bitmapCache?.evictAll()
        this.theme = theme

        // 确定要使用的配色方案ID
        val targetSchemeId = when {
            // 优先使用用户明确选择的配色
            userSelectedColorSchemeId != null -> userSelectedColorSchemeId
            // 如果没有明确选择，尝试使用保存的normalModeColor
            else -> normalModeColor
        }

        // 尝试在新主题中找到对应的配色方案
        var newScheme = targetSchemeId?.let { colorScheme(it) }

        // 如果找不到对应的配色，使用默认配色
        if (newScheme == null) {
            newScheme = colorScheme("default") ?: theme.colorSchemes.first()
            // 更新用户选择的配色方案ID
            userSelectedColorSchemeId = newScheme.id
        } else {
            // 确保userSelectedColorSchemeId被设置
            if (userSelectedColorSchemeId == null) {
                userSelectedColorSchemeId = targetSchemeId
            }
        }

        // 无论如何都更新normalModeColor，确保持久化存储
        normalModeColor = userSelectedColorSchemeId ?: newScheme.id

        // 设置当前激活的配色方案
        activeColorScheme = newScheme

        // 检查是否需要根据当前的夜间模式调整配色
        if (followSystemDayNight) {
            activeColorScheme = evaluateActiveColorScheme()
        }
    }

    fun setColorScheme(scheme: ColorScheme) {
        // 用户手动选择配色时，保存到userSelectedColorSchemeId和normalModeColor
        userSelectedColorSchemeId = scheme.id
        normalModeColor = scheme.id
        activeColorScheme = scheme
    }

    @ColorInt
    private fun resolveColor(key: String): Int {
        val color =
            try {
                resolveValue(key) { value ->
                    ColorUtils.parseColor(value)
                }
            } catch (_: IllegalArgumentException) {
                ColorUtils.parseColor(key)
            }
        return color
    }

    private fun resolveDrawable(key: String): Drawable? {
        val drawable =
            try {
                resolveValue(key) { value ->
                    parseDrawable(value)
                }
            } catch (_: IllegalArgumentException) {
                parseDrawable(key)
            }
        return drawable
    }

    private inline fun <T> resolveValue(
        key: String,
        parser: (String) -> T,
    ): T {
        var currentKey = key

        while (true) {
            val target = activeColorScheme.colors[currentKey]
            if (!target.isNullOrEmpty()) {
                Timber.d("current: $currentKey, origin: $key, target: $target")
                return parser(target)
            }
            val fallback = theme.fallbackColors[currentKey]
            if (!fallback.isNullOrEmpty()) {
                currentKey = fallback
                continue
            }
            val altFallback = BuiltinFallbackColors[currentKey]
            if (!altFallback.isNullOrEmpty()) {
                currentKey = altFallback
            } else {
                throw IllegalArgumentException("$key not found")
            }
        }
    }

    private fun parseDrawable(value: String): Drawable? {
        if (value.isEmpty()) return null
        if (SUPPORTED_IMG_FORMATS.any { value.endsWith(it) }) {
            val path = resolveImageFilePath(value)
            val bitmap =
                bitmapCache?.get(path)
                    ?: BitmapFactory.decodeFile(path)?.also {
                        bitmapCache?.put(path, it)
                    } ?: return null
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
            return bitmap.toDrawable(Resources.getSystem())
        } else {
            val color =
                try {
                    ColorUtils.parseColor(value)
                } catch (_: Exception) {
                    Color.TRANSPARENT
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
    fun getColor(key: String): Int = resolveColor(key)

    fun getDrawable(key: String): Drawable? = resolveDrawable(key)

    fun getDecorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = when (val drawable = getDrawable(colorKey)) {
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
        else -> drawable?.also { it.alpha = MathUtils.clamp(alpha, 0, 255) }
    }

    private val SUPPORTED_IMG_FORMATS = arrayOf(".png", ".webp", ".jpg", ".gif")
}
