/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.candidateRowHeight
import com.osfans.trime.data.prefs.candidateTextSize
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.GeneralStyle

/** Read actual font bounds, including system SP scaling, when the view is constructed. */
internal fun candidateRowHeight(context: Context, theme: Theme): Int {
    val preferred = AppPrefs.defaultInstance().candidates.textSize.getValue()
    val metrics = context.resources.displayMetrics
    fun height(size: Float, font: Typeface): Float {
        val paint = TextPaint().apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, metrics)
            typeface = font
        }
        val bounds = paint.fontMetricsInt
        return (bounds.bottom - bounds.top) / metrics.density + 4f
    }
    return candidateRowHeight(
        theme.style.candidateViewHeight,
        height(candidateTextSize(theme.style.candidateTextSize, preferred), theme.fonts.candidate),
        height(theme.style.commentTextSize, theme.fonts.comment),
        theme.style.commentPosition == GeneralStyle.CommentPosition.TOP,
    )
}
