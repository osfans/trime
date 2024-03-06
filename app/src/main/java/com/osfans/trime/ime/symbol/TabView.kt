/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.ime.symbol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.updateLayoutParams
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.util.GraphicUtils.drawText
import com.osfans.trime.util.GraphicUtils.measureText
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import timber.log.Timber
import kotlin.math.abs

// 这是滑动键盘顶部的view，展示了键盘布局的多个标签。
// 为了公用候选栏的皮肤参数以及外观，大部分代码从Candidate.java复制而来。
class TabView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private val theme = ThemeManager.activeTheme
    private var highlightIndex = 0
    private val tabTags = mutableListOf<TabTag>()
    private val candidateHighlight =
        PaintDrawable(ColorManager.getColor("hilited_candidate_back_color")!!).apply {
            setCornerRadius(theme.style.getFloat("layout/round_corner"))
        }
    private val separatorPaint =
        Paint().apply {
            color = ColorManager.getColor("candidate_separator_color") ?: Color.BLACK
        }
    private val candidatePaint =
        Paint().apply {
            isAntiAlias = true
            strokeWidth = 0f
            textSize = sp(theme.style.getFloat("candidate_text_size"))
            typeface = candidateFont
        }
    private val candidateFont = FontManager.getTypeface("candidate_font")
    private val candidateTextColor = ColorManager.getColor("candidate_text_color")!!
    private val hilitedCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")!!
    private val candidateViewHeight = theme.style.getInt("candidate_view_height")
    private val commentHeight = theme.style.getInt("comment_height")
    private val candidateSpacing = theme.style.getFloat("candidate_spacing")
    private val candidatePadding = theme.style.getFloat("candidate_padding")
    private val isCommentOnTop = theme.style.getBoolean("comment_on_top")
    private val shouldCandidateUseCursor = theme.style.getBoolean("comment_on_top")

    // private final Rect[] tabGeometries = new Rect[MAX_CANDIDATE_COUNT + 2];

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val h = if (isCommentOnTop) candidateViewHeight + commentHeight else candidateViewHeight
        setMeasuredDimension(
            MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(dp(h), MeasureSpec.AT_MOST),
        )
    }

    private fun isHighlighted(i: Int): Boolean {
        return shouldCandidateUseCursor && i >= 0 && i == highlightIndex
    }

    val highlightLeft: Int
        get() = tabTags[highlightIndex].geometry.left
    val highlightRight: Int
        get() = tabTags[highlightIndex].geometry.right

    override fun onDraw(canvas: Canvas) {
        if (tabTags.isEmpty()) return
        super.onDraw(canvas)

        // Draw highlight background
        if (isHighlighted(highlightIndex)) {
            candidateHighlight.bounds = tabTags[highlightIndex].geometry
            candidateHighlight.draw(canvas)
        }
        // Draw tab text
        val tabY =
            tabTags[0].geometry.centerY() -
                (candidatePaint.ascent() + candidatePaint.descent()) / 2.0f
        for ((i, computedTab) in tabTags.withIndex()) {
            // Calculate a position where the text could be centered in the rectangle.
            val tabX = computedTab.geometry.centerX().toFloat()
            candidatePaint.color = if (isHighlighted(i)) hilitedCandidateTextColor else candidateTextColor
            canvas.drawText(computedTab.text, tabX, tabY, candidatePaint, candidateFont)
            // Draw the separator at the right edge of each candidate.
            canvas.drawRect(
                computedTab.geometry.right - dp(candidateSpacing),
                computedTab.geometry.top.toFloat(),
                computedTab.geometry.right + dp(candidateSpacing),
                computedTab.geometry.bottom.toFloat(),
                separatorPaint,
            )
        }
    }

    fun updateTabWidth() {
        tabTags.clear()
        tabTags.addAll(TabManager.tabCandidates)
        highlightIndex = TabManager.selectedOrZero
        var x = 0
        for ((i, computedTab) in tabTags.withIndex()) {
            computedTab.geometry.set(x, 0, (x + getTabWidth(i)).toInt(), height)
            x = (x + (getTabWidth(i) + candidateSpacing)).toInt()
        }
        updateLayoutParams {
            width = x
            height = dp(if (isCommentOnTop) candidateViewHeight + commentHeight else candidateViewHeight)
        }
        invalidate()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTabWidth()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private var x0 = 0
    private var y0 = 0
    private var time0: Long = 0

    init {
        setWillNotDraw(false)
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        val x = me.x.toInt()
        val y = me.y.toInt()
        when (me.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x0 = x
                y0 = y
                time0 = System.currentTimeMillis()
            }

            MotionEvent.ACTION_MOVE -> if (abs(x - x0) > 100) time0 = 0
            MotionEvent.ACTION_UP -> {
                val i = tabTags.indexOfFirst { it.geometry.contains(x, y) }
                if (i > -1) {
                    performClick()
                    val tag = TabManager.tabTags[i]
                    if (tag.type == SymbolKeyboardType.NO_KEY) {
                        if (tag.command == KeyCommandType.EXIT) {
                            TrimeInputMethodService.getService().selectLiquidKeyboard(-1)
                        }
                    } else if (System.currentTimeMillis() - time0 < 500) {
                        highlightIndex = i
                        invalidate()
                        TrimeInputMethodService.getService().selectLiquidKeyboard(i)
                    }
                    Timber.d("index=" + i + " length=" + tabTags.size)
                }
            }
        }
        return true
    }

    private fun getTabWidth(i: Int): Float {
        val s = tabTags[i].text
        return if (s.isNotEmpty()) {
            2 * dp(candidatePadding) + candidatePaint.measureText(s, candidateFont)
        } else {
            2 * dp(candidatePadding)
        }
    }
}
