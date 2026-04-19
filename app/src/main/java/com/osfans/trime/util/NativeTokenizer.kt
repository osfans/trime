/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.os.Build

object NativeTokenizer {
    /**
     * Utilize Android native ICU engine to tokenize text
     * API 24+ uses android.icu.text, otherwise uses java.text
     *
     * @param text original text
     * @param filterBlank filter the whitespace characters
     */
    fun tokenize(text: String, filterBlank: Boolean = true): List<String> {
        if (text.isEmpty()) return emptyList()
        val words = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val iterator = android.icu.text.BreakIterator.getWordInstance().apply { setText(text) }
            var start = iterator.first()
            var end = iterator.next()
            while (end != android.icu.text.BreakIterator.DONE) {
                val word = text.substring(start, end)
                if (!filterBlank || word.isNotBlank()) words.add(word)
                start = end
                end = iterator.next()
            }
        } else {
            val iterator = java.text.BreakIterator.getWordInstance().apply { setText(text) }
            var start = iterator.first()
            var end = iterator.next()
            while (end != java.text.BreakIterator.DONE) {
                val word = text.substring(start, end)
                if (!filterBlank || word.isNotBlank()) words.add(word)
                start = end
                end = iterator.next()
            }
        }
        return words
    }
}
