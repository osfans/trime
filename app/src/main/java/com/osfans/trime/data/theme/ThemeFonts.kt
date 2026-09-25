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
import com.osfans.trime.data.base.DataManager
import timber.log.Timber
import java.io.File

class ThemeFonts(private val theme: Theme) {
    private val style = theme.generalStyle

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

    val hanb: Typeface
        get() = getTypeface(style.hanbFont)

    val latin: Typeface
        get() = getTypeface(style.latinFont)

    val candidate: Typeface
        get() = getTypeface(style.candidateFont)

    val comment: Typeface
        get() = getTypeface(style.commentFont)

    val key: Typeface
        get() = getTypeface(style.keyFont)

    val label: Typeface
        get() = getTypeface(style.labelFont)

    val popup: Typeface
        get() = getTypeface(style.popupFont)

    val symbol: Typeface
        get() = getTypeface(style.symbolFont)

    val text: Typeface
        get() = getTypeface(style.textFont)

    val toolbar: Typeface
        get() = getTypeface(theme.toolBar.buttonFont)

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
        val typeface = typefaceCache[key]
        return if (typeface == null) {
            Timber.d("getTypeface: key=$key")
            val newTypeface = if (key.files.isEmpty()) {
                Typeface.DEFAULT
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bulk = makeFileStateList(style.latinFont + fonts + style.hanbFont)
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
        } else {
            typeface
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
