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
    private val fontDir = File(DataManager.userDataDir, "fonts")
    private val theme get() = ThemeManager.activeTheme
    val hanBFont: Typeface get() = getTypefaceOrNull("hanb_font") ?: Typeface.DEFAULT
    val latinFont: Typeface get() = getTypefaceOrNull("latin_font") ?: Typeface.DEFAULT

    @JvmStatic
    fun getTypeface(key: String): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fontFamilies = mutableListOf<FontFamily>()
            getFontFamilies(key).let {
                if (it.isEmpty()) return@getTypeface Typeface.DEFAULT
                fontFamilies.addAll(getFontFamilies("latin_font"))
                fontFamilies.addAll(it)
                fontFamilies.addAll(getFontFamilies("hanb_font"))
            }
            return buildTypeface(fontFamilies)
        }
        return getTypefaceOrNull(key) ?: Typeface.DEFAULT
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

    private fun getTypefaceOrNull(key: String): Typeface? {
        val fonts = theme.style.getObject(key)

        fun handler(fontName: String): Typeface? {
            val fontFile = File(fontDir, fontName)
            if (fontFile.exists()) {
                return Typeface.createFromFile(fontFile)
            }
            Timber.w("font %s not found", fontFile)
            return null
        }

        if (fonts is String) return handler(fonts)
        if (fonts is List<*>) {
            for (font in fonts as List<String>) {
                handler(font).let {
                    if (it != null) return it
                }
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getFontFamilies(key: String): List<FontFamily> {
        val fonts = theme.style.getObject(key)
        val fontFamilies = mutableListOf<FontFamily>()

        fun handler(fontName: String) {
            val fontFile = File(fontDir, fontName)
            if (fontFile.exists()) {
                fontFamilies.add(FontFamily.Builder(Font.Builder(fontFile).build()).build())
                return
            }
            Timber.w("font %s not found", fontFile)
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
