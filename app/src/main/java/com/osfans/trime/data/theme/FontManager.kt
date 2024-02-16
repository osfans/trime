package com.osfans.trime.data.theme

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import androidx.annotation.RequiresApi
import com.osfans.trime.data.DataManager
import timber.log.Timber
import java.io.File

object FontManager {
    private val fontDir get() = File(DataManager.userDataDir, "fonts")
    var hanBFont: Typeface = getTypefaceOrDefault("hanb_font")
        private set
    var latinFont: Typeface = getTypefaceOrDefault("latin_font")
        private set
    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun refresh() {
        typefaceCache.clear()
        fontFamiliyCache.clear()
        hanBFont = getTypefaceOrDefault("hanb_font")
        latinFont = getTypefaceOrDefault("latin_font")
    }

    @JvmStatic
    fun getTypeface(key: String): Typeface {
        if (typefaceCache.containsKey(key)) {
            return typefaceCache[key]!!
        }
        Timber.d("getTypeface() key=%s", key)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fontFamilies = mutableListOf<FontFamily>()
            getFontFamilies(key).let {
                if (it.isEmpty()) return@getTypeface Typeface.DEFAULT
                fontFamilies.addAll(getFontFamilies("latin_font"))
                fontFamilies.addAll(it)
                fontFamilies.addAll(getFontFamilies("hanb_font"))
            }
            buildTypeface(fontFamilies).let {
                typefaceCache[key] = it
                return@getTypeface it
            }
        }
        getTypefaceOrDefault(key).let {
            typefaceCache[key] = it
            return@getTypeface it
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildTypeface(fontFamilies: List<FontFamily>): Typeface {
        if (fontFamilies.isEmpty()) return Typeface.DEFAULT
        val builder = Typeface.CustomFallbackBuilder(fontFamilies[0])
        for (i in 1 until fontFamilies.size) {
            builder.addCustomFallback(fontFamilies[i])
        }
        return builder.setSystemFallback("sans-serif").build()
    }

    private fun getTypefaceOrDefault(key: String): Typeface {
        val fonts = ThemeManager.activeTheme.style.getObject(key)

        fun handler(fontName: String): Typeface? {
            val fontFile = File(fontDir, fontName)
            if (fontFile.exists()) {
                return Typeface.createFromFile(fontFile)
            }
            Timber.w("font %s not found", fontFile)
            return null
        }

        if (fonts is String) return handler(fonts) ?: Typeface.DEFAULT
        if (fonts is List<*>) {
            for (font in fonts as List<String>) {
                handler(font).let {
                    if (it != null) return it
                }
            }
        }
        return Typeface.DEFAULT
    }

    private val fontFamiliyCache = mutableMapOf<String, FontFamily>()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFontFamily(fontName: String): FontFamily? {
        if (fontFamiliyCache.containsKey(fontName)) {
            return fontFamiliyCache[fontName]!!
        }
        val fontFile = File(fontDir, fontName)
        if (fontFile.exists()) {
            return FontFamily.Builder(Font.Builder(fontFile).build()).build()
        }
        Timber.w("font %s not found", fontFile)
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFontFamilies(key: String): List<FontFamily> {
        val fonts = ThemeManager.activeTheme.style.getObject(key)
        val fontFamilies = mutableListOf<FontFamily>()

        fun handler(fontName: String) {
            getFontFamily(fontName)?.let {
                fontFamilies.add(it)
            }
        }

        if (fonts is String) handler(fonts)
        if (fonts is List<*>) {
            for (font in fonts as List<String>) {
                handler(font)
            }
        }
        return fontFamilies
    }
}
