/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.prefs

import kotlin.math.ceil

/** Zero keeps a custom theme's value; positive overrides have readable, bounded sizes. */
internal fun candidateTextSize(themed: Float, preferred: Int): Float = if (preferred <= 0) themed else preferred.coerceIn(12, 36).toFloat()

/** Reserve editor space within the height reported by the keyboard context. */
internal fun keyboardHeightOverride(availableDp: Int, preferredDp: Int): Int = if (preferredDp <= 0) 0 else minOf(preferredDp.coerceIn(200, 480), (availableDp - 216).coerceAtLeast(200))

/** TOP reserves 60% for the candidate and 40% for its annotation in CandidateItemUi. */
internal fun candidateRowHeight(
    themed: Int,
    textHeightDp: Float,
    commentHeightDp: Float,
    commentsAbove: Boolean,
): Int {
    val required = if (commentsAbove) {
        maxOf(textHeightDp / 0.6f, commentHeightDp / 0.4f)
    } else {
        maxOf(textHeightDp, commentHeightDp)
    }
    return maxOf(themed, ceil(required.toDouble()).toInt())
}
