/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.min

/** Pixel-space drawing bounds only: never changes a key's hit target or accessible label. */
internal object KeyContentLayout {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = (right - left).coerceAtLeast(0f)
        val height get() = (bottom - top).coerceAtLeast(0f)
    }

    data class Bands(val main: Box, val top: Box?, val bottom: Box?)
    data class Metrics(val width: Float, val top: Float, val bottom: Float) {
        val height get() = (bottom - top).coerceAtLeast(1f)
    }

    data class Text(val lines: List<String>, val size: Float, val width: Float, val top: Float, val bottom: Float) {
        val lineHeight get() = (bottom - top).coerceAtLeast(1f)
        val height get() = lineHeight * lines.size
    }

    fun bands(content: Box, hasTop: Boolean, hasBottom: Boolean, hintHeight: Float, gap: Float): Bands {
        // Even an unusually short third-party key retains at least half its area for the main label.
        val reserve = min(hintHeight + gap, content.height / 4f).coerceAtLeast(0f)
        val separator = min(gap, reserve / 4f).coerceAtLeast(0f)
        val top = if (hasTop) Box(content.left, content.top, content.right, content.top + reserve - separator) else null
        val bottom = if (hasBottom) Box(content.left, content.bottom - reserve + separator, content.right, content.bottom) else null
        return Bands(
            Box(content.left, content.top + if (hasTop) reserve else 0f, content.right, content.bottom - if (hasBottom) reserve else 0f),
            top,
            bottom,
        )
    }

    fun place(area: Box, width: Float, height: Float, offsetX: Float, offsetY: Float): Box {
        val w = width.coerceIn(0f, area.width)
        val h = height.coerceIn(0f, area.height)
        val x = ((area.left + area.right - w) / 2f + offsetX).coerceIn(area.left, area.right - w)
        val y = ((area.top + area.bottom - h) / 2f + offsetY).coerceIn(area.top, area.bottom - h)
        return Box(x, y, x + w, y + h)
    }

    fun text(
        value: String,
        preferredSize: Float,
        minimumSize: Float,
        area: Box,
        maxLines: Int,
        measure: (String, Float) -> Metrics,
    ): Text {
        if (value.isEmpty() || area.width <= 0f || area.height <= 0f) return Text(emptyList(), 0f, 0f, 0f, 0f)
        val preferred = preferredSize.coerceAtLeast(1f)
        val original = measure(value.replace('\n', ' '), preferred)
        // Height alone may constrain the floor for physically tiny custom keys. Label length never does.
        val floor = min(minimumSize.coerceAtLeast(1f), min(preferred, preferred * area.height / original.height))
        val fitted = min(preferred, min(preferred * area.width / original.width.coerceAtLeast(1f), preferred * area.height / original.height))
        if ('\n' !in value && fitted >= floor) {
            val result = measured(listOf(value), fitted, measure)
            if (result.width <= area.width && result.height <= area.height) return result
        }
        val atFloor = measure(value.replace('\n', ' '), floor)
        val linesAllowed = min(maxLines.coerceIn(1, 2), (area.height / atFloor.height).toInt().coerceAtLeast(1))
        val remaining = value.split('\n').toMutableList()
        val lines = mutableListOf<String>()
        while (remaining.isNotEmpty() && lines.size < linesAllowed) {
            val next = remaining.removeAt(0)
            if (lines.size == linesAllowed - 1) {
                val rest = (listOf(next) + remaining).joinToString(" ")
                lines.add(ellipsize(rest, floor, area.width, measure))
                break
            }
            val end = fittingBoundary(next, floor, area.width, measure)
            if (end == 0 && next.isNotEmpty()) {
                lines.add(ellipsize(next, floor, area.width, measure))
                break
            }
            lines.add(next.substring(0, end))
            if (end < next.length) remaining.add(0, next.substring(end))
        }
        return measured(lines, floor, measure)
    }

    private fun measured(lines: List<String>, size: Float, measure: (String, Float) -> Metrics): Text {
        val metrics = lines.map { measure(it, size) }
        return Text(lines, size, metrics.maxOfOrNull { it.width } ?: 0f, metrics.minOfOrNull { it.top } ?: 0f, metrics.maxOfOrNull { it.bottom } ?: 0f)
    }

    private fun ellipsize(value: String, size: Float, width: Float, measure: (String, Float) -> Metrics): String {
        if (measure(value, size).width <= width) return value
        val ellipsis = "…"
        if (measure(ellipsis, size).width > width) return ""
        val end = fittingBoundary(value, size, width, measure, ellipsis)
        return value.substring(0, end) + ellipsis
    }

    private fun fittingBoundary(value: String, size: Float, width: Float, measure: (String, Float) -> Metrics, suffix: String = ""): Int {
        val boundaries = boundaries(value)
        var low = 0
        var high = boundaries.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (measure(value.substring(0, boundaries[mid]) + suffix, size).width <= width) low = mid else high = mid - 1
        }
        return boundaries[low]
    }

    /** Java's API21-compatible character iterator plus emoji joins older iterators may split. */
    internal fun boundaries(value: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(value) }
        val result = mutableListOf(0)
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val previous = value.codePointBefore(end)
            val next = if (end < value.length) value.codePointAt(end) else -1
            val nextType = if (next >= 0) Character.getType(next) else -1
            val joined = previous == 0x200d || next == 0x200d ||
                nextType in listOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt()) ||
                next in 0xfe00..0xfe0f || next in 0xe0100..0xe01ef || next in 0x1f3fb..0x1f3ff
            var regionalCount = 0
            var start = end
            while (start > 0 && value.codePointBefore(start) in 0x1f1e6..0x1f1ff) {
                val code = value.codePointBefore(start)
                start -= Character.charCount(code)
                regionalCount++
            }
            val flagPair = next in 0x1f1e6..0x1f1ff && regionalCount % 2 == 1
            if (end == value.length || (!joined && !flagPair)) result.add(end)
            end = iterator.next()
        }
        return result
    }
}
