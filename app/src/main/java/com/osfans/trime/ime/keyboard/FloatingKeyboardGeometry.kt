/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import androidx.core.graphics.Insets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure geometry helpers for the floating keyboard.
 *
 * The keyboard is laid out bottom-centered and moved with translations, so
 * [clampTranslation] takes the layout position it is translated from.
 */
internal object FloatingKeyboardGeometry {
    const val MIN_WIDTH_PERCENT = 20
    const val MAX_WIDTH_PERCENT = 100

    /** The strip at the top of the card that holds the drag pill and controls. */
    const val DRAG_HANDLE_HEIGHT_DP = 32

    /** The card's own padding below the keys. */
    const val CARD_BOTTOM_PADDING_DP = 8

    /**
     * The key area height that makes the card's total height equal to the
     * normal portrait keyboard height.
     *
     * The drag handle and the card's bottom padding are part of that total,
     * so they are taken out of the key area instead of added on top of it.
     * [scalePercent] shrinks the key area together with the card width, which
     * keeps the keyboard's proportions when the user resizes it.
     */
    fun floatingKeyHeight(
        portraitKeyboardHeight: Int,
        portraitBottomPadding: Int,
        dragHandleHeight: Int,
        cardBottomPadding: Int,
        scalePercent: Int = 100,
    ): Int {
        val base = (portraitKeyboardHeight + portraitBottomPadding - dragHandleHeight - cardBottomPadding)
            .coerceAtLeast(1)
        return (base * scalePercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT) / 100f)
            .roundToInt()
            .coerceAtLeast(1)
    }

    /**
     * The default position of a floating card inside
     * [containerStart]..[containerEnd].
     *
     * The card is aligned to the end edge, leaving [margin] between it and
     * the edge. When [center] is set it is centered in the available space
     * instead, which is how the landscape card starts out.
     */
    fun defaultPosition(
        containerStart: Int,
        containerEnd: Int,
        viewSize: Int,
        margin: Int,
        center: Boolean,
    ): Int {
        if (containerEnd <= containerStart || viewSize <= 0) return containerStart
        val safeMargin = margin.coerceAtLeast(0)
        val start = (containerStart + safeMargin).coerceAtMost(containerEnd)
        val end = (containerEnd - safeMargin).coerceAtLeast(start)
        val maxStart = (end - viewSize).coerceAtLeast(start)
        return if (center) start + (maxStart - start) / 2 else maxStart
    }

    /**
     * The width of the card, as a percentage of the available space.
     *
     * [minWidth] keeps the keys readable on a narrow screen; it is ignored
     * when it does not fit.
     */
    fun widthFor(
        containerWidth: Int,
        percent: Int,
        minWidth: Int = 0,
    ): Int {
        if (containerWidth <= 0) return 0
        val clampedPercent = percent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
        val width = (containerWidth.toFloat() * clampedPercent / 100f).roundToInt()
        val min = minWidth.coerceIn(0, containerWidth)
        return width.coerceIn(min, containerWidth)
    }

    /**
     * The percentage of [containerWidth] that [width] represents, for saving
     * a width that was chosen by dragging.
     */
    fun percentFor(
        containerWidth: Int,
        width: Int,
    ): Int {
        if (containerWidth <= 0 || width <= 0) return MIN_WIDTH_PERCENT
        return (width.toFloat() * 100f / containerWidth)
            .roundToInt()
            .coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)
    }

    /**
     * Merges the system bar insets that are currently visible with the stable
     * ones, which are reported even when a bar is hidden.
     *
     * The left, top and right edges use the stable values, because a status bar
     * or a side navigation bar can reappear at any moment and the card must
     * not slide under them. The bottom edge follows the current navigation bar,
     * so the card can still rest on the bottom of the screen.
     */
    fun mergeSystemInsets(
        current: Insets,
        stable: Insets,
    ): Insets = Insets.of(
        max(current.left, stable.left),
        max(current.top, stable.top),
        max(current.right, stable.right),
        current.bottom,
    )

    /**
     * Clamps a translation so [viewSize] stays inside the available interval
     * [containerStart]..[containerEnd], where both values are coordinates in the
     * parent's space and [basePosition] is the view's untranslated position.
     */
    fun clampTranslation(
        translation: Float,
        containerStart: Int,
        containerEnd: Int,
        viewSize: Int,
        basePosition: Int,
    ): Float {
        if (containerEnd <= containerStart || viewSize <= 0) return 0f
        val min = (containerStart - basePosition).toFloat()
        val max = (containerEnd - viewSize - basePosition).toFloat()
        if (min > max) return 0f
        return translation.coerceIn(min, max)
    }

    /**
     * Snaps the view to the closest anchor, if it is close enough to one.
     *
     * The anchors are the edges of the available space and, when
     * [includeCentre] is set, its middle. The view only moves when it is
     * within [threshold] pixels of an anchor.
     */
    fun snapTranslation(
        translation: Float,
        containerStart: Int,
        containerEnd: Int,
        viewSize: Int,
        basePosition: Int,
        threshold: Int,
        includeCentre: Boolean = false,
    ): Float {
        if (containerEnd <= containerStart || viewSize <= 0) return translation
        if (viewSize > containerEnd - containerStart) return translation
        val anchors =
            buildList {
                add(containerStart)
                if (includeCentre) {
                    val centre = (containerStart + containerEnd - viewSize) / 2
                    add(centre.coerceIn(containerStart, containerEnd - viewSize))
                }
                add(containerEnd - viewSize)
            }
        val absolute = (basePosition + translation).roundToInt()
        return (snapToNearest(absolute, anchors, threshold) - basePosition).toFloat()
    }

    /** The anchor closest to [position], or [position] itself if none is near. */
    fun snapToNearest(
        position: Int,
        anchors: List<Int>,
        threshold: Int,
    ): Int {
        var best = position
        var bestDistance = Int.MAX_VALUE
        for (anchor in anchors) {
            val distance = abs(anchor - position)
            if (distance < bestDistance) {
                bestDistance = distance
                best = anchor
            }
        }
        return if (bestDistance <= threshold) best else position
    }
}
