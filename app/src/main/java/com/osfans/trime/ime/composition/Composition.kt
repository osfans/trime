// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.EventManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.model.CompositionComponent
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.Event
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.text.Candidate
import com.osfans.trime.ime.text.TextInputManager
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import timber.log.Timber

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置  */
@SuppressLint("AppCompatCustomView")
class Composition(context: Context, attrs: AttributeSet?) : TextView(context, attrs) {
    private val theme = ThemeManager.activeTheme
    private val textInputManager = TextInputManager.instanceOrNull()

    private val keyTextSize = theme.generalStyle.keyTextSize
    private val keyTextColor = ColorManager.getColor("key_text_color")!!
    private val candidateUseCursor = theme.generalStyle.candidateUseCursor
    private val movable = Movable.fromString(theme.generalStyle.layout.movable)
    private val showComment = !Rime.getOption("_hide_comment")
    private val maxEntries =
        theme.generalStyle.layout.maxEntries.takeIf { it > 0 }
            ?: Candidate.MAX_CANDIDATE_COUNT

    private val windowComponents = theme.generalStyle.window ?: listOf()
    private val liquidWindowComponents = theme.generalStyle.liquidKeyboardWindow ?: listOf()

    private var highlightIndex = 0
    private val compositionPos = IntArray(2)
    private val movePos = IntArray(2)

    private var ss: SpannableStringBuilder? = null
    private var firstMove = true
    private var mDx = 0f
    private var mDy = 0f
    private var mCurrentX = 0
    private var mCurrentY = 0
    private var candidateNum = 0
    private val allPhrases = theme.generalStyle.layout.allPhrases

    private var isToolbarMode = false

    private val stickyLines: Int
        get() =
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> theme.generalStyle.layout.stickyLinesLand
                else -> theme.generalStyle.layout.stickyLines
            }

    private enum class Movable {
        ALWAYS,
        NEVER,
        ONCE,
        ;

        companion object {
            fun fromString(string: String): Movable {
                return runCatching {
                    when (string) {
                        "true" -> ALWAYS
                        "false" -> NEVER
                        "once" -> ONCE
                        else -> valueOf(string)
                    }
                }.getOrDefault(NEVER)
            }
        }
    }

    private inner class CompositionSpan : UnderlineSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = FontManager.getTypeface("text_font")
            ds.color = ColorManager.getColor("text_color")!!
            ds.bgColor = ColorManager.getColor("back_color")!!
        }
    }

    private inner class CandidateSpan(
        private val index: Int,
        private val typeface: Typeface?,
        private val highlightTextColor: Int,
        private val highlightBackColor: Int,
        private val textColor: Int,
    ) : ClickableSpan() {
        override fun onClick(tv: View) {
            textInputManager?.onCandidatePressed(index)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.isUnderlineText = false
            ds.typeface = typeface
            if (index == highlightIndex) {
                ds.color = highlightTextColor
                ds.bgColor = highlightBackColor
            } else {
                ds.color = textColor
            }
        }
    }

    private inner class EventSpan(private val event: Event) : ClickableSpan() {
        override fun onClick(tv: View) {
            textInputManager?.onPress(event.code)
            textInputManager?.onEvent(event)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.isUnderlineText = false
            ds.color = keyTextColor
            ColorManager.getColor("key_back_color")?.let {
                ds.bgColor = it
            }
        }
    }

    /**
     * @param letterSpacing 字符間距
     */
    class LetterSpacingSpan(private val letterSpacing: Float) : UnderlineSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.letterSpacing = letterSpacing
        }
    }

    init {
        setLineSpacing(
            theme.generalStyle.layout.lineSpacing.toFloat(),
            theme.generalStyle.layout.lineSpacingMultiplier.coerceAtLeast(1f),
        )
        val marginX = dp(theme.generalStyle.layout.marginX)
        val marginY = dp(theme.generalStyle.layout.marginY)
        val marginBottom = dp(theme.generalStyle.layout.marginBottom)
        setPadding(marginX, marginY, marginX, marginBottom)

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
        val action = event.action
        if (isToolbarMode) return super.onTouchEvent(event)

        if (action == MotionEvent.ACTION_UP) {
            var n = getOffsetForPosition(event.x, event.y)
            if (n in compositionPos[0]..compositionPos[1]) {
                val s =
                    text
                        .toString()
                        .substring(n, compositionPos[1])
                        .replace(" ", "")
                        .replace("‸", "")
                n = Rime.getRimeRawInput()!!.length - s.length // 從右側定位
                Rime.setCaretPos(n)
                TrimeInputMethodService.getService().updateComposing()
                return true
            }
        } else if (movable != Movable.NEVER &&
            (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN)
        ) {
            val n = getOffsetForPosition(event.x, event.y)
            if (n in movePos[0]..movePos[1]) {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (firstMove || movable == Movable.ONCE) {
                        firstMove = false
                        getLocationOnScreen(intArrayOf(mCurrentX, mCurrentY))
                    }
                    mDx = mCurrentX - event.rawX
                    mDy = mCurrentY - event.rawY
                } else { // MotionEvent.ACTION_MOVE
                    mCurrentX = (event.rawX + mDx).toInt()
                    mCurrentY = (event.rawY + mDy).toInt()
                    TrimeInputMethodService.getService().updatePopupWindow(mCurrentX, mCurrentY)
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun getAlign(m: CompositionComponent): Any {
        var i = Layout.Alignment.ALIGN_NORMAL
        if (m.align.isNotBlank()) {
            when (m.align) {
                "left", "normal" -> i = Layout.Alignment.ALIGN_NORMAL
                "right", "opposite" -> i = Layout.Alignment.ALIGN_OPPOSITE
                "center" -> i = Layout.Alignment.ALIGN_CENTER
            }
        }
        return AlignmentSpan.Standard(i)
    }

    private fun appendComposition(m: CompositionComponent) {
        val r = Rime.composition!!
        val s = r.preedit
        var start: Int
        var end: Int
        var sep = m.start
        if (sep.isNotEmpty()) {
            start = ss!!.length
            ss!!.append(sep)
            end = ss!!.length
            ss!!.setSpan(getAlign(m), start, end, 0)
        }
        start = ss!!.length
        ss!!.append(s)
        end = ss!!.length
        ss!!.setSpan(getAlign(m), start, end, 0)
        compositionPos[0] = start
        compositionPos[1] = end
        ss!!.setSpan(CompositionSpan(), start, end, 0)
        val textSize = sp(theme.generalStyle.textSize)
        ss!!.setSpan(AbsoluteSizeSpan(textSize), start, end, 0)
        m.letterSpacing.toFloat().takeIf { it > 0 }
            ?.let { ss?.setSpan(LetterSpacingSpan(it), start, end, 0) }
        start = compositionPos[0] + r.selStart
        end = compositionPos[0] + r.selEnd
        ss!!.setSpan(ForegroundColorSpan(ColorManager.getColor("hilited_text_color")!!), start, end, 0)
        ss!!.setSpan(BackgroundColorSpan(ColorManager.getColor("hilited_back_color")!!), start, end, 0)
        sep = m.end
        if (sep.isNotEmpty()) ss!!.append(sep)
    }

    /**
     * 计算悬浮窗显示候选词后，候选栏从第几个候选词开始展示 注意当 all_phrases==true 时，悬浮窗显示的候选词数量和候选栏从第几个开始，是不一致的
     *
     * @param minLength 候选词长度大于设定，才会显示到悬浮窗中
     * @param minCheck 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
     * @return j
     */
    private fun calcStartNum(
        minLength: Int,
        minCheck: Int,
    ): Int {
        Timber.d("setWindow calcStartNum() getCandidates")
        val candidates = Rime.candidatesOrStatusSwitches
        if (candidates.isEmpty()) return 0
        Timber.d("setWindow calcStartNum() getCandidates finish, size=%s", candidates.size)
        var j = if (minCheck > maxEntries) maxEntries - 1 else minCheck - 1
        if (j >= candidates.size) j = candidates.size - 1
        while (j >= 0) {
            val cand = candidates[j].text
            if (cand.length >= minLength) break
            j--
        }
        if (j < 0) j = 0
        while (j < maxEntries && j < candidates.size) {
            val cand = candidates[j].text
            if (cand.length < minLength) {
                return j
            }
            j++
        }
        return j
    }

    /** 生成悬浮窗内的文本  */
    private fun appendCandidates(
        m: CompositionComponent,
        length: Int,
        endNum: Int,
    ) {
        Timber.d("appendCandidates(): length = %s", length)
        var start: Int
        var end: Int
        val candidates = Rime.candidatesOrStatusSwitches
        if (candidates.isEmpty()) return
        val prefix = m.start
        highlightIndex = if (candidateUseCursor) Rime.candHighlightIndex else -1
        val labelFormat = m.label
        val candidateFormat = m.candidate
        val commentFormat = m.comment
        val line = m.sep
        var lineLength = 0
        val labels = Rime.selectLabels
        var i = -1
        candidateNum = 0
        val maxLength = theme.generalStyle.layout.maxLength
        val hilitedCandidateBackColor = ColorManager.getColor("hilited_candidate_back_color")!!
        for (o in candidates) {
            var cand = o.text
            i++
            if (candidateNum >= maxEntries) break
            if (!allPhrases && candidateNum >= endNum) break
            if (allPhrases && cand.length < length) {
                continue
            }
            cand = String.format(candidateFormat, cand)
            val lineSep =
                if (candidateNum == 0) {
                    prefix
                } else if (stickyLines > 0 && stickyLines >= i || maxLength > 0 && lineLength + cand.length > maxLength) {
                    "\n".also { lineLength = 0 }
                } else {
                    line
                }
            if (lineSep.isNotEmpty()) {
                start = ss!!.length
                ss!!.append(lineSep)
                end = ss!!.length
                ss!!.setSpan(getAlign(m), start, end, 0)
            }
            if (labelFormat.isNotEmpty() && labels.isNotEmpty()) {
                val label = String.format(labelFormat, labels[i])
                start = ss!!.length
                ss!!.append(label)
                end = ss!!.length
                ss!!.setSpan(
                    CandidateSpan(
                        i,
                        FontManager.getTypeface("label_font"),
                        ColorManager.getColor("hilited_label_color")!!,
                        hilitedCandidateBackColor,
                        ColorManager.getColor("label_color")!!,
                    ),
                    start,
                    end,
                    0,
                )
                val labelTextSize = sp(theme.generalStyle.labelTextSize)
                ss!!.setSpan(AbsoluteSizeSpan(labelTextSize), start, end, 0)
            }
            start = ss!!.length
            ss!!.append(cand)
            end = ss!!.length
            lineLength += cand.length
            ss!!.setSpan(getAlign(m), start, end, 0)
            ss!!.setSpan(
                CandidateSpan(
                    i,
                    FontManager.getTypeface("candidate_font"),
                    ColorManager.getColor("hilited_candidate_text_color")!!,
                    hilitedCandidateBackColor,
                    ColorManager.getColor("candidate_text_color")!!,
                ),
                start,
                end,
                0,
            )
            val candidateTextSize = sp(theme.generalStyle.candidateTextSize)
            ss!!.setSpan(AbsoluteSizeSpan(candidateTextSize), start, end, 0)
            if (showComment && commentFormat.isNotEmpty() && o.comment.isNotEmpty()) {
                val comment = String.format(commentFormat, o.comment)
                start = ss!!.length
                ss!!.append(comment)
                end = ss!!.length
                ss!!.setSpan(getAlign(m), start, end, 0)
                ss!!.setSpan(
                    CandidateSpan(
                        i,
                        FontManager.getTypeface("comment_font"),
                        ColorManager.getColor("hilited_comment_text_color")!!,
                        hilitedCandidateBackColor,
                        ColorManager.getColor("comment_text_color")!!,
                    ),
                    start,
                    end,
                    0,
                )
                val commentTextSize = sp(theme.generalStyle.commentTextSize)
                ss!!.setSpan(AbsoluteSizeSpan(commentTextSize), start, end, 0)
                lineLength += comment.length
            }
            candidateNum++
        }
        val suffix = m.end
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    private fun appendButton(m: CompositionComponent) {
        if (m.whenStr.isNotBlank()) {
            val `when` = m.whenStr
            if (`when`.contentEquals("paging") && !Rime.hasLeft()) return
            if (`when`.contentEquals("has_menu") && !Rime.hasMenu()) return
        }
        val e = EventManager.getEvent(m.click)
        val label =
            if (m.label.isNotBlank()) {
                m.label
            } else {
                e.getLabel(KeyboardSwitcher.currentKeyboard)
            }
        var start: Int
        var end: Int
        val prefix = m.start
        if (prefix.isNotEmpty()) {
            start = ss!!.length
            ss!!.append(prefix)
            end = ss!!.length
            ss!!.setSpan(getAlign(m), start, end, 0)
        }
        start = ss!!.length
        ss!!.append(label)
        end = ss!!.length
        ss!!.setSpan(getAlign(m), start, end, 0)
        ss!!.setSpan(EventSpan(e), start, end, 0)
        ss!!.setSpan(AbsoluteSizeSpan(sp(keyTextSize)), start, end, 0)
        val suffix = m.end
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    private fun appendMove(m: CompositionComponent) {
        val s = m.move
        var start: Int
        var end: Int
        val prefix = m.start
        if (prefix.isNotEmpty()) {
            start = ss!!.length
            ss!!.append(prefix)
            end = ss!!.length
            ss!!.setSpan(getAlign(m), start, end, 0)
        }
        start = ss!!.length
        ss!!.append(s)
        end = ss!!.length
        ss!!.setSpan(getAlign(m), start, end, 0)
        movePos[0] = start
        movePos[1] = end
        ss!!.setSpan(AbsoluteSizeSpan(sp(keyTextSize)), start, end, 0)
        ss!!.setSpan(ForegroundColorSpan(keyTextColor), start, end, 0)
        val suffix = m.end
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    /**
     * 设置悬浮窗文本
     *
     * @return 悬浮窗显示的候选词数量
     */
    fun setWindowContent(): Int {
        if (visibility != VISIBLE) return 0
        Rime.composition?.preedit?.takeIf { it.isNotBlank() } ?: return 0
        isSingleLine = true // 設置單行
        ss = SpannableStringBuilder()
        var startNum = 0
        val minLength = theme.generalStyle.layout.minLength // 候选词长度大于设定，才会显示到悬浮窗中
        val minCheck = theme.generalStyle.layout.minCheck // 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
        for (m in windowComponents) {
            if (m.composition.isNotBlank()) {
                appendComposition(m)
            } else if (m.candidate.isNotBlank()) {
                startNum = calcStartNum(minLength, minCheck)
                Timber.d("start_num = %s, min_length = %s, min_check = %s", startNum, minLength, minCheck)
                appendCandidates(m, minLength, startNum)
            } else if (m.click.isNotBlank()) {
                appendButton(m)
            } else if (m.move.isNotBlank()) {
                appendMove(m)
            }
        }
        if (candidateNum > 0 || ss.toString().contains("\n")) isSingleLine = false // 設置單行
        text = ss
        movementMethod = LinkMovementMethod.getInstance()
        isToolbarMode = false
        return startNum
    }
}
