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
    private val backgroundFolder get() = theme.generalStyle.backgroundFolder

    private var isNightMode = false

    private var _activeColorScheme: ColorScheme? = null
    private var colorTable: ColorTable? = null
    private var tableTheme: Theme? = null

    val activeColorScheme: ColorScheme
        get() = requireNotNull(_activeColorScheme) { "ColorManager is not initialized" }

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

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        setActiveColorScheme(resolveActiveScheme())

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
        setActiveColorScheme(resolveActiveScheme())
    }

    private fun resolveActiveScheme(): ColorScheme = ColorSchemeResolver.resolve(
        schemes = theme.colorSchemes,
        selectedSchemeId = prefs.normalModeColor.getValue(),
        followSystemDayNight = prefs.followSystemDayNight.getValue(),
        isNightMode = isNightMode,
    )

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun switchTheme(theme: Theme) {
        bitmapCache?.evictAll()
        this.theme = theme
        setActiveColorScheme(resolveActiveScheme())
    }

    fun setColorScheme(scheme: ColorScheme) {
        setActiveColorScheme(scheme)
        prefs.normalModeColor.setValue(scheme.id)
    }

    /**
     * Activates a color scheme and pre-compiles its color table. Listener
     * notification is kept to scheme changes so theme switches (which notify
     * through ThemeManager) do not fire twice.
     */
    private fun setActiveColorScheme(scheme: ColorScheme) {
        val schemeChanged = _activeColorScheme != scheme
        val themeChanged = this::theme.isInitialized && tableTheme !== theme
        if (!schemeChanged && !themeChanged) return
        _activeColorScheme = scheme
        if (this::theme.isInitialized) {
            colorTable = buildColorTable(scheme)
            tableTheme = theme
        }
        if (schemeChanged) fireChange()
    }

    private fun buildColorTable(scheme: ColorScheme): ColorTable = ColorTable.resolve(scheme, theme.fallbackColors) { value ->
        runCatching { ColorUtils.parseColor(value) }.getOrNull()
    }.also { table ->
        if (table.unresolvedKeys.isNotEmpty()) {
            Timber.w("Unknown color key: %s", table.unresolvedKeys.joinToString { it.key })
        }
        if (table.invalidValues.isNotEmpty()) {
            Timber.w("Invalid color value: %s", table.invalidValues.joinToString { it.key })
        }
    }

    @ColorInt
    private fun resolveColor(key: String): Int {
        val tableEntry = ColorKey.from(key)?.let { colorTable?.get(it) }
        if (tableEntry is ColorTable.Value.Color) return tableEntry.argb
        // Keys defined only by a theme resolve through the same chain rules.
        val raw = ColorTable.resolveRaw(key, activeColorScheme.colors, theme.fallbackColors)
        return try {
            if (raw == null) throw IllegalArgumentException("$key not found")
            ColorUtils.parseColor(raw)
        } catch (_: IllegalArgumentException) {
            ColorUtils.parseColor(key)
        }
    }

    private fun resolveDrawable(key: String): Drawable? {
        val tableEntry = ColorKey.from(key)?.let { colorTable?.get(it) }
        if (tableEntry != null) {
            return when (tableEntry) {
                is ColorTable.Value.Color -> GradientDrawable().apply { setColor(tableEntry.argb) }
                is ColorTable.Value.Image -> imageDrawable(tableEntry.path)
                ColorTable.Value.None -> parseDrawable(key)
            }
        }
        // Keys defined only by a theme resolve through the same chain rules.
        val raw = ColorTable.resolveRaw(key, activeColorScheme.colors, theme.fallbackColors)
        return parseDrawable(raw ?: key)
    }

    private fun parseDrawable(value: String): Drawable? {
        if (value.isEmpty()) return null
        if (ColorTable.isImageValue(value)) return imageDrawable(value)
        val color = runCatching { ColorUtils.parseColor(value) }.getOrDefault(Color.TRANSPARENT)
        return GradientDrawable().apply { setColor(color) }
    }

    private fun imageDrawable(value: String): Drawable? {
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
}
