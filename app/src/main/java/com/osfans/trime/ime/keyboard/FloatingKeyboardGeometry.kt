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

    /** The default upper bound, used when no larger cap is supplied. */
    const val MAX_WIDTH_PERCENT = 100

    /**
     * The absolute upper bound for the scale. The floating card may grow past
     * the portrait width in landscape, but not without limit.
     */
    const val ABSOLUTE_MAX_WIDTH_PERCENT = 300

    /** The strip at the top of the card that holds the drag pill and controls. */
    const val DRAG_HANDLE_HEIGHT_DP = 32

    /** Scales a dimension by the floating scale, keeping the card's ratio. */
    fun scaledDimension(
        base: Int,
        scalePercent: Int,
    ): Int = (base * scalePercent.coerceIn(MIN_WIDTH_PERCENT, ABSOLUTE_MAX_WIDTH_PERCENT) / 100f)
        .roundToInt()
        .coerceAtLeast(0)

    /**
     * The key area height of the floating card.
     *
     * At 100% the card uses exactly the docked portrait keyboard height. The
     * drag handle is added above this area instead of being taken out of it.
     * [scalePercent] scales the key area together with the card width, which
     * keeps the keyboard's proportions when the user resizes it. It may exceed
     * 100% in landscape, up to [ABSOLUTE_MAX_WIDTH_PERCENT].
     */
    fun floatingKeyHeight(
        keyboardHeight: Int,
        scalePercent: Int = 100,
    ): Int = scaledDimension(keyboardHeight, scalePercent).coerceAtLeast(1)

    /**
     * The largest scale that fits both limits. [overlayHeight] is the part of
     * the card that may overlap the top edge; [unscaledFixedHeight] is content
     * that does not scale, such as the preedit row.
     */
    fun maxScalePercent(
        widthPercent: Int,
        availableHeight: Int,
        baseCardHeight: Int,
        unscaledFixedHeight: Int = 0,
        overlayHeight: Int = 0,
    ): Int {
        val widthLimit = widthPercent.coerceIn(MIN_WIDTH_PERCENT, ABSOLUTE_MAX_WIDTH_PERCENT)
        if (availableHeight <= 0 || baseCardHeight <= 0) return widthLimit
        val scaledCardHeight = (baseCardHeight - overlayHeight).coerceAtLeast(1)
        val usableHeight = (availableHeight - unscaledFixedHeight).coerceAtLeast(1)
        val heightLimit = (usableHeight * 100f / scaledCardHeight).toInt()
        return minOf(widthLimit, heightLimit)
            .coerceIn(MIN_WIDTH_PERCENT, ABSOLUTE_MAX_WIDTH_PERCENT)
    }

    /**
     * The default position of a floating card inside
     * [containerStart]..[containerEnd].
     *
     * The card is aligned to the end edge, leaving [margin] between it and
     * the edge. When [center] is set it is centered in the available space
     * instead, which is how the landscape card starts out.
     *
     * [allowStartOverflow] keeps the end edge fixed when the card is too
     * large, so it overflows at the top instead of off the bottom.
     */
    fun defaultPosition(
        containerStart: Int,
        containerEnd: Int,
        viewSize: Int,
        margin: Int,
        center: Boolean,
        allowStartOverflow: Boolean = false,
    ): Int {
        if (containerEnd <= containerStart || viewSize <= 0) return containerStart
        val safeMargin = margin.coerceAtLeast(0)
        val start = (containerStart + safeMargin).coerceAtMost(containerEnd)
        val end = (containerEnd - safeMargin).coerceAtLeast(start)
        val maxStart = end - viewSize
        if (center) {
            val centeredStart = maxStart.coerceAtLeast(start)
            return start + (centeredStart - start) / 2
        }
        return if (allowStartOverflow) maxStart else maxStart.coerceAtLeast(start)
    }

    /**
     * The width of the card, as a percentage of the available space.
     *
     * [minWidth] keeps the keys readable on a narrow screen; it is ignored
     * when it does not fit. [maxWidth] lets the caller allow the card to grow
     * past [containerWidth] in landscape, and is itself capped by
     * [ABSOLUTE_MAX_WIDTH_PERCENT].
     */
    fun widthFor(
        containerWidth: Int,
        percent: Int,
        minWidth: Int = 0,
        maxWidth: Int = containerWidth,
    ): Int {
        if (containerWidth <= 0) return 0
        val clampedPercent = percent.coerceIn(MIN_WIDTH_PERCENT, ABSOLUTE_MAX_WIDTH_PERCENT)
        val width = (containerWidth.toFloat() * clampedPercent / 100f).roundToInt()
        val absoluteMax = containerWidth * ABSOLUTE_MAX_WIDTH_PERCENT / 100
        val max = maxWidth.coerceIn(0, absoluteMax)
        val min = minWidth.coerceIn(0, max)
        return width.coerceIn(min, max)
    }

    /**
     * The percentage of [containerWidth] that [width] represents, for saving
     * a width that was chosen by dragging.
     *
     * [maxPercent] is the largest percentage the caller allows. It defaults to
     * [MAX_WIDTH_PERCENT] and cannot exceed [ABSOLUTE_MAX_WIDTH_PERCENT].
     */
    fun percentFor(
        containerWidth: Int,
        width: Int,
        maxPercent: Int = MAX_WIDTH_PERCENT,
    ): Int {
        if (containerWidth <= 0 || width <= 0) return MIN_WIDTH_PERCENT
        val max = maxPercent.coerceIn(MIN_WIDTH_PERCENT, ABSOLUTE_MAX_WIDTH_PERCENT)
        return (width.toFloat() * 100f / containerWidth)
            .roundToInt()
            .coerceIn(MIN_WIDTH_PERCENT, max)
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
     * Normalized 0..1 position of [position] within
     * [containerStart]..[containerEnd - viewSize].
     *
     * A degenerate interval has no meaningful position, so it maps to 0.
     */
    fun positionFraction(
        position: Int,
        containerStart: Int,
        containerEnd: Int,
        viewSize: Int,
    ): Float {
        if (viewSize <= 0) return 0f
        val maxStart = containerEnd - viewSize
        // An oversized view is only ever end-anchored, so it maps to the end.
        if (maxStart < containerStart) return 1f
        if (maxStart == containerStart) return 0f
        return ((position - containerStart).toFloat() / (maxStart - containerStart))
            .coerceIn(0f, 1f)
    }

    /**
     * Inverse of [positionFraction]; returns [containerStart] for a degenerate
     * interval.
     */
    fun positionFromFraction(
        fraction: Float,
        containerStart: Int,
        containerEnd: Int,
        viewSize: Int,
    ): Float {
        if (viewSize <= 0) return containerStart.toFloat()
        val maxStart = containerEnd - viewSize
        // An oversized view is end-anchored, not top-aligned.
        if (maxStart < containerStart) return maxStart.toFloat()
        if (maxStart == containerStart) return containerStart.toFloat()
        return containerStart + fraction.coerceIn(0f, 1f) * (maxStart - containerStart)
    }

    /**
     * Clamps a translation so [viewSize] stays inside the available interval
     * [containerStart]..[containerEnd], where both values are coordinates in the
     * parent's space and [basePosition] is the view's untranslated position.
     *
     * [allowStartOverflow] anchors an oversized view to the end edge.
     */
    fun clampTranslation(
        translation: Float,
        containerStart: Int,
        containerEnd: Int,
        viewSize: Int,
        basePosition: Int,
        allowStartOverflow: Boolean = false,
    ): Float {
        if (containerEnd <= containerStart || viewSize <= 0) return 0f
        val min = (containerStart - basePosition).toFloat()
        val max = (containerEnd - viewSize - basePosition).toFloat()
        if (min > max) {
            return if (allowStartOverflow) max else 0f
        }
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
