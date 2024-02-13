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
import android.graphics.Typeface
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.updateLayoutParams
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.util.GraphicUtils.drawText
import com.osfans.trime.util.GraphicUtils.measureText
import com.osfans.trime.util.dp2px
import timber.log.Timber
import kotlin.math.abs

// 这是滑动键盘顶部的view，展示了键盘布局的多个标签。
// 为了公用候选栏的皮肤参数以及外观，大部分代码从Candidate.java复制而来。
class TabView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var highlightIndex = 0
    private var tabTags: ArrayList<TabTag>? = null
    private var candidateHighlight: PaintDrawable? = null
    private val separatorPaint: Paint
    private val candidatePaint: Paint
    private var candidateFont: Typeface? = null
    private var candidateTextColor = 0
    private var hilitedCandidateTextColor = 0
    private var candidateViewHeight = 0
    private var commentHeight = 0
    private var candidateSpacing = 0
    private var candidatePadding = 0
    private var isCommentOnTop = false
    private var shouldCandidateUseCursor = false

    // private final Rect[] tabGeometries = new Rect[MAX_CANDIDATE_COUNT + 2];
    fun reset() {
        val theme = ThemeManager.activeTheme
        candidateHighlight = PaintDrawable(theme.colors.getColor("hilited_candidate_back_color")!!)
        candidateHighlight!!.setCornerRadius(theme.style.getFloat("layout/round_corner"))
        separatorPaint.color = theme.colors.getColor("candidate_separator_color")!!
        candidateSpacing = dp2px(theme.style.getFloat("candidate_spacing")).toInt()
        candidatePadding = dp2px(theme.style.getFloat("candidate_padding")).toInt()
        candidateTextColor = theme.colors.getColor("candidate_text_color")!!
        hilitedCandidateTextColor = theme.colors.getColor("hilited_candidate_text_color")!!
        commentHeight = dp2px(theme.style.getFloat("comment_height")).toInt()
        val candidateTextSize = dp2px(theme.style.getFloat("candidate_text_size")).toInt()
        candidateViewHeight = dp2px(theme.style.getFloat("candidate_view_height")).toInt()
        candidateFont = FontManager.getTypeface("candidate_font")
        candidatePaint.textSize = candidateTextSize.toFloat()
        candidatePaint.setTypeface(candidateFont)
        isCommentOnTop = theme.style.getBoolean("comment_on_top")
        shouldCandidateUseCursor = theme.style.getBoolean("candidate_use_cursor")
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val h = if (isCommentOnTop) candidateViewHeight + commentHeight else candidateViewHeight
        setMeasuredDimension(
            MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST),
        )
    }

    private fun isHighlighted(i: Int): Boolean {
        return shouldCandidateUseCursor && i >= 0 && i == highlightIndex
    }

    val highlightLeft: Int
        get() = tabTags!![highlightIndex].geometry.left
    val highlightRight: Int
        get() = tabTags!![highlightIndex].geometry.right

    override fun onDraw(canvas: Canvas) {
        if (tabTags == null) return
        super.onDraw(canvas)

        // Draw highlight background
        if (isHighlighted(highlightIndex)) {
            candidateHighlight!!.bounds = tabTags!![highlightIndex].geometry
            candidateHighlight!!.draw(canvas)
        }
        // Draw tab text
        val tabY = (
            /* (shouldShowComment && isCommentOnTop)
        ? tabTags.get(0).geometry.centerY()
            - (candidatePaint.ascent() + candidatePaint.descent()) / 2.0f
            + commentHeight / 2.0f
        : */
            tabTags!![0].geometry.centerY() -
                (candidatePaint.ascent() + candidatePaint.descent()) / 2.0f
        )
        for ((i, computedTab) in tabTags!!.withIndex()) {
            // Calculate a position where the text could be centered in the rectangle.
            val tabX = computedTab.geometry.centerX().toFloat()
            candidatePaint.color = if (isHighlighted(i)) hilitedCandidateTextColor else candidateTextColor
            canvas.drawText(computedTab.text, tabX, tabY, candidatePaint, candidateFont!!)
            // Draw the separator at the right edge of each candidate.
            canvas.drawRect(
                (
                    computedTab.geometry.right - candidateSpacing
                ).toFloat(),
                computedTab.geometry.top.toFloat(),
                (
                    computedTab.geometry.right + candidateSpacing
                ).toFloat(),
                computedTab.geometry.bottom.toFloat(),
                separatorPaint,
            )
        }
    }

    fun updateTabWidth() {
        tabTags = TabManager.get().tabCandidates
        highlightIndex = TabManager.get().selectedOrZero
        var x = 0
        for ((i, computedTab) in tabTags!!.withIndex()) {
            computedTab.geometry.set(x, 0, (x + getTabWidth(i)).toInt(), height)
            x = (x + (getTabWidth(i) + candidateSpacing)).toInt()
        }
        updateLayoutParams {
            Timber.d("updateTabWidth: layoutPrams from: height=$height, width=$width")
            width = x
            height = if (isCommentOnTop) candidateViewHeight + commentHeight else candidateViewHeight
            Timber.d("updateTabWidth: layoutPrams to: height=$height, width=$width")
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
        Timber.d("onSizeChanged() w=$w, Height=$oldh=>$h")
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    var x0 = 0
    var y0 = 0
    private var time0: Long = 0

    init {
        candidatePaint = Paint()
        candidatePaint.isAntiAlias = true
        candidatePaint.strokeWidth = 0f
        separatorPaint = Paint()
        separatorPaint.color = Color.BLACK
        reset()
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
                val i = getTabIndex(x, y)
                if (i > -1) {
                    performClick()
                    val tag = TabManager.getTag(i)
                    if (tag.type == SymbolKeyboardType.NO_KEY) {
                        when (tag.command) {
                            KeyCommandType.EXIT -> Trime.getService().selectLiquidKeyboard(-1)
                            KeyCommandType.DEL_LEFT, KeyCommandType.DEL_RIGHT, KeyCommandType.REDO, KeyCommandType.UNDO -> {}
                            else -> {}
                        }
                    } else if (System.currentTimeMillis() - time0 < 500) {
                        highlightIndex = i
                        invalidate()
                        Trime.getService().selectLiquidKeyboard(i)
                    }
                    Timber.d("index=" + i + " length=" + tabTags!!.size)
                }
            }
        }
        return true
    }

    /**
     * 獲得觸摸處候選項序號
     *
     * @param x 觸摸點橫座標
     * @param y 觸摸點縱座標
     * @return `>=0`: 觸摸點 (x, y) 處候選項序號，從0開始編號； `-1`: 觸摸點 (x, y) 處無候選項；
     */
    private fun getTabIndex(
        x: Int,
        y: Int,
    ): Int {
        // Rect r = new Rect();
        var retIndex = -1 // Returns -1 if there is no tab in the hitting rectangle.
        for (computedTab in tabTags!!) {
            /* Enlarge the rectangle to be more responsive to user clicks.
      // r.set(tabGeometries[j++]);
      //r.inset(0, CANDIDATE_TOUCH_OFFSET); */
            if (computedTab.geometry.contains(x, y)) {
                retIndex = tabTags!!.indexOf(computedTab)
            }
        }
        return retIndex
    }

    private fun getTabWidth(i: Int): Float {
        val s = tabTags!![i].text
        return if (s.isNotEmpty()) {
            2 * candidatePadding + candidatePaint.measureText(s, candidateFont!!)
        } else {
            (2 * candidatePadding).toFloat()
        }
    }
}
