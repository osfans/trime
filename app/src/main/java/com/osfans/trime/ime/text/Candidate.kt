// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.text

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.PaintDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.osfans.trime.core.CandidateListItem
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.GraphicUtils.drawText
import com.osfans.trime.util.GraphicUtils.measureText
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import java.lang.ref.WeakReference
import kotlin.math.max

/** 顯示候選字詞  */
class Candidate(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    /** 處理候選條選字事件  */
    interface EventListener {
        fun onCandidatePressed(index: Int)

        fun onCandidateSymbolPressed(arrow: String)

        fun onCandidateLongClicked(index: Int)
    }

    private val theme = ThemeManager.activeTheme

    // private var expectWidth = 0
    private var listener = WeakReference<EventListener?>(null)
    private var highlightIndex = -1
    private var candidates: Array<CandidateListItem>? = null
    private val computedCandidates = ArrayList<ComputedCandidate>(MAX_CANDIDATE_COUNT)
    private var startNum = 0
    private var timeDown: Long = 0
    private var timeMove: Long = 0
    private val candidateHighlight =
        PaintDrawable(ColorManager.getColor("hilited_candidate_back_color")!!).apply {
            setCornerRadius(theme.generalStyle.layout.roundCorner.toFloat())
        }
    private val separatorPaint =
        Paint().apply {
            color = ColorManager.getColor("candidate_separator_color")!!
        }
    private val candidateFont = FontManager.getTypeface("candidate_font")
    private val symbolFont = FontManager.getTypeface("symbol_font")
    private val commentFont = FontManager.getTypeface("comment_font")
    private val candidatePaint =
        Paint().apply {
            typeface = candidateFont
            theme.generalStyle.candidateTextSize.toFloat().takeIf { it > 0 }?.let { textSize = sp(it) }
            isAntiAlias = true
            strokeWidth = 0f
        }
    private val symbolPaint =
        Paint().apply {
            typeface = symbolFont
            theme.generalStyle.candidateTextSize.toFloat().takeIf { it > 0 }?.let { textSize = sp(it) }
            isAntiAlias = true
            strokeWidth = 0f
        }
    private val commentPaint =
        Paint().apply {
            typeface = commentFont
            theme.generalStyle.commentTextSize.toFloat().takeIf { it > 0 }?.let { textSize = sp(it) }
            isAntiAlias = true
            strokeWidth = 0f
        }
    private val candidateTextColor = ColorManager.getColor("candidate_text_color")!!
    private val hilitedCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")!!
    private val commentTextColor = ColorManager.getColor("comment_text_color")!!
    private val hilitedCommentTextColor = ColorManager.getColor("hilited_comment_text_color")!!
    private val candidateViewHeight = dp(theme.generalStyle.candidateViewHeight)
    private val commentHeight = dp(theme.generalStyle.commentHeight)
    private val candidateSpacing = dp(theme.generalStyle.candidateSpacing).toInt()
    private val candidatePadding = dp(theme.generalStyle.candidatePadding)
    var shouldShowComment = true
    private val isCommentOnTop = theme.generalStyle.commentOnTop
    private val candidateUseCursor = theme.generalStyle.candidateUseCursor
    private val prefs = AppPrefs.defaultInstance().keyboard

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
        updateCandidateData()
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

        val candidate = computedCandidates[index]
        if (candidate is ComputedCandidate.Word) {
            if (listener.get() != null) {
                if (isLongClick && prefs.shouldLongClickDeleteCandidate) {
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
            return if (highlightIndex in computedCandidates.indices) {
                computedCandidates[highlightIndex].geometry.left
            } else {
                0
            }
        }
    val highlightRight: Int
        get() {
            return if (highlightIndex in computedCandidates.indices) {
                computedCandidates[highlightIndex].geometry.right
            } else {
                0
            }
        }

    override fun onDraw(canvas: Canvas) {
        if (candidates == null) return
        super.onDraw(canvas)
        // 是否水平居中显示(不带编码提示的)候选项
        var isAlign = true
        if (isCommentOnTop) {
            candidates?.forEachIndexed { index, candidate ->
                if (index < startNum) return@forEachIndexed
                val comment = candidate.comment
                // 只要有一个候选项有编码提示就不会居中
                if (comment.isNotEmpty()) isAlign = false
            }
        }
        computedCandidates.forEachIndexed { index, computedCandidate ->
            // Draw highlight
            if (candidateUseCursor && index == highlightIndex) {
                candidateHighlight.bounds = computedCandidate.geometry
                candidateHighlight.draw(canvas)
            }
            // Draw candidates
            if (computedCandidate is ComputedCandidate.Word) {
                var wordX = computedCandidate.geometry.centerX().toFloat()
                var wordY = (
                    computedCandidates[0].geometry.centerY() -
                        (candidatePaint.ascent() + candidatePaint.descent()) / 2
                )
                if (!isAlign) wordY += commentHeight / 2.0f
                // 绘制编码提示
                val comment = computedCandidate.comment
                if (shouldShowComment && !comment.isNullOrEmpty()) {
                    var commentX = computedCandidate.geometry.centerX().toFloat()
                    var commentY = commentHeight / 2.0f - (commentPaint.ascent() + commentPaint.descent()) / 2
                    if (!isCommentOnTop) {
                        val commentWidth = commentPaint.measureText(comment, commentFont)
                        commentX = computedCandidate.geometry.right - commentWidth / 2
                        commentY += (computedCandidates[0].geometry.bottom - commentHeight).toFloat()
                        wordX -= commentWidth / 2.0f
                    }
                    commentPaint.color = if (isHighlighted(index)) hilitedCommentTextColor else commentTextColor
                    canvas.drawText(comment, commentX, commentY, commentPaint, commentFont)
                }
                // 绘制候选项
                val word = computedCandidate.word
                candidatePaint.color = if (isHighlighted(index)) hilitedCandidateTextColor else candidateTextColor
                canvas.drawText(word, wordX, wordY, candidatePaint, candidateFont)
            } else if (computedCandidate is ComputedCandidate.Symbol) {
                // Draw page up / down buttons
                val arrow = computedCandidate.arrow
                val arrowX = (
                    computedCandidate.geometry.centerX() -
                        symbolPaint.measureText(arrow, symbolFont) / 2
                )
                val arrowY = (
                    computedCandidates[0].geometry.centerY() -
                        (candidatePaint.ascent() + candidatePaint.descent()) / 2
                )
                symbolPaint.color = if (isHighlighted(index)) hilitedCommentTextColor else commentTextColor
                canvas.drawText(arrow, arrowX, arrowY, symbolPaint)
            }
            // Draw separators
            if (index + 1 < computedCandidates.size) {
                canvas.drawRect(
                    (computedCandidate.geometry.right - candidateSpacing).toFloat(),
                    computedCandidate.geometry.height() * 0.2f,
                    (computedCandidate.geometry.right + candidateSpacing).toFloat(),
                    computedCandidate.geometry.height() * 0.8f,
                    separatorPaint,
                )
            }
        }
    }

    private fun updateCandidateData() {
        // TODO 暂时搁置设置里关于候选数量的设定
        // 之后根据 Rime 新的 api 写一个候选管理器再重新启用
        // var hasExButton = false
        // val pageEx = appPrefs.keyboard.candidatePageSize - 10000
        val pageButtonWidth =
            candidateSpacing + 2 * candidatePadding +
                symbolPaint.measureText(PAGE_DOWN_BUTTON, symbolFont).toInt()
        // val minWidth =
        //     if (pageEx > 2) {
        //         (expectWidth * (pageEx / 10f + 1) - pageButtonWidth).toInt()
        //     } else if (pageEx == 2) {
        //         (expectWidth - pageButtonWidth * 2)
        //     } else {
        //         expectWidth - pageButtonWidth
        //     }
        computedCandidates.clear()
        updateCandidates()
        var x = if ((!Rime.hasLeft())) 0 else pageButtonWidth
        candidates?.forEachIndexed { index, candidate ->
            if (index < startNum) return@forEachIndexed
            // if (pageEx >= 0 && x >= minWidth) {
            //     computedCandidates.add(
            //         ComputedCandidate.Symbol(
            //             PAGE_EX_BUTTON,
            //             Rect(x, 0, (x + pageButtonWidth), measuredHeight),
            //         ),
            //     )
            //     x += pageButtonWidth
            //     hasExButton = true
            //     break
            // }
            val text = candidate.text
            val comment = candidate.comment
            var candidateWidth = candidatePaint.measureText(text, (candidateFont)) + 2 * candidatePadding
            if (shouldShowComment && comment.isNotEmpty()) {
                val commentWidth = commentPaint.measureText(comment, (commentFont))
                candidateWidth = if (isCommentOnTop) max(candidateWidth, commentWidth) else candidateWidth + commentWidth
            }

            // 自动填满候选栏，并保障展开候选按钮显示出来
            // if (pageEx == 0 && x + candidateWidth + candidateSpacing > minWidth) {
            //     computedCandidates.add(
            //         ComputedCandidate.Symbol(
            //             PAGE_EX_BUTTON,
            //             Rect(x, 0, (x + pageButtonWidth), measuredHeight),
            //         ),
            //     )
            //     x += pageButtonWidth
            //     hasExButton = true
            //     break
            // }
            computedCandidates.add(
                ComputedCandidate.Word(
                    text,
                    comment,
                    Rect(x, 0, (x + candidateWidth).toInt(), measuredHeight),
                ),
            )
            x += (candidateWidth + candidateSpacing).toInt()
        }
        if (Rime.hasLeft()) {
            computedCandidates.add(
                ComputedCandidate.Symbol(
                    PAGE_UP_BUTTON,
                    Rect(0, 0, pageButtonWidth, measuredHeight),
                ),
            )
        }
        if (Rime.hasRight()) {
            computedCandidates.add(
                ComputedCandidate.Symbol(
                    PAGE_DOWN_BUTTON,
                    Rect(x, 0, (x + pageButtonWidth), measuredHeight),
                ),
            )
            x += pageButtonWidth
        }
        val params = layoutParams
        params.width = x
        params.height = if ((shouldShowComment && isCommentOnTop)) candidateViewHeight + commentHeight else candidateViewHeight
        layoutParams = params
        // Trime.getService().candidateExPage = hasExButton
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCandidateData()
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
                        durationMs >= prefs.deleteCandidateTimeout,
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
        computedCandidates.forEachIndexed { index, computedCandidate ->
            if (computedCandidate.geometry.contains(x, y)) {
                return@getCandidateIndex index
            }
        }
        return -1
    }

    private fun updateCandidates() {
        candidates = Rime.candidatesOrStatusSwitches
        highlightIndex = Rime.candHighlightIndex - startNum
    }

    companion object {
        const val MAX_CANDIDATE_COUNT = 30
        const val PAGE_UP_BUTTON = "◀"
        const val PAGE_DOWN_BUTTON = "▶"
        const val PAGE_EX_BUTTON = "▼"
    }
}
