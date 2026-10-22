/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.log

/**
 * A recent suffix of log records, bounded by both record count and UTF-16 length.
 *
 * Indexing and removing the oldest record are O(1). Appending may evict several
 * old records; each record is removed once. The character budget includes the
 * newlines joining records, but omission counters count the original text only.
 * This class has one owner; callers must serialize access.
 */
internal class LogBuffer(
    private val maxLines: Int = 10_000,
    private val maxCharacters: Int = 2 * 1024 * 1024,
) {
    data class Entry(
        val id: Long,
        val text: String,
        val isLogcat: Boolean,
        val priority: Char?,
    )

    init {
        require(maxLines > 0)
        require(maxCharacters > 0)
    }

    private val entries = arrayOfNulls<Entry>(maxLines)
    private var head = 0
    private var nextId = 0L

    var size = 0
        private set

    var characterCount = 0
        private set

    var omittedLines = 0L
        private set

    var omittedCharacters = 0L
        private set

    val hasOmissions: Boolean
        get() = omittedLines != 0L || omittedCharacters != 0L

    operator fun get(index: Int): Entry {
        require(index in 0 until size)
        return checkNotNull(entries[(head + index) % maxLines])
    }

    fun append(
        text: String,
        isLogcat: Boolean = false,
        priority: Char? = null,
    ) {
        val retained = if (text.length > maxCharacters) {
            // A partial record must be the first retained record, not an internal
            // gap hidden behind older records. Keep its most recent tail.
            discardRetained()
            var start = text.length - maxCharacters
            if (text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start++
            omittedCharacters += start.toLong()
            // Own a bounded copy even on Android versions whose String slices
            // can otherwise retain the original backing character array.
            buildString(text.length - start) { append(text, start, text.length) }
        } else {
            text
        }
        while (size != 0 && (size == maxLines || characterCount.toLong() + 1 + retained.length > maxCharacters)) {
            discardFirst()
        }
        entries[(head + size) % maxLines] = Entry(nextId++, retained, isLogcat, priority)
        if (size != 0) characterCount++
        characterCount += retained.length
        size++
    }

    private fun removeFirst(): Entry {
        val entry = checkNotNull(entries[head])
        entries[head] = null
        head = (head + 1) % maxLines
        size--
        characterCount -= entry.text.length
        if (size != 0) characterCount--
        return entry
    }

    private fun discardFirst() {
        val entry = removeFirst()
        omittedLines++
        omittedCharacters += entry.text.length.toLong()
    }

    private fun discardRetained() {
        while (size != 0) discardFirst()
    }

    /** Transfer a pending batch while preserving a continuous recent suffix. */
    fun drainTo(destination: LogBuffer) {
        require(destination !== this)
        if (hasOmissions) {
            // Pending already lost a prefix: older displayed records cannot stay
            // before that gap while the export claims only its beginning is missing.
            destination.discardRetained()
            destination.omittedLines += omittedLines
            destination.omittedCharacters += omittedCharacters
        }
        while (size != 0) {
            val entry = removeFirst()
            destination.append(entry.text, entry.isLogcat, entry.priority)
        }
        clear()
    }

    /** Forget retained records and omission history, but never reuse visible IDs. */
    fun clear() {
        while (size != 0) removeFirst()
        head = 0
        omittedLines = 0
        omittedCharacters = 0
    }

    /** Produce an immutable export, explicitly identifying a missing prefix. */
    fun snapshot(omissionNotice: String): String = buildString {
        if (hasOmissions) {
            append(omissionNotice)
            if (size != 0) append('\n')
        }
        for (index in 0 until size) {
            if (index != 0) append('\n')
            append(this@LogBuffer[index].text)
        }
    }
}
