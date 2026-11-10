// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.isNightMode
import java.io.File

/**
 * Global entry point for the colors and drawables of the active theme.
 *
 * The activation state itself lives in the current [ThemeScope], which
 * [attachTheme] replaces on every theme switch; scheme switches update the
 * same scope in place. String-keyed lookups below ([getColor], [getDrawable])
 * resolve through that scope so that keys only a theme can define (per-key
 * keyboard colors, image-valued entries) keep working, while [activeColorScheme]
 * and the scope's [ThemeScope.colors] expose the typed view for UI code.
 */
object ColorManager {
    /** Lower bound for [bitmapCache] in KiB, so the background still fits on small heaps. */
    private const val MIN_BITMAP_CACHE_SIZE = 8 * 1024

    private var scope: ThemeScope? = null
    private val prefs = ThemeManager.prefs

    private var isNightMode = false

    val activeColorScheme: ColorScheme
        get() = requireNotNull(requireScope().activeColorScheme) { "ColorManager is not initialized" }

    private var bitmapCache: LruCache<String, Bitmap>? = null

    private var generation = 0L

    /**
     * Bumped whenever the active scheme changes. Scheme-dependent caches
     * (e.g. per-key colors in Key) re-resolve when it moves.
     */
    val colorGeneration: Long
        get() = generation

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
        onChangeListeners.forEach { it.onColorChange(requireScope().theme) }
    }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        activateScheme(notify = false)

        // Entries are decoded at their drawn size now, so a smaller cap is
        // enough; the floor keeps the background from being evicted on small heaps.
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val cacheSize = maxOf(maxMemory / 16, MIN_BITMAP_CACHE_SIZE.toLong())
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
        activateScheme(notify = true)
    }

    /** The current theme scope, or null before the first theme is attached. */
    fun currentScope(): ThemeScope? = scope

    /**
     * Attaches a theme, replacing the current scope. Theme switches notify
     * through ThemeManager, so no color listener fires here.
     */
    fun attachTheme(theme: Theme) {
        bitmapCache?.evictAll()
        scope = ThemeScope(theme)
        activateScheme(notify = false)
    }

    /**
     * Switches to the scheme [schemeId] and remembers it as the user's choice.
     * The scheme is resolved from the preference afterwards, so the day/night
     * links a scheme may declare are honoured, like an automatic switch.
     */
    fun setColorScheme(schemeId: String) {
        prefs.normalModeColor.setValue(schemeId)
        activateScheme(notify = true)
    }

    private fun requireScope(): ThemeScope = requireNotNull(scope) { "ColorManager is not initialized" }

    private fun resolveActiveScheme(theme: Theme): ColorScheme = ColorSchemeResolver.resolve(
        schemes = theme.presetColorSchemes,
        selectedSchemeId = prefs.normalModeColor.getValue(),
        followSystemDayNight = prefs.followSystemDayNight.getValue(),
        isNightMode = isNightMode,
    )

    /**
     * Re-resolves the active scheme from the prefs and current theme, or
     * activates the given one. Listener notification fires only when the
     * scheme actually changed.
     */
    private fun activateScheme(
        scheme: ColorScheme? = null,
        notify: Boolean,
    ) {
        val activeScope = scope ?: return
        val target = scheme ?: resolveActiveScheme(activeScope.theme)
        if (activeScope.activeColorScheme == target) return
        activeScope.updateScheme(target)
        generation++
        if (notify) fireChange()
    }

    private fun backgroundFolder(scope: ThemeScope) = scope.theme.style.backgroundFolder

    /**
     * Resolves a color key against the given scope. Exposed so UI code can
     * look up keys only a theme can define through an injected scope.
     */
    @ColorInt
    internal fun resolveColor(
        scope: ThemeScope,
        key: String,
    ): Int {
        val tableEntry = ColorKey.from(key)?.let { scope.colorTable?.get(it) }
        if (tableEntry is ColorTable.Value.Color) return tableEntry.argb
        // Keys defined only by a theme resolve through the same chain rules.
        val scheme = requireNotNull(scope.activeColorScheme)
        val raw = ColorTable.resolveRaw(key, scheme, scope.theme.fallbackColors)
        return try {
            if (raw == null) throw IllegalArgumentException("$key not found")
            ColorUtils.parseColor(raw)
        } catch (_: IllegalArgumentException) {
            ColorUtils.parseColor(key)
        }
    }

    /** Resolves a drawable key (color or image asset) against the given scope. */
    internal fun resolveDrawable(
        scope: ThemeScope,
        key: String,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
    ): Drawable? {
        val tableEntry = ColorKey.from(key)?.let { scope.colorTable?.get(it) }
        if (tableEntry != null) {
            return when (tableEntry) {
                is ColorTable.Value.Color -> GradientDrawable().apply { setColor(tableEntry.argb) }
                is ColorTable.Value.Image -> imageDrawable(scope, tableEntry.path, targetWidth, targetHeight)
                ColorTable.Value.None -> parseDrawable(scope, key, targetWidth, targetHeight)
            }
        }
        // Keys defined only by a theme resolve through the same chain rules.
        val scheme = requireNotNull(scope.activeColorScheme)
        val raw = ColorTable.resolveRaw(key, scheme, scope.theme.fallbackColors)
        return parseDrawable(scope, raw ?: key, targetWidth, targetHeight)
    }

    private fun parseDrawable(
        scope: ThemeScope,
        value: String,
        targetWidth: Int,
        targetHeight: Int,
    ): Drawable? {
        if (value.isEmpty()) return null
        val parsed = ColorTable.parseValue(value) {
            runCatching { ColorUtils.parseColor(it) }.getOrNull()
        }
        return when (parsed) {
            is ColorTable.Value.Color -> GradientDrawable().apply { setColor(parsed.argb) }
            is ColorTable.Value.Image -> imageDrawable(scope, parsed.path, targetWidth, targetHeight)
            ColorTable.Value.None -> null
        }
    }

    private fun imageDrawable(
        scope: ThemeScope,
        value: String,
        targetWidth: Int,
        targetHeight: Int,
    ): Drawable? {
        val path = resolveImageFilePath(scope, value)
        val isNinePatch = path.endsWith(".9.png")
        // A nine-patch carries its slice geometry in the PNG itself, so it has
        // to keep its original resolution; sampling it would break the chunks.
        val bitmap =
            loadBitmap(
                path,
                if (isNinePatch) 0 else targetWidth,
                if (isNinePatch) 0 else targetHeight,
            ) ?: return null
        if (isNinePatch) {
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

    /**
     * The decoded [path], sampled down to [targetWidth] x [targetHeight] when both
     * are positive. The cache key includes the size, so one file used as both the
     * background and a key background does not thrash a single entry.
     */
    private fun loadBitmap(
        path: String,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val scaled = targetWidth > 0 && targetHeight > 0
        val cacheKey = if (scaled) "$path@${targetWidth}x$targetHeight" else path
        bitmapCache?.get(cacheKey)?.let { return it }
        val file = File(path)
        if (!file.isFile) return null
        val bitmap =
            if (scaled) {
                runCatching { decodeScaledBitmap(file, targetWidth, targetHeight) }.getOrNull()
            } else {
                BitmapFactory.decodeFile(path)
            }
        if (bitmap == null) return null
        bitmapCache?.put(cacheKey, bitmap)
        return bitmap
    }

    /** Decodes [file] so that it still covers the target, matching `CENTER_CROP`; never upscales. */
    private fun decodeScaledBitmap(
        file: File,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null
        val (width, height) = BitmapSampling.coverSize(srcWidth, srcHeight, targetWidth, targetHeight)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.setTargetSize(width, height)
            }
        } else {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize =
                        BitmapSampling.calculateSampleSize(srcWidth, srcHeight, targetWidth, targetHeight)
                }
            val sampled = BitmapFactory.decodeFile(file.path, options) ?: return null
            if (sampled.width == width && sampled.height == height) {
                sampled
            } else {
                Bitmap.createScaledBitmap(sampled, width, height, true).also { scaled ->
                    if (scaled !== sampled) sampled.recycle()
                }
            }
        }
    }

    private fun resolveImageFilePath(
        scope: ThemeScope,
        value: String,
    ): String {
        val default = DataManager.userDataDir.resolve("backgrounds/${backgroundFolder(scope)}/$value")
        if (!default.exists()) {
            val fallback = DataManager.userDataDir.resolve("backgrounds/$value")
            if (fallback.exists()) return fallback.absolutePath
        }
        return default.absolutePath
    }

    @ColorInt
    fun getColor(key: String): Int = resolveColor(requireScope(), key)

    /**
     * Resolves [key], decoding an image asset at [targetWidth] x [targetHeight]
     * when the caller knows the size it will be drawn at.
     */
    fun getDrawable(
        key: String,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
    ): Drawable? = resolveDrawable(requireScope(), key, targetWidth, targetHeight)

    internal fun resolveDecorDrawable(
        scope: ThemeScope,
        colorKey: String,
        borderColorKey: String?,
        borderPx: Int,
        cornerRadius: Float,
        alpha: Int,
    ): Drawable? = when (val drawable = resolveDrawable(scope, colorKey)) {
        is GradientDrawable ->
            drawable.also {
                it.cornerRadius = cornerRadius
                it.alpha = MathUtils.clamp(alpha, 0, 255)
                if (!borderColorKey.isNullOrEmpty()) {
                    try {
                        val borderColor = resolveColor(scope, borderColorKey)
                        it.setStroke(borderPx, borderColor)
                    } catch (_: Exception) {
                    }
                }
            }

        else -> drawable?.also { it.alpha = MathUtils.clamp(alpha, 0, 255) }
    }

    fun getDecorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = resolveDecorDrawable(requireScope(), colorKey, borderColorKey, borderPx, cornerRadius, alpha)
}
