/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.os.Build
import androidx.annotation.RequiresApi
import com.osfans.trime.core.data.DataManager
import timber.log.Timber
import java.io.File

class ThemeFonts(private val theme: Theme) {
    private val style = theme.style

    private data class FileState(
        val path: String,
        val lastModified: Long,
        val size: Long,
    )

    private data class TypefaceKey(val files: List<FileState>)

    private val fontDir get() = File(DataManager.userDataDir, "fonts")

    private val fontFamilyCache =
        object : androidx.collection.LruCache<FileState, FontFamily>(16) {}

    private val typefaceCache =
        object : androidx.collection.LruCache<TypefaceKey, Typeface>(16 * 1024) {
            override fun sizeOf(key: TypefaceKey, value: Typeface): Int = key.files.sumOf {
                (it.size / 1024).toInt().coerceAtLeast(1)
            }
        }

    // Font names only change with the theme, so resolve each role once instead of
    // touching the font files on every access.
    val hanb: Typeface by lazy { getTypeface(style.hanbFont.toList()) }

    val latin: Typeface by lazy { getTypeface(style.latinFont.toList()) }

    val candidate: Typeface by lazy { getTypeface(style.candidateFont.toList()) }

    val comment: Typeface by lazy { getTypeface(style.commentFont.toList()) }

    val key: Typeface by lazy { getTypeface(style.keyFont.toList()) }

    val label: Typeface by lazy { getTypeface(style.labelFont.toList()) }

    val popup: Typeface by lazy { getTypeface(style.popupFont.toList()) }

    val symbol: Typeface by lazy { getTypeface(style.symbolFont.toList()) }

    val text: Typeface by lazy { getTypeface(style.textFont.toList()) }

    val toolbar: Typeface by lazy { getTypeface(theme.toolBar.buttonFont.toList()) }

    private fun makeFileStateList(names: List<String>): List<FileState> = names.asSequence()
        .map { File(fontDir, it) }
        .filter { it.isFile }
        .map { FileState(it.path, it.lastModified(), it.length()) }
        .toList()

    private fun resolveKey(fonts: List<String>): TypefaceKey {
        val existing = makeFileStateList(fonts)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TypefaceKey(existing)
        } else {
            TypefaceKey(existing.take(1))
        }
    }

    private fun getTypeface(fonts: List<String>): Typeface {
        val key = resolveKey(fonts)
        return typefaceCache[key] ?: run {
            Timber.d("getTypeface: key=$key")
            val newTypeface = if (key.files.isEmpty()) {
                Typeface.DEFAULT
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bulk = makeFileStateList(style.latinFont.toList() + fonts + style.hanbFont.toList())
                val fontFamilies = bulk.map { getFontFamily(it) }
                Typeface.CustomFallbackBuilder(fontFamilies.first())
                    .apply {
                        for (i in 1 until fontFamilies.size) {
                            addCustomFallback(fontFamilies[i])
                        }
                        setSystemFallback("sans-serif")
                    }
                    .build()
            } else {
                Typeface.createFromFile(key.files.first().path)
            }
            typefaceCache.put(key, newTypeface)
            newTypeface
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getFontFamily(state: FileState): FontFamily {
        val family = fontFamilyCache[state]
        return if (family == null) {
            val newFamily = FontFamily.Builder(
                Font.Builder(File(state.path)).build(),
            ).build()
            fontFamilyCache.put(state, newFamily)
            newFamily
        } else {
            family
        }
    }
}
