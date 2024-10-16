// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ScaleXSpan
import android.text.style.UnderlineSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.CompositionComponent
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.Event
import com.osfans.trime.ime.keyboard.KeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import kotlin.math.absoluteValue

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置  */
@SuppressLint("AppCompatCustomView")
class Composition(
    context: Context,
    private val theme: Theme,
) : TextView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val keyTextSize = theme.generalStyle.keyTextSize
    private val labelTextSize = theme.generalStyle.labelTextSize
    private val candidateTextSize = theme.generalStyle.candidateTextSize
    private val commentTextSize = theme.generalStyle.commentTextSize
    private val textColor = ColorManager.getColor("text_color")
    private val backColor = ColorManager.getColor("back_color")
    private val keyTextColor = ColorManager.getColor("key_text_color")
    private val keyBackColor = ColorManager.getColor("key_back_color")
    private val labelColor = ColorManager.getColor("label_color")
    private val candidateTextColor = ColorManager.getColor("candidate_text_color")
    private val commentTextColor = ColorManager.getColor("comment_text_color")
    private val highlightTextColor = ColorManager.getColor("hilited_text_color")
    private val highlightBackColor = ColorManager.getColor("hilited_back_color")
    private val highlightLabelColor = ColorManager.getColor("hilited_label_color")
    private val highlightCommentTextColor = ColorManager.getColor("hilited_comment_text_color")
    private val highlightCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")
    private val highlightCandidateBackColor = ColorManager.getColor("hilited_candidate_back_color")
    private val candidateUseCursor = theme.generalStyle.candidateUseCursor
    private val movable = Movable.fromString(theme.generalStyle.layout.movable)
    private val showComment = !Rime.getOption("_hide_comment")
    private val allPhrases = theme.generalStyle.layout.allPhrases
    private val maxCount =
        theme.generalStyle.layout.maxEntries
            .takeIf { it > 0 }
            ?: 30
    private val maxLength = theme.generalStyle.layout.maxLength
    private val minCheckLength = theme.generalStyle.layout.minLength // 候选词长度大于设定，才会显示到悬浮窗中
    private val minCheckCount = theme.generalStyle.layout.minCheck // 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词

    private val windowComponents = theme.generalStyle.window

    private var highlightIndex: Int = -1
    private val preeditRange = intArrayOf(0, 0)
    private val movableRange = intArrayOf(0, 0)

    private val keyTextSizeSpan by lazy { AbsoluteSizeSpan(sp(keyTextSize)) }
    private val labelTextSizeSpan by lazy { AbsoluteSizeSpan(sp(labelTextSize)) }
    private val candidateTextSizeSpan by lazy { AbsoluteSizeSpan(sp(candidateTextSize)) }
    private val commentTextSizeSpan by lazy { AbsoluteSizeSpan(sp(commentTextSize)) }
    private val keyTextColorSpan by lazy { keyTextColor?.let { ForegroundColorSpan(it) } }
    private val highlightTextColorSpan by lazy { highlightTextColor?.let { ForegroundColorSpan(it) } }
    private val highlightBackColorSpan by lazy { highlightBackColor?.let { BackgroundColorSpan(it) } }

    private var firstMove = true
    private var deltaX = 0f
    private var deltaY = 0f
    private var initialX = 0f
    private var initialY = 0f
    private var onActionMove: ((Float, Float) -> Unit)? = null
    private var onSelectCandidate: ((Int) -> Unit)? = null
    private var keyboardActionListener: KeyboardActionListener? = null

    private val stickyLines: Int
        get() =
            if (context.isLandscapeMode()) {
                theme.generalStyle.layout.stickyLinesLand
            } else {
                theme.generalStyle.layout.stickyLines
            }

    private enum class Movable {
        ALWAYS,
        NEVER,
        ONCE,
        ;

        companion object {
            fun fromString(string: String): Movable =
                runCatching {
                    when (string) {
                        "true" -> ALWAYS
                        "false" -> NEVER
                        "once" -> ONCE
                        else -> valueOf(string)
                    }
                }.getOrDefault(NEVER)
        }
    }

    private inner class CompositionSpan : UnderlineSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = FontManager.getTypeface("text_font")
            textColor?.let { ds.color = it }
            backColor?.let { ds.bgColor = it }
        }
    }

    private inner class CandidateSpan(
        private val index: Int,
        private val typeface: Typeface?,
        private val highlightTextColor: Int?,
        private val highlightBackColor: Int?,
        private val textColor: Int?,
        private val textSize: Int,
    ) : ClickableSpan() {
        override fun onClick(tv: View) {
            onSelectCandidate?.invoke(index)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.textSize = sp(textSize).toFloat()
            ds.isUnderlineText = false
            ds.typeface = typeface
            if (index == highlightIndex) {
                highlightTextColor?.let { ds.color = it }
                highlightBackColor?.let { ds.bgColor = it }
            } else {
                textColor?.let { ds.color = it }
            }
        }
    }

    fun setOnSelectCandidateListener(listener: ((Int) -> Unit)?) {
        onSelectCandidate = listener
    }

    fun setKeyboardActionListener(listener: KeyboardActionListener?) {
        keyboardActionListener = listener
    }

    private inner class EventSpan(
        private val event: Event,
    ) : ClickableSpan() {
        override fun onClick(tv: View) {
            keyboardActionListener?.onPress(event.code)
            keyboardActionListener?.onEvent(event)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.isUnderlineText = false
            keyTextColor?.let { ds.color = it }
            keyBackColor?.let { ds.bgColor = it }
        }
    }

    private fun alignmentSpan(alignment: String): AlignmentSpan {
        val align =
            when (alignment) {
                "right", "opposite" -> Layout.Alignment.ALIGN_OPPOSITE
                "center" -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_NORMAL // "left", "normal" or else
            }
        return AlignmentSpan.Standard(align)
    }

    init {
        setLineSpacing(
            theme.generalStyle.layout.lineSpacing
                .toFloat(),
            theme.generalStyle.layout.lineSpacingMultiplier
                .coerceAtLeast(1f),
        )

        minWidth = dp(theme.generalStyle.layout.minWidth)
        minHeight = dp(theme.generalStyle.layout.minHeight)
        val displayMetrics = resources.displayMetrics
        val realMargin = dp(theme.generalStyle.layout.realMargin)
        maxWidth = dp(theme.generalStyle.layout.maxWidth)
            .coerceAtMost(displayMetrics.widthPixels) - realMargin * 2
        maxHeight =
            dp(theme.generalStyle.layout.maxHeight)
                .coerceAtMost(displayMetrics.heightPixels)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touched = getOffsetForPosition(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (movable != Movable.NEVER) {
                    if (touched in movableRange[0]..movableRange[1]) {
                        if (firstMove || movable == Movable.ONCE) {
                            initialX = event.rawX
                            initialY = event.rawY
                            firstMove = false
                        }
                        deltaX = initialX - event.rawX
                        deltaY = initialY - event.rawY
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (movable != Movable.NEVER) {
                    if (touched in movableRange[0]..movableRange[1]) {
                        initialX = event.rawX + deltaX
                        initialY = event.rawY + deltaY
                        val absX = initialX.absoluteValue
                        val absY = initialY.absoluteValue
                        if (absX > touchSlop || absY > touchSlop) {
                            onActionMove?.invoke(initialX, initialY)
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (touched in preeditRange[0]..preeditRange[1]) {
                    val s =
                        text
                            .toString()
                            .substring(touched, preeditRange[1])
                            .replace(" ", "")
                            .replace("‸", "")
                    val newPos = Rime.getRimeRawInput()!!.length - s.length // 從右側定位
                    Rime.setCaretPos(newPos)
                    TrimeInputMethodService.getService().updateComposing()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun setOnActionMoveListener(listener: ((Float, Float) -> Unit)? = null) {
        onActionMove = listener
    }

    private fun SpannableStringBuilder.buildSpannedComposition(
        m: CompositionComponent,
        composition: RimeProto.Context.Composition,
    ) {
        if (composition.length <= 0) return
        val alignmentSpan = alignmentSpan(m.align)
        if (m.start.isNotEmpty()) {
            inSpans(alignmentSpan) { append(m.start) }
        }
        preeditRange[0] = length
        inSpans(
            alignmentSpan,
            CompositionSpan(),
            AbsoluteSizeSpan(sp(theme.generalStyle.textSize)),
        ) { append(composition.preedit) }
        if (m.letterSpacing > 0) {
            setSpan(ScaleXSpan(m.letterSpacing.toFloat()), preeditRange[0], length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        preeditRange[1] = length

        val selStart = preeditRange[0] + composition.selStart
        val selEnd = preeditRange[0] + composition.selEnd
        highlightTextColorSpan?.let { setSpan(it, selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE) }
        highlightBackColorSpan?.let { setSpan(it, selStart, selEnd, Spanned.SPAN_INCLUSIVE_EXCLUSIVE) }
        if (m.end.isNotEmpty()) {
            append(m.end)
        }
    }

    /** 生成悬浮窗内的文本  */
    private fun SpannableStringBuilder.buildSpannedCandidates(
        m: CompositionComponent,
        candidates: Array<RimeProto.Candidate>,
        offset: Int,
    ) {
        if (candidates.isEmpty()) return
        var currentLineLength = 0
        val alignmentSpan = alignmentSpan(m.align)
        for ((i, candidate) in candidates.withIndex()) {
            val text = String.format(m.candidate, candidate.text)
            val label = String.format(m.label, candidate.label)
            if (i >= maxCount) break
            if (!allPhrases && i >= offset) break
            if (allPhrases && text.length < minCheckLength) {
                continue
            }

            val lineSep =
                if (i == 0) {
                    m.start
                } else if (i <= stickyLines || currentLineLength + text.length > maxLength) {
                    "\n".also { currentLineLength = 0 }
                } else {
                    m.sep
                }
            inSpans(alignmentSpan) { append(lineSep) }

            val labelSpan =
                CandidateSpan(
                    i,
                    FontManager.getTypeface("label_font"),
                    highlightLabelColor,
                    highlightCandidateBackColor,
                    labelColor,
                    labelTextSize,
                )
            inSpans(alignmentSpan, labelSpan, labelTextSizeSpan) { append(label) }

            val candidateSpan =
                CandidateSpan(
                    i,
                    FontManager.getTypeface("candidate_font"),
                    highlightCandidateTextColor,
                    highlightCandidateBackColor,
                    candidateTextColor,
                    candidateTextSize,
                )
            inSpans(alignmentSpan, candidateSpan, candidateTextSizeSpan) { append(text) }
            currentLineLength += text.length

            if (showComment && !candidate.comment.isNullOrEmpty()) {
                val comment = String.format(m.comment, candidate.comment)
                val commentSpan =
                    CandidateSpan(
                        i,
                        FontManager.getTypeface("comment_font"),
                        highlightCommentTextColor,
                        highlightCandidateBackColor,
                        commentTextColor,
                        commentTextSize,
                    )
                inSpans(alignmentSpan, commentSpan, commentTextSizeSpan) { append(comment) }
                currentLineLength += comment.length
            }
        }
        inSpans(alignmentSpan) { append(m.end) }
    }

    private fun SpannableStringBuilder.buildSpannedMove(m: CompositionComponent) {
        val alignmentSpan = alignmentSpan(m.align)
        val moveSpans =
            listOf(alignmentSpan, keyTextSizeSpan, keyTextColorSpan)
                .mapNotNull { it }
                .toTypedArray()

        inSpans(alignmentSpan) { append(m.start) }
        inSpans(*moveSpans) {
            movableRange[0] = length
            append(m.move)
            movableRange[1] = length
        }
        inSpans(alignmentSpan) { append(m.end) }
    }

    /**
     * 计算悬浮窗显示候选词后，候选栏从第几个候选词开始展示 注意当 all_phrases==true 时，悬浮窗显示的候选词数量和候选栏从第几个开始，是不一致的
     */
    private fun calculateOffset(candidates: List<String>): Int {
        if (candidates.isEmpty()) return 0
        var j = (minOf(minCheckCount, candidates.size, maxCount) - 1).coerceAtLeast(0)
        while (j > 0) {
            val text = candidates[j]
            if (text.length >= minCheckLength) break
            j--
        }
        while (j < minOf(maxCount, candidates.size)) {
            val text = candidates[j]
            if (text.length < minCheckLength) {
                return j
            }
            j++
        }
        return j
    }

    /**
     * 设置悬浮窗文本
     *
     * @return 悬浮窗显示的候选词数量
     */
    fun update(ctx: RimeProto.Context): Int {
        if (visibility != VISIBLE) return 0
        ctx.composition.preedit.takeIf { !it.isNullOrEmpty() } ?: return 0
        val candidates = ctx.menu.candidates
        val startNum = calculateOffset(candidates.map { it.text })
        if (candidateUseCursor) {
            highlightIndex = ctx.menu.highlightedCandidateIndex
        }
        val content =
            buildSpannedString {
                for (component in windowComponents) {
                    when {
                        component.move.isNotBlank() -> buildSpannedMove(component)
                        component.composition.isNotBlank() ->
                            buildSpannedComposition(
                                component,
                                ctx.composition,
                            )
                        component.candidate.isNotBlank() ->
                            buildSpannedCandidates(
                                component,
                                candidates,
                                startNum,
                            )
                    }
                }
            }
        isSingleLine = startNum == 0
        text = content
        movementMethod = LinkMovementMethod.getInstance()
        return startNum
    }
}
