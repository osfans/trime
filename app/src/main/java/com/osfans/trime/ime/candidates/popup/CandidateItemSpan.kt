/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.annotation.ColorInt

class CandidateItemSpan(
    @ColorInt
    private val color: Int,
    private val textSize: Float,
    private val typeface: Typeface,
) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.color = color
        updateState(textPaint)
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        updateState(textPaint)
    }

    private fun updateState(textPaint: TextPaint) {
        textPaint.textSize = textSize
        textPaint.typeface = typeface
    }
}
