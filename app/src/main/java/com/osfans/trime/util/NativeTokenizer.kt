/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

object NativeTokenizer {
    /**
     * Utilize Android native ICU engine to tokenize text
     *
     * @param text original text
     * @param filterBlank filter the whitespace characters
     */
    fun tokenize(text: String, filterBlank: Boolean = true): List<String> {
        if (text.isEmpty()) return emptyList()
        val words = mutableListOf<String>()

        val iterator = android.icu.text.BreakIterator.getWordInstance().apply { setText(text) }
        var start = iterator.first()
        var end = iterator.next()
        while (end != android.icu.text.BreakIterator.DONE) {
            val word = text.substring(start, end)
            if (!filterBlank || word.isNotBlank()) words.add(word)
            start = end
            end = iterator.next()
        }
        return words
    }
}
