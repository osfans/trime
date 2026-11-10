/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import kotlin.math.ceil

/** Size arithmetic for decoding theme images at the size they are drawn at. */
internal object BitmapSampling {
    /**
     * The size that covers a [targetWidth] x [targetHeight] view, as `CENTER_CROP`
     * would draw it. Never upscales.
     */
    fun coverSize(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return srcWidth to srcHeight
        }
        val scale = maxOf(targetWidth.toFloat() / srcWidth, targetHeight.toFloat() / srcHeight)
        if (scale >= 1f) return srcWidth to srcHeight
        val width = ceil(srcWidth * scale).toInt().coerceIn(1, srcWidth)
        val height = ceil(srcHeight * scale).toInt().coerceIn(1, srcHeight)
        return width to height
    }

    /** The power-of-two `inSampleSize` that still covers the target afterwards. */
    fun calculateSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sample = 1
        while (srcWidth / (sample * 2) >= targetWidth && srcHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }
}
