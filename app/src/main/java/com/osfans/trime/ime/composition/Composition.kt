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
import com.osfans.trime.core.RimeContext
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.EventManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.Trime
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
    private val textInputManager = TextInputManager.getInstance()

    private val keyTextSize = theme.style.getInt("key_text_size")
    private val keyTextColor = ColorManager.getColor("key_text_color")!!
    private val highlightCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")!!
    private val highlightCandidateBackColor = ColorManager.getColor("hilited_candidate_back_color")!!
    private val highlightLabelColor =
        ColorManager.getColor("hilited_label_color")
            ?: highlightCandidateTextColor
    private val candidateUseCursor = theme.style.getBoolean("candidate_use_cursor")
    private val movable = Movable.fromString(theme.style.getString("layout/movable"))
    private val showComment = !Rime.getOption("_hide_comment")
    private val minLength = theme.style.getInt("layout/min_length") // 候选词长度大于设定，才会显示到悬浮窗中
    private val minCheck = theme.style.getInt("layout/min_check") // 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
    private val maxEntries =
        theme.style.getInt("layout/max_entries").takeIf { it > 0 }
            ?: Candidate.MAX_CANDIDATE_COUNT

    @Suppress("UNCHECKED_CAST")
    private val windowComponents =
        WindowComponent
            .decodeFromList(theme.style.getObject("window") as List<Map<String, String?>>? ?: listOf())

    @Suppress("UNCHECKED_CAST")
    private val liquidWindowComponents =
        WindowComponent
            .decodeFromList(theme.style.getObject("liquid_keyboard_window") as List<Map<String, String?>>? ?: listOf())

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
    private val allPhrases = theme.style.getBoolean("layout/all_phrases")

    private var isToolbarMode = false

    private val stickyLines: Int
        get() =
            when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> theme.style.getInt("layout/sticky_lines_land")
                else -> theme.style.getInt("layout/sticky_lines")
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
            textInputManager.onCandidatePressed(index)
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
            textInputManager.onPress(event.code)
            textInputManager.onEvent(event)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.isUnderlineText = false
            ds.color = keyTextColor
            ds.bgColor = ColorManager.getColor("key_back_color")!!
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
            theme.style.getFloat("layout/line_spacing"),
            theme.style.getFloat("layout/line_spacing_multiplier").coerceAtLeast(1f),
        )
        val marginX = dp(theme.style.getInt("layout/margin_x"))
        val marginY = dp(theme.style.getInt("layout/margin_y"))
        val marginBottom = dp(theme.style.getInt("layout/margin_bottom"))
        setPadding(marginX, marginY, marginX, marginBottom)

        minWidth = dp(theme.style.getInt("layout/min_width"))
        minHeight = dp(theme.style.getInt("layout/min_height"))
        val displayMetrics = resources.displayMetrics
        val realMargin = dp(theme.style.getInt("layout/real_margin"))
        maxWidth = dp(theme.style.getInt("layout/max_width"))
            .coerceAtMost(displayMetrics.widthPixels) - realMargin * 2
        maxHeight =
            dp(theme.style.getInt("layout/max_height"))
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
                Trime.getService().updateComposing()
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
                    Trime.getService().updatePopupWindow(mCurrentX, mCurrentY)
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun getAlignSpan(align: String): Any {
        val i =
            when (align) {
                "right", "opposite" -> Layout.Alignment.ALIGN_OPPOSITE
                "center" -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_NORMAL // left, normal and else
            }
        return AlignmentSpan.Standard(i)
    }

    private fun appendComposition(
        c: WindowComponent.Composition,
        context: RimeContext,
    ) {
        val composition = context.composition!!
        val preedit = composition.preedit
        var start: Int
        var end: Int
        val alignSpan = getAlignSpan(c.alignment)
        var sep = c.prefix
        if (sep.isNotEmpty()) {
            start = ss!!.length
            ss!!.append(sep)
            end = ss!!.length
            ss!!.setSpan(alignSpan, start, end, 0)
        }
        start = ss!!.length
        ss!!.append(preedit)
        end = ss!!.length
        ss!!.setSpan(alignSpan, start, end, 0)
        compositionPos[0] = start
        compositionPos[1] = end
        ss!!.setSpan(CompositionSpan(), start, end, 0)
        val textSize = sp(theme.style.getInt("text_size"))
        ss!!.setSpan(AbsoluteSizeSpan(textSize), start, end, 0)
        c.letterSpacing.takeIf { it > 0f }
            ?.let { ss?.setSpan(LetterSpacingSpan(it), start, end, 0) }
        start = compositionPos[0] + composition.selStart
        end = compositionPos[0] + composition.selEnd
        ss!!.setSpan(ForegroundColorSpan(ColorManager.getColor("hilited_text_color")!!), start, end, 0)
        ss!!.setSpan(BackgroundColorSpan(ColorManager.getColor("hilited_back_color")!!), start, end, 0)
        sep = c.suffix
        if (sep.isNotEmpty()) ss!!.append(sep)
    }

    /**
     * 计算悬浮窗显示候选词后，候选栏从第几个候选词开始展示 注意当 all_phrases==true 时，悬浮窗显示的候选词数量和候选栏从第几个开始，是不一致的
     *
     * @param minLength 候选词长度大于设定，才会显示到悬浮窗中
     * @param minCheck 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
     * @return j
     */
    private fun calcStartNum(): Int {
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
        c: WindowComponent.Candidate,
        endNum: Int,
        context: RimeContext,
    ) {
        var start: Int
        var end: Int
        val alignSpan = getAlignSpan(c.alignment)
        val candidates = context.menu!!.candidates
        if (candidates.isEmpty()) return
        val prefix = c.prefix
        highlightIndex = if (candidateUseCursor) Rime.candHighlightIndex else -1
        val labelFormat = c.labelFormat
        val candidateFormat = c.candidateFormat
        val commentFormat = c.commentFormat
        val line = c.separator
        var lineLength = 0
        val labels = context.selectLabels
        var i = -1
        candidateNum = 0
        val maxLength = theme.style.getInt("layout/max_length")
        for (o in candidates) {
            var cand = o.text
            i++
            if (candidateNum >= maxEntries) break
            if (!allPhrases && candidateNum >= endNum) break
            if (allPhrases && cand.length < minLength) {
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
                ss!!.setSpan(alignSpan, start, end, 0)
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
                        highlightLabelColor,
                        highlightCandidateBackColor,
                        ColorManager.getColor("label_color")!!,
                    ),
                    start,
                    end,
                    0,
                )
                val labelTextSize = sp(theme.style.getInt("label_text_size"))
                ss!!.setSpan(AbsoluteSizeSpan(labelTextSize), start, end, 0)
            }
            start = ss!!.length
            ss!!.append(cand)
            end = ss!!.length
            lineLength += cand.length
            ss!!.setSpan(alignSpan, start, end, 0)
            ss!!.setSpan(
                CandidateSpan(
                    i,
                    FontManager.getTypeface("candidate_font"),
                    highlightCandidateTextColor,
                    highlightCandidateBackColor,
                    ColorManager.getColor("candidate_text_color")!!,
                ),
                start,
                end,
                0,
            )
            val candidateTextSize = sp(theme.style.getInt("candidate_text_size"))
            ss!!.setSpan(AbsoluteSizeSpan(candidateTextSize), start, end, 0)
            if (showComment && commentFormat.isNotEmpty() && o.comment.isNotEmpty()) {
                val comment = String.format(commentFormat, o.comment)
                start = ss!!.length
                ss!!.append(comment)
                end = ss!!.length
                ss!!.setSpan(alignSpan, start, end, 0)
                ss!!.setSpan(
                    CandidateSpan(
                        i,
                        FontManager.getTypeface("comment_font"),
                        ColorManager.getColor("hilited_comment_text_color")!!,
                        highlightCandidateBackColor,
                        ColorManager.getColor("comment_text_color")!!,
                    ),
                    start,
                    end,
                    0,
                )
                val commentTextSize = sp(theme.style.getInt("comment_text_size"))
                ss!!.setSpan(AbsoluteSizeSpan(commentTextSize), start, end, 0)
                lineLength += comment.length
            }
            candidateNum++
        }
        val suffix = c.suffix
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    private fun appendButton(c: WindowComponent.Button) {
        if (c.`when` == "has_menu" && !Rime.hasMenu()) return
        if (c.`when` == "paging" && !Rime.hasLeft()) return
        val ev = EventManager.getEvent(c.click)
        val label =
            c.label.ifEmpty {
                ev.getLabel(KeyboardSwitcher.currentKeyboard)
            }
        var start: Int
        var end: Int
        val alignSpan = getAlignSpan(c.alignment)
        val prefix = c.prefix
        if (prefix.isNotEmpty()) {
            start = ss!!.length
            ss!!.append(prefix)
            end = ss!!.length
            ss!!.setSpan(alignSpan, start, end, 0)
        }
        start = ss!!.length
        ss!!.append(label)
        end = ss!!.length
        ss!!.setSpan(alignSpan, start, end, 0)
        ss!!.setSpan(EventSpan(ev), start, end, 0)
        ss!!.setSpan(AbsoluteSizeSpan(sp(keyTextSize)), start, end, 0)
        val suffix = c.suffix
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    private fun appendMove(c: WindowComponent.Move) {
        val s = c.move
        var start: Int
        var end: Int
        val prefix = c.prefix
        if (prefix.isNotEmpty()) {
            start = ss!!.length
            ss!!.append(prefix)
            end = ss!!.length
            ss!!.setSpan(getAlignSpan(c.alignment), start, end, 0)
        }
        start = ss!!.length
        ss!!.append(s)
        end = ss!!.length
        ss!!.setSpan(getAlignSpan(c.alignment), start, end, 0)
        movePos[0] = start
        movePos[1] = end
        ss!!.setSpan(AbsoluteSizeSpan(sp(keyTextSize)), start, end, 0)
        ss!!.setSpan(ForegroundColorSpan(keyTextColor), start, end, 0)
        val suffix = c.suffix
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    /**
     * 设置悬浮窗文本
     *
     * @return 悬浮窗显示的候选词数量
     */
    fun update(context: RimeContext): Int {
        if (visibility != VISIBLE) return 0
        if (context.composition?.preedit.isNullOrEmpty()) return 0
        isSingleLine = true // 設置單行
        ss = SpannableStringBuilder()
        val startNum = calcStartNum()
        Timber.d("startNum=$startNum, minLength=$minLength, minCheck=$minCheck")
        windowComponents.forEach {
            when (it) {
                is WindowComponent.Move -> appendMove(it)
                is WindowComponent.Composition -> appendComposition(it, context)
                is WindowComponent.Candidate -> appendCandidates(it, startNum, context)
                is WindowComponent.Button -> appendButton(it)
            }
        }
        if (candidateNum > 0 || ss.toString().contains("\n")) isSingleLine = false // 設置單行
        text = ss
        movementMethod = LinkMovementMethod.getInstance()
        isToolbarMode = false
        return startNum
    }

    /** 设置悬浮窗, 用于liquidKeyboard的悬浮窗工具栏  */
    fun changeToLiquidKeyboardToolbar() {
        if (visibility != VISIBLE) return
        if (liquidWindowComponents.isEmpty()) {
            this.visibility = GONE
            return
        }
        ss = SpannableStringBuilder()
        liquidWindowComponents.filterIsInstance<WindowComponent.Button>()
            .forEach { appendButton(it) }
        isSingleLine = !ss.toString().contains("\n")
        text = ss
        movementMethod = LinkMovementMethod.getInstance()
        isToolbarMode = true
    }
}
