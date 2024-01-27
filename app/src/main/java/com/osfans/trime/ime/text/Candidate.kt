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
package com.osfans.trime.ime.text

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.PaintDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.osfans.trime.core.CandidateListItem
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.GraphicUtils.drawText
import com.osfans.trime.util.GraphicUtils.measureText
import com.osfans.trime.util.dp2px
import com.osfans.trime.util.sp2px
import java.lang.ref.WeakReference
import kotlin.math.max

/** 顯示候選字詞  */
@Suppress("ktlint:standard:property-naming")
class Candidate(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    /** 處理候選條選字事件  */
    interface EventListener {
        fun onCandidatePressed(index: Int)

        fun onCandidateSymbolPressed(arrow: String)

        fun onCandidateLongClicked(index: Int)
    }

    private var expectWidth = 0
    private var listener = WeakReference<EventListener?>(null)
    private var highlightIndex = -1
    private var candidates: Array<CandidateListItem>? = null
    private val computedCandidates = ArrayList<ComputedCandidate>(maxCandidateCount)
    private var numCandidates = 0
    private var startNum = 0
    private var timeDown: Long = 0
    private var timeMove: Long = 0
    private var candidateHighlight: PaintDrawable? = null
    private val separatorPaint: Paint
    private val candidatePaint: Paint = Paint()
    private val symbolPaint: Paint
    private val commentPaint: Paint
    private var candidateFont: Typeface? = null
    private var symbolFont: Typeface? = null
    private var commentFont: Typeface? = null
    private var candidateTextColor = 0
    private var hilitedCandidateTextColor = 0
    private var commentTextColor = 0
    private var hilitedCommentTextColor = 0
    private var candidateViewHeight = 0
    private var commentHeight = 0
    private var candidateSpacing = 0
    private var candidatePadding = 0
    private var shouldShowComment = true
    private var isCommentOnTop = false
    private var candidateUseCursor = false
    private val appPrefs: AppPrefs
        get() = AppPrefs.defaultInstance()

    fun reset() {
        val theme = ThemeManager.activeTheme
        candidateHighlight = PaintDrawable(theme.colors.getColor("hilited_candidate_back_color")!!)
        candidateHighlight!!.setCornerRadius(theme.style.getFloat("layout/round_corner"))
        separatorPaint.color = (theme.colors.getColor("candidate_separator_color"))!!
        candidateSpacing = dp2px(theme.style.getFloat("candidate_spacing")).toInt()
        candidatePadding = dp2px(theme.style.getFloat("candidate_padding")).toInt()
        candidateTextColor = theme.colors.getColor("candidate_text_color")!!
        commentTextColor = theme.colors.getColor("comment_text_color")!!
        hilitedCandidateTextColor = theme.colors.getColor("hilited_candidate_text_color")!!
        hilitedCommentTextColor = theme.colors.getColor("hilited_comment_text_color")!!
        val candidateTextSize = sp2px(theme.style.getFloat("candidate_text_size")).toInt()
        val commentTextSize = sp2px(theme.style.getFloat("comment_text_size")).toInt()
        candidateViewHeight = dp2px(theme.style.getFloat("candidate_view_height")).toInt()
        commentHeight = dp2px(theme.style.getFloat("comment_height")).toInt()
        candidateFont = FontManager.getTypeface(theme.style.getString("candidate_font"))
        commentFont = FontManager.getTypeface(theme.style.getString("comment_font"))
        symbolFont = FontManager.getTypeface(theme.style.getString("symbol_font"))
        candidatePaint.textSize = candidateTextSize.toFloat()
        candidatePaint.setTypeface(candidateFont)
        symbolPaint.textSize = candidateTextSize.toFloat()
        symbolPaint.setTypeface(symbolFont)
        commentPaint.textSize = commentTextSize.toFloat()
        commentPaint.setTypeface(commentFont)
        isCommentOnTop = theme.style.getBoolean("comment_on_top")
        candidateUseCursor = theme.style.getBoolean("candidate_use_cursor")
        invalidate()
    }

    fun setShowComment(value: Boolean) {
        shouldShowComment = value
    }

    init {
        candidatePaint.isAntiAlias = true
        candidatePaint.strokeWidth = 0f
        symbolPaint = Paint()
        symbolPaint.isAntiAlias = true
        symbolPaint.strokeWidth = 0f
        commentPaint = Paint()
        commentPaint.isAntiAlias = true
        commentPaint.strokeWidth = 0f
        separatorPaint = Paint()
        separatorPaint.color = Color.BLACK

        // reset(context);
        setWillNotDraw(false)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val h =
            if (shouldShowComment && isCommentOnTop) {
                candidateViewHeight + commentHeight
            } else {
                candidateViewHeight
            }
        setMeasuredDimension(
            MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST),
        )
    }

    fun setCandidateListener(listener: EventListener?) {
        this.listener = WeakReference(listener)
    }

    /**
     * 刷新候選列表
     *
     * @param start 候選的起始編號
     */
    fun setText(start: Int) {
        startNum = start
        removeHighlight()
        updateCandidateWidth()
        if (updateCandidates() > 0) {
            invalidate()
        }
    }

    fun setExpectWidth(expectWidth: Int) {
        this.expectWidth = expectWidth
    }

    /**
     * 選取候選項
     *
     * @param index 候選項序號（從0開始），`-1`表示選擇當前高亮候選項
     */
    private fun onCandidateClick(
        index: Int,
        isLongClick: Boolean,
    ) {
        if (index < 0 || index >= computedCandidates.size) return

        val candidate: ComputedCandidate = computedCandidates[index]
        if (candidate is ComputedCandidate.Word) {
            if (listener.get() != null) {
                if (isLongClick && appPrefs.keyboard.shouldLongClickDeleteCandidate) {
                    listener.get()!!.onCandidateLongClicked(index + startNum)
                } else {
                    listener.get()!!.onCandidatePressed(index + startNum)
                }
            }
        }
        if (candidate is ComputedCandidate.Symbol) {
            val arrow = candidate.arrow
            if (listener.get() != null) {
                listener.get()!!.onCandidateSymbolPressed(arrow)
            }
        }
    }

    private fun removeHighlight() {
        highlightIndex = -1
        invalidate()
        requestLayout()
    }

    private fun isHighlighted(i: Int): Boolean {
        return candidateUseCursor && i == highlightIndex
    }

    val highlightLeft: Int
        get() {
            return if (highlightIndex < computedCandidates.size && highlightIndex >= 0) {
                computedCandidates[highlightIndex].geometry.left
            } else {
                0
            }
        }
    val highlightRight: Int
        get() {
            return if (highlightIndex < computedCandidates.size && highlightIndex >= 0) {
                computedCandidates[highlightIndex].geometry.right
            } else {
                0
            }
        }

    override fun onDraw(canvas: Canvas) {
        if (candidates == null) return
        super.onDraw(canvas)
        var moveAllCandidatesDown = false
        for (computedCandidate: ComputedCandidate in computedCandidates) {
            val i = computedCandidates.indexOf(computedCandidate)
            // Draw highlight
            if (candidateUseCursor && i == highlightIndex) {
                candidateHighlight!!.bounds = computedCandidates[i].geometry
                candidateHighlight!!.draw(canvas)
            }
            // Draw candidates
            if (computedCandidate is ComputedCandidate.Word) {
                var wordX = computedCandidate.geometry.centerX().toFloat()
                var wordY = (
                    computedCandidates[0].geometry.centerY() -
                        (candidatePaint.ascent() + candidatePaint.descent()) / 2
                )
                if (shouldShowComment) {
                    val comment = computedCandidate.comment
                    moveAllCandidatesDown = moveAllCandidatesDown or !comment.isNullOrEmpty()
                    if (moveAllCandidatesDown) wordY += commentHeight / 2.0f
                    if (!comment.isNullOrEmpty()) {
                        var commentX = computedCandidate.geometry.centerX().toFloat()
                        var commentY = commentHeight / 2.0f - (commentPaint.ascent() + commentPaint.descent()) / 2
                        if (!isCommentOnTop) {
                            val commentWidth = commentPaint.measureText(comment, commentFont!!)
                            commentX = computedCandidate.geometry.right - commentWidth / 2
                            commentY += (computedCandidates[0].geometry.bottom - commentHeight).toFloat()
                            wordX -= commentWidth / 2.0f
                            wordY -= commentHeight / 2.0f
                        }
                        commentPaint.color = if (isHighlighted(i)) hilitedCommentTextColor else commentTextColor
                        canvas.drawText(comment, commentX, commentY, commentPaint, commentFont!!)
                    }
                }
                val word = computedCandidate.word
                candidatePaint.color = if (isHighlighted(i)) hilitedCandidateTextColor else candidateTextColor
                canvas.drawText(word, wordX, wordY, candidatePaint, candidateFont!!)
            } else if (computedCandidate is ComputedCandidate.Symbol) {
                // Draw page up / down buttons
                val arrow = computedCandidate.arrow
                val arrowX = (
                    computedCandidate.geometry.centerX() -
                        symbolPaint.measureText(arrow, symbolFont!!) / 2
                )
                val arrowY = (
                    computedCandidates[0].geometry.centerY() -
                        (candidatePaint.ascent() + candidatePaint.descent()) / 2
                )
                symbolPaint.color = if (isHighlighted(i)) hilitedCommentTextColor else commentTextColor
                canvas.drawText(arrow, arrowX, arrowY, symbolPaint)
            }
            // Draw separators
            if (i + 1 < computedCandidates.size) {
                canvas.drawRect(
                    (
                        computedCandidate.geometry.right - candidateSpacing
                    ).toFloat(),
                    computedCandidate.geometry.height() * 0.2f,
                    (
                        computedCandidate.geometry.right + candidateSpacing
                    ).toFloat(),
                    computedCandidate.geometry.height() * 0.8f,
                    separatorPaint,
                )
            }
        }
    }

    private fun updateCandidateWidth() {
        var hasExButton = false
        val pageEx = appPrefs.keyboard.candidatePageSize.toInt() - 10000
        val pageBottonWidth =
            candidateSpacing + 2 * candidatePadding +
                symbolPaint.measureText(PAGE_DOWN_BUTTON, symbolFont!!).toInt()

        val minWidth: Int =
            if (pageEx > 2) {
                (expectWidth * (pageEx / 10f + 1) - pageBottonWidth).toInt()
            } else if (pageEx == 2) {
                (expectWidth - pageBottonWidth * 2)
            } else {
                expectWidth - pageBottonWidth
            }
        computedCandidates.clear()
        updateCandidates()
        var x = if ((!Rime.hasLeft())) 0 else pageBottonWidth
        for (i in 0 until numCandidates) {
            val n = i + startNum
            if (pageEx >= 0 && x >= minWidth) {
                computedCandidates.add(
                    ComputedCandidate.Symbol(
                        PAGE_EX_BUTTON,
                        Rect(x, 0, (x + pageBottonWidth), measuredHeight),
                    ),
                )
                x += pageBottonWidth
                hasExButton = true
                break
            }
            var comment: String? = null
            val text = candidates!![n].text
            var candidateWidth = candidatePaint.measureText(text, (candidateFont)!!) + 2 * candidatePadding
            if (shouldShowComment) {
                comment = candidates!![n].comment
                if (!TextUtils.isEmpty(comment)) {
                    val commentWidth = commentPaint.measureText(comment, (commentFont)!!)
                    candidateWidth = if (isCommentOnTop) max(candidateWidth, commentWidth) else candidateWidth + commentWidth
                }
            }

            // 自动填满候选栏，并保障展开候选按钮显示出来
            if (pageEx == 0 && x + candidateWidth + candidateSpacing > minWidth) {
                computedCandidates.add(
                    ComputedCandidate.Symbol(
                        PAGE_EX_BUTTON,
                        Rect(x, 0, (x + pageBottonWidth), measuredHeight),
                    ),
                )
                x += pageBottonWidth
                hasExButton = true
                break
            }
            computedCandidates.add(
                ComputedCandidate.Word(
                    text,
                    comment,
                    Rect(x, 0, (x + candidateWidth).toInt(), measuredHeight),
                ),
            )
            x = (x + (candidateWidth + candidateSpacing)).toInt()
        }
        if (Rime.hasLeft()) {
            computedCandidates.add(
                ComputedCandidate.Symbol(
                    PAGE_UP_BUTTON,
                    Rect(0, 0, pageBottonWidth, measuredHeight),
                ),
            )
        }
        if (Rime.hasRight()) {
            computedCandidates.add(
                ComputedCandidate.Symbol(
                    PAGE_DOWN_BUTTON,
                    Rect(x, 0, (x + pageBottonWidth), measuredHeight),
                ),
            )
            x += pageBottonWidth
        }
        val params = layoutParams
        params.width = x
        params.height = if ((shouldShowComment && isCommentOnTop)) candidateViewHeight + commentHeight else candidateViewHeight
        layoutParams = params
        Trime.getService().setCandidateExPage(hasExButton)
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCandidateWidth()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        val x = me.x.toInt()
        val y = me.y.toInt()
        when (me.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                timeDown = System.currentTimeMillis()
                isPressed = true
                highlightIndex = getCandidateIndex(x, y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                isPressed = true
                highlightIndex = getCandidateIndex(x, y)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                timeMove = System.currentTimeMillis()
                val durationMs = timeMove - timeDown
                isPressed = false
                if (me.actionMasked == MotionEvent.ACTION_UP) {
                    onCandidateClick(
                        highlightIndex,
                        durationMs >= appPrefs.keyboard.deleteCandidateTimeout,
                    )
                }
                highlightIndex = -1
                invalidate()
            }
        }
        return true
    }

    /**
     * 獲得觸摸處候選項序號
     *
     * @param x 觸摸點橫座標
     * @param y 觸摸點縱座標
     * @return `>=0`: 觸摸點 (x, y) 處候選項序號，從0開始編號； `-1`: 觸摸點 (x, y) 處無候選項； `-4`: 觸摸點
     * (x, y) 處爲`Page_Up`； `-5`: 觸摸點 (x, y) 處爲`Page_Down`
     */
    private fun getCandidateIndex(
        x: Int,
        y: Int,
    ): Int {
        // Rect r = new Rect();
        var retIndex = -1
        for (computedCandidate: ComputedCandidate in computedCandidates) {
        /*
         * Enlarge the rectangle to be more responsive to user clicks.
         * r.set(candidateRect[j++]);
         * r.inset(0, CANDIDATE_TOUCH_OFFSET);
         */
            if (computedCandidate.geometry.contains(x, y)) {
                retIndex = computedCandidates.indexOf(computedCandidate)
                break
            }
        }
        return retIndex
    }

    private fun updateCandidates(): Int {
        candidates = Rime.candidatesOrStatusSwitches
        highlightIndex = Rime.candHighlightIndex - startNum
        numCandidates = if (candidates == null) 0 else candidates!!.size - startNum
        return numCandidates
    }

    companion object {
        const val maxCandidateCount = 30
        const val PAGE_UP_BUTTON = "◀"
        const val PAGE_DOWN_BUTTON = "▶"
        const val PAGE_EX_BUTTON = "▼"
    }
}
