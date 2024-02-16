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
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.keyboard.Event
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.util.CollectionUtils
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import timber.log.Timber

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置  */
@SuppressLint("AppCompatCustomView")
class Composition(context: Context, attrs: AttributeSet?) : TextView(context, attrs) {
    private val theme = ThemeManager.activeTheme
    private val textInputManager = TextInputManager.getInstance()

    private val keyTextSize = theme.style.getInt("key_text_size")
    private val keyTextColor = theme.colors.getColor("key_text_color")!!
    private val candidateUseCursor = theme.style.getBoolean("candidate_use_cursor")
    private val movable = Movable.fromString(theme.style.getString("layout/movable"))
    private val showComment = !Rime.getOption("_hide_comment")
    private val maxEntries =
        theme.style.getInt("layout/max_entries").takeIf { it > 0 }
            ?: Candidate.MAX_CANDIDATE_COUNT

    @Suppress("UNCHECKED_CAST")
    private val windowComponents =
        theme.style.getObject("window") as List<Map<String, Any?>>?
            ?: listOf()

    @Suppress("UNCHECKED_CAST")
    private val liquidWindowComponents =
        theme.style.getObject("liquid_keyboard_window") as List<Map<String, Any?>>?
            ?: listOf()

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
            ds.color = theme.colors.getColor("text_color")!!
            ds.bgColor = theme.colors.getColor("back_color")!!
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
            ds.bgColor = theme.colors.getColor("key_back_color")!!
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

    private fun getAlign(m: Map<String, Any?>): Any {
        var i = Layout.Alignment.ALIGN_NORMAL
        if (m.containsKey("align")) {
            when (CollectionUtils.obtainString(m, "align")) {
                "left", "normal" -> i = Layout.Alignment.ALIGN_NORMAL
                "right", "opposite" -> i = Layout.Alignment.ALIGN_OPPOSITE
                "center" -> i = Layout.Alignment.ALIGN_CENTER
            }
        }
        return AlignmentSpan.Standard(i)
    }

    private fun appendComposition(m: Map<String, Any?>) {
        val r = Rime.composition!!
        val s = r.preedit
        var start: Int
        var end: Int
        var sep = CollectionUtils.obtainString(m, "start", "")
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
        val textSize = sp(theme.style.getInt("text_size"))
        ss!!.setSpan(AbsoluteSizeSpan(textSize), start, end, 0)
        CollectionUtils.obtainFloat(m, "letter_spacing").takeIf { it > 0 }
            ?.let { ss?.setSpan(LetterSpacingSpan(it), start, end, 0) }
        start = compositionPos[0] + r.selStartPos
        end = compositionPos[0] + r.selEndPos
        ss!!.setSpan(ForegroundColorSpan(theme.colors.getColor("hilited_text_color")!!), start, end, 0)
        ss!!.setSpan(BackgroundColorSpan(theme.colors.getColor("hilited_back_color")!!), start, end, 0)
        sep = CollectionUtils.obtainString(m, "end", "")
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
        m: Map<String, Any?>,
        length: Int,
        endNum: Int,
    ) {
        Timber.d("appendCandidates(): length = %s", length)
        var start: Int
        var end: Int
        val candidates = Rime.candidatesOrStatusSwitches
        if (candidates.isEmpty()) return
        val prefix = CollectionUtils.obtainString(m, "start")
        highlightIndex = if (candidateUseCursor) Rime.candHighlightIndex else -1
        val labelFormat = CollectionUtils.obtainString(m, "label")
        val candidateFormat = CollectionUtils.obtainString(m, "candidate")
        val commentFormat = CollectionUtils.obtainString(m, "comment")
        val line = CollectionUtils.obtainString(m, "sep")
        var lineLength = 0
        val labels = Rime.selectLabels
        var i = -1
        candidateNum = 0
        val maxLength = theme.style.getInt("layout/max_length")
        val hilitedCandidateBackColor = theme.colors.getColor("hilited_candidate_back_color")!!
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
                        theme.colors.getColor("hilited_label_color")!!,
                        hilitedCandidateBackColor,
                        theme.colors.getColor("label_color")!!,
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
            ss!!.setSpan(getAlign(m), start, end, 0)
            ss!!.setSpan(
                CandidateSpan(
                    i,
                    FontManager.getTypeface("candidate_font"),
                    theme.colors.getColor("hilited_candidate_text_color")!!,
                    hilitedCandidateBackColor,
                    theme.colors.getColor("candidate_text_color")!!,
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
                ss!!.setSpan(getAlign(m), start, end, 0)
                ss!!.setSpan(
                    CandidateSpan(
                        i,
                        FontManager.getTypeface("comment_font"),
                        theme.colors.getColor("hilited_comment_text_color")!!,
                        hilitedCandidateBackColor,
                        theme.colors.getColor("comment_text_color")!!,
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
        val suffix = CollectionUtils.obtainString(m, "end", "")
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    private fun appendButton(m: Map<String, Any?>) {
        if (m.containsKey("when")) {
            val `when` = CollectionUtils.obtainString(m, "when", "")
            if (`when`.contentEquals("paging") && !Rime.hasLeft()) return
            if (`when`.contentEquals("has_menu") && !Rime.hasMenu()) return
        }
        val e = Event(CollectionUtils.obtainString(m, "click"))
        val label =
            if (m.containsKey("label")) {
                CollectionUtils.obtainString(m, "label", "")
            } else {
                e.getLabel(KeyboardSwitcher.currentKeyboard)
            }
        var start: Int
        var end: Int
        val prefix = CollectionUtils.obtainString(m, "start")
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
        val suffix = CollectionUtils.obtainString(m, "end")
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    private fun appendMove(m: Map<String, Any?>) {
        val s = CollectionUtils.obtainString(m, "move", "")
        var start: Int
        var end: Int
        val prefix = CollectionUtils.obtainString(m, "start")
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
        ss!!.setSpan(AbsoluteSizeSpan(keyTextSize), start, end, 0)
        ss!!.setSpan(ForegroundColorSpan(keyTextColor), start, end, 0)
        val suffix = CollectionUtils.obtainString(m, "end")
        if (suffix.isNotEmpty()) ss!!.append(suffix)
    }

    /**
     * 设置悬浮窗文本
     *
     * @return 悬浮窗显示的候选词数量
     */
    fun setWindowContent(): Int {
        if (visibility != VISIBLE) return 0
        val stacks = Throwable().stackTrace
        Timber.d("setWindow Rime.getComposition(), [1]${stacks[1]}, [2]${stacks[2]}, [3]${stacks[3]}")
        val (_, _, _, _, s) = Rime.composition ?: return 0
        if (s.isNullOrEmpty()) return 0
        isSingleLine = true // 設置單行
        ss = SpannableStringBuilder()
        var startNum = 0
        val minLength = theme.style.getInt("layout/min_length") // 候选词长度大于设定，才会显示到悬浮窗中
        val minCheck = theme.style.getInt("layout/min_check") // 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
        for (m in windowComponents) {
            if (m.containsKey("composition")) {
                appendComposition(m)
            } else if (m.containsKey("candidate")) {
                startNum = calcStartNum(minLength, minCheck)
                Timber.d("start_num = %s, min_length = %s, min_check = %s", startNum, minLength, minCheck)
                appendCandidates(m, minLength, startNum)
            } else if (m.containsKey("click")) {
                appendButton(m)
            } else if (m.containsKey("move")) {
                appendMove(m)
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
        for (m in liquidWindowComponents) {
            if (m.containsKey("composition")) {
                appendComposition(m)
            } else if (m.containsKey("click")) {
                appendButton(m)
            }
        }
        isSingleLine = !ss.toString().contains("\n")
        text = ss
        movementMethod = LinkMovementMethod.getInstance()
        isToolbarMode = true
    }
}
