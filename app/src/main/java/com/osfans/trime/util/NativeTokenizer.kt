/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.icu.text.BreakIterator
import timber.log.Timber

/**
 * Word segmentation backed by the system ICU engine.
 *
 * Vendors build their own ICU, and some texts make it fail — occasionally with an [Error]
 * rather than an exception. That must not take the IME down, so a failure falls back to
 * splitting on whitespace.
 */
object NativeTokenizer {
    /**
     * Utilize Android native ICU engine to tokenize text
     *
     * @param text original text
     * @param filterBlank filter the whitespace characters
     */
    fun tokenize(text: String, filterBlank: Boolean = true): List<String> {
        if (text.isEmpty()) return emptyList()
        return try {
            tokenizeWithIcu(text, filterBlank)
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            Timber.e(e, "Failed to tokenize ${text.length} characters, splitting on whitespace")
            splitOnWhitespace(text, filterBlank)
        }
    }

    private fun tokenizeWithIcu(text: String, filterBlank: Boolean): List<String> {
        val iterator = BreakIterator.getWordInstance().apply { setText(text) }
        val words = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            // A boundary outside the text throws in substring, one that does not advance spins forever.
            if (end <= start || end > text.length) {
                error("Invalid word boundary $start..$end for ${text.length} characters")
            }
            val word = text.substring(start, end)
            if (!filterBlank || word.isNotBlank()) words.add(word)
            start = end
            end = iterator.next()
        }
        return words
    }

    private fun splitOnWhitespace(text: String, filterBlank: Boolean): List<String> {
        val words = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val blank = text[index].isWhitespace()
            var end = index + 1
            while (end < text.length && text[end].isWhitespace() == blank) end++
            if (!blank || !filterBlank) words.add(text.substring(index, end))
            index = end
        }
        return words
    }
}
