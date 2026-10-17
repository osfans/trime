/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import kotlin.math.ceil

/** Dimensions in dp; independent of candidates and composition length. */
object T9LayoutPolicy {
    data class Widths(val left: Int, val right: Int, val choices: Int)
    data class HeightBudget(
        val maximum: Int,
        val minimumTargetsFit: Boolean,
        val panelMaximum: Int,
        val editorFits: Boolean,
    )

    /** An IME cannot infer the other app's text size or padding from EditorInfo. */
    fun editorReserve(available: Int, minimum: Int): Int = maxOf(minimum, ceil(available.coerceAtLeast(0) / 4.0).toInt())

    fun heightBudget(available: Int, header: Int, controls: Int, padding: Int, editor: Int = 48): HeightBudget {
        val remaining = available - header - controls - padding - editor
        return HeightBudget(
            remaining.coerceAtLeast(1),
            remaining >= 192,
            (available - editor).coerceAtLeast(1),
            available > editor,
        )
    }

    /** Ownership comes from the page/return route, never from empty or changing input text. */
    fun usesCompositionPanel(t9Layout: Boolean, symbols: Boolean, restricted: Boolean, originIsT9: Boolean, sameSchema: Boolean): Boolean = t9Layout || (symbols && !restricted && originIsT9 && sameSchema)

    fun modelHeight(requested: Int, maximum: Int, minimum: Int = 192): Int = if (maximum > 0) minOf(requested, maxOf(minimum, maximum)) else requested

    fun widths(available: Int, sidePadding: Int, fontScale: Float, hand: Int, availableHeight: Int = Int.MAX_VALUE): Widths {
        val padding = sidePadding.coerceIn(0, (available - 300).coerceAtLeast(0) / 2)
        val content = available - padding * 2
        // A short-screen sidebar also holds three real 48dp action targets.
        val short = availableHeight <= 480
        val choiceWidth = maxOf(if (short) 144 else 0, ceil(88 * fontScale.coerceIn(1f, 1.5f)).toInt())
        val handWidth = 320 + if (availableHeight <= 480) choiceWidth else 0
        val spare = if (hand == 0) 0 else (content - handWidth).coerceAtLeast(0)
        val width = content - spare
        return Widths(
            padding + if (hand == 2) spare else 0,
            padding + if (hand == 1) spare else 0,
            if (width >= 400 && width - choiceWidth >= 300) choiceWidth else 0,
        )
    }

    fun height(available: Int, themed: Int, preferredRow: Int): Int {
        val desired = maxOf(192, if (preferredRow > 0) preferredRow.coerceIn(48, 80) * 4 else themed)
        // Reserve two control rows, candidates, navigation and status bars on supported phone sizes.
        return minOf(desired, (available - 216).coerceAtLeast(192))
    }
}
