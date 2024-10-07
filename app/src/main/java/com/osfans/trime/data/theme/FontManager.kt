// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import androidx.annotation.RequiresApi
import com.osfans.trime.data.base.DataManager
import timber.log.Timber
import java.io.File

object FontManager {
    private lateinit var theme: Theme

    private enum class FontKey {
        HANB_FONT,
        LATIN_FONT,
        CANDIDATE_FONT,
        COMMENT_FONT,
        KEY_FONT,
        LABEL_FONT,
        PREVIEW_FONT,
        SYMBOL_FONT,
        TEXT_FONT,
        LONG_TEXT_FONT,
    }

    private val fontDir get() = File(DataManager.userDataDir, "fonts")
    lateinit var hanBFont: Typeface
        private set
    lateinit var latinFont: Typeface
        private set
    private val typefaceCache = mutableMapOf<String, Typeface>()
    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    fun resetCache(theme: Theme) {
        typefaceCache.clear()
        fontFamilyCache.clear()
        this.theme = theme
        hanBFont = getTypefaceOrDefault(FontKey.HANB_FONT.name)
        latinFont = getTypefaceOrDefault(FontKey.LATIN_FONT.name)
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
                if (it.isEmpty()) {
                    typefaceCache[key] = Typeface.DEFAULT
                    return@getTypeface Typeface.DEFAULT
                }
                fontFamilies.addAll(getFontFamilies(FontKey.LATIN_FONT.name))
                fontFamilies.addAll(it)
                fontFamilies.addAll(getFontFamilies(FontKey.HANB_FONT.name))
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
        val fonts = getFontFromStyle(key)

        fun handler(fontName: String): Typeface? {
            val fontFile = File(fontDir, fontName)
            if (fontFile.exists()) {
                return Typeface.createFromFile(fontFile)
            }
            Timber.w("font %s not found", fontFile)
            return null
        }

        return fonts?.firstNotNullOfOrNull { handler(it) } ?: Typeface.DEFAULT
    }

    private fun getFontFromStyle(key: String): List<String>? {
        val style = theme.generalStyle
        return when (FontKey.entries.firstOrNull { it.name == key.uppercase() }) {
            FontKey.HANB_FONT -> style.hanbFont
            FontKey.LATIN_FONT -> style.latinFont
            FontKey.CANDIDATE_FONT -> style.candidateFont
            FontKey.COMMENT_FONT -> style.commentFont
            FontKey.KEY_FONT -> style.keyFont
            FontKey.LABEL_FONT -> style.labelFont
            FontKey.PREVIEW_FONT -> style.previewFont
            FontKey.SYMBOL_FONT -> style.symbolFont
            FontKey.TEXT_FONT -> style.textFont
            FontKey.LONG_TEXT_FONT -> style.longTextFont
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFontFamily(fontName: String): FontFamily? {
        if (fontFamilyCache.containsKey(fontName)) {
            return fontFamilyCache[fontName]!!
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
        val fonts = getFontFromStyle(key)

        return fonts?.mapNotNull { getFontFamily(it) } ?: listOf()
    }
}
