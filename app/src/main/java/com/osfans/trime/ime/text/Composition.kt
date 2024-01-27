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
import android.content.res.Resources
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.TextUtils
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
import androidx.appcompat.widget.AppCompatTextView
import com.osfans.trime.core.Rime.Companion.candHighlightIndex
import com.osfans.trime.core.Rime.Companion.candidatesOrStatusSwitches
import com.osfans.trime.core.Rime.Companion.composition
import com.osfans.trime.core.Rime.Companion.getOption
import com.osfans.trime.core.Rime.Companion.getRimeRawInput
import com.osfans.trime.core.Rime.Companion.hasLeft
import com.osfans.trime.core.Rime.Companion.hasMenu
import com.osfans.trime.core.Rime.Companion.selectLabels
import com.osfans.trime.core.Rime.Companion.setCaretPos
import com.osfans.trime.data.theme.FontManager.getTypeface
import com.osfans.trime.data.theme.Theme.Companion.get
import com.osfans.trime.ime.core.Trime.Companion.getService
import com.osfans.trime.ime.keyboard.Event
import com.osfans.trime.ime.text.TextInputManager.Companion.getInstance
import com.osfans.trime.ime.util.UiUtil.isDarkMode
import com.osfans.trime.util.CollectionUtils.obtainFloat
import com.osfans.trime.util.CollectionUtils.obtainString
import com.osfans.trime.util.dp2px
import com.osfans.trime.util.sp2px
import timber.log.Timber

/** 編碼區，顯示已輸入的按鍵編碼，可使用方向鍵或觸屏移動光標位置  */
@Suppress("ktlint:standard:property-naming")
class Composition(context: Context?, attrs: AttributeSet?) : AppCompatTextView(context!!, attrs) {
    private var key_text_size = 0
    private var text_size = 0
    private var label_text_size = 0
    private var candidate_text_size = 0
    private var comment_text_size = 0
    private var key_text_color = 0
    private var text_color = 0
    private var label_color = 0
    private var candidate_text_color = 0
    private var comment_text_color = 0
    private var hilited_text_color = 0
    private var hilited_candidate_text_color = 0
    private var hilited_comment_text_color = 0
    private var back_color = 0
    private var hilited_back_color = 0
    private var hilited_candidate_back_color = 0
    private var key_back_color: Int? = null
    private var tfText: Typeface? = null
    private var tfLabel: Typeface? = null
    private var tfCandidate: Typeface? = null
    private var tfComment: Typeface? = null
    private val composition_pos = IntArray(2)
    private var max_length = 0
    private var sticky_lines = 0
    private var sticky_lines_land = 0
    private var max_entries = 0
    private var candidate_use_cursor = false
    private var show_comment = false
    private var highlightIndex = 0
    private var windows_comps: List<Map<String, Any?>>? = null
    private var liquid_keyboard_window_comp: List<Map<String, Any?>>? = null
    private var ss: SpannableStringBuilder? = null
    private val span = 0
    private var movable: String? = null
    private val move_pos = IntArray(2)
    private var first_move = true
    private var mDx = 0f
    private var mDy = 0f
    private var mCurrentX = 0
    private var mCurrentY = 0
    private var candidate_num = 0
    private var all_phrases = false

    // private View mInputRoot;
    // 候选高亮序号颜色
    private var hilited_label_color: Int? = null
    private val textInputManager: TextInputManager
    private var isToolbarMode = false

    private inner class CompositionSpan : UnderlineSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.setTypeface(tfText)
            ds.color = text_color
            ds.bgColor = back_color
        }
    }

    private inner class CandidateSpan(
        var index: Int,
        var tf: Typeface?,
        var hi_text: Int,
        var hi_back: Int,
        var text: Int,
    ) : ClickableSpan() {
        override fun onClick(tv: View) {
            textInputManager.onCandidatePressed(index)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.isUnderlineText = false
            ds.setTypeface(tf)
            if (index == highlightIndex) {
                ds.color = hi_text
                ds.bgColor = hi_back
            } else {
                ds.color = text
            }
        }
    }

    private inner class EventSpan(var event: Event) : ClickableSpan() {
        override fun onClick(tv: View) {
            textInputManager.onPress(event.code)
            textInputManager.onEvent(event)
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.isUnderlineText = false
            ds.color = key_text_color
            if (key_back_color != null) ds.bgColor = key_back_color as Int
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
        textInputManager = getInstance(isDarkMode(context!!))
        setShowComment(!getOption("_hide_comment"))
        reset()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.action
        if (!isToolbarMode) {
            if (action == MotionEvent.ACTION_UP) {
                var n = getOffsetForPosition(event.x, event.y)
                if (composition_pos[0] <= n && n <= composition_pos[1]) {
                    val s =
                        text
                            .toString()
                            .substring(n, composition_pos[1])
                            .replace(" ", "")
                            .replace("‸", "")
                    n = getRimeRawInput()!!.length - s.length // 從右側定位
                    setCaretPos(n)
                    getService().updateComposing()
                    return true
                }
            } else if (!movable.contentEquals("false") &&
                (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN)
            ) {
                val n = getOffsetForPosition(event.x, event.y)
                if (move_pos[0] <= n && n <= move_pos[1]) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        if (first_move || movable.contentEquals("once")) {
                            first_move = false
                            getLocationOnScreen(intArrayOf(mCurrentX, mCurrentY))
                        }
                        mDx = mCurrentX - event.rawX
                        mDy = mCurrentY - event.rawY
                    } else { // MotionEvent.ACTION_MOVE
                        mCurrentX = (event.rawX + mDx).toInt()
                        mCurrentY = (event.rawY + mDy).toInt()
                        getService().updatePopupWindow(mCurrentX, mCurrentY)
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun setShowComment(value: Boolean) {
        show_comment = value
    }

    fun reset() {
        val theme = get()
        windows_comps = theme.style.getObject("window") as List<Map<String, Any?>>?
            ?: ArrayList()
        liquid_keyboard_window_comp = theme.style.getObject("liquid_keyboard_window") as List<Map<String, Any?>>?
            ?: ArrayList()

        if (theme.style.getInt("layout/max_entries").also { max_entries = it } == 0) {
            max_entries = Candidate.maxCandidateCount
        }
        candidate_use_cursor = theme.style.getBoolean("candidate_use_cursor")
        text_size = sp2px(theme.style.getFloat("text_size")).toInt()
        candidate_text_size = sp2px(theme.style.getFloat("candidate_text_size")).toInt()
        comment_text_size = sp2px(theme.style.getFloat("comment_text_size")).toInt()
        label_text_size = sp2px(theme.style.getFloat("label_text_size")).toInt()
        text_color = theme.colors.getColor("text_color")!!
        candidate_text_color = theme.colors.getColor("candidate_text_color")!!
        comment_text_color = theme.colors.getColor("comment_text_color")!!
        hilited_text_color = theme.colors.getColor("hilited_text_color")!!
        hilited_candidate_text_color = theme.colors.getColor("hilited_candidate_text_color")!!
        hilited_comment_text_color = theme.colors.getColor("hilited_comment_text_color")!!
        label_color = theme.colors.getColor("label_color")!!
        hilited_label_color = theme.colors.getColor("hilited_label_color")
        if (hilited_label_color == null) {
            hilited_label_color = hilited_candidate_text_color
        }
        back_color = theme.colors.getColor("back_color")!!
        hilited_back_color = theme.colors.getColor("hilited_back_color")!!
        hilited_candidate_back_color = theme.colors.getColor("hilited_candidate_back_color")!!
        key_text_size = sp2px(theme.style.getFloat("key_text_size")).toInt()
        key_text_color = theme.colors.getColor("key_text_color")!!
        key_back_color = theme.colors.getColor("key_back_color")
        var line_spacing_multiplier = theme.style.getFloat("layout/line_spacing_multiplier")
        if (line_spacing_multiplier == 0f) line_spacing_multiplier = 1f
        setLineSpacing(theme.style.getFloat("layout/line_spacing"), line_spacing_multiplier)
        minWidth = dp2px(theme.style.getFloat("layout/min_width")).toInt()
        minHeight = dp2px(theme.style.getFloat("layout/min_height")).toInt()
        var max_width = dp2px(theme.style.getFloat("layout/max_width")).toInt()
        val real_margin = dp2px(theme.style.getFloat("layout/real_margin")).toInt()
        val displayWidth = Resources.getSystem().displayMetrics.widthPixels
        Timber.d("max_width = %s, displayWidth = %s ", max_width, displayWidth)
        if (max_width > displayWidth) max_width = displayWidth
        maxWidth = max_width - real_margin * 2
        maxHeight = dp2px(theme.style.getFloat("layout/max_height")).toInt()
        val margin_x: Int = dp2px(theme.style.getFloat("layout/margin_x")).toInt()
        val margin_y: Int = dp2px(theme.style.getFloat("layout/margin_y")).toInt()
        val margin_bottom: Int = dp2px(theme.style.getFloat("layout/margin_bottom")).toInt()
        setPadding(margin_x, margin_y, margin_x, margin_bottom)
        max_length = theme.style.getInt("layout/max_length")
        sticky_lines = theme.style.getInt("layout/sticky_lines")
        sticky_lines_land = theme.style.getInt("layout/sticky_lines_land")
        movable = theme.style.getString("layout/movable")
        all_phrases = theme.style.getBoolean("layout/all_phrases")
        tfLabel = getTypeface(theme.style.getString("label_font"))
        tfText = getTypeface(theme.style.getString("text_font"))
        tfCandidate = getTypeface(theme.style.getString("candidate_font"))
        tfComment = getTypeface(theme.style.getString("comment_font"))
    }

    private fun getAlign(m: Map<String, Any?>): Any {
        var i = Layout.Alignment.ALIGN_NORMAL
        if (m.containsKey("align")) {
            val align = obtainString(m, "align", "")
            when (align) {
                "left", "normal" -> i = Layout.Alignment.ALIGN_NORMAL
                "right", "opposite" -> i = Layout.Alignment.ALIGN_OPPOSITE
                "center" -> i = Layout.Alignment.ALIGN_CENTER
            }
        }
        return AlignmentSpan.Standard(i)
    }

    private fun appendComposition(m: Map<String, Any?>) {
        val r = composition!!
        val s = r.preedit
        var start: Int
        var end: Int
        var sep = obtainString(m, "start", "")
        if (!TextUtils.isEmpty(sep)) {
            start = ss!!.length
            ss!!.append(sep)
            end = ss!!.length
            ss!!.setSpan(getAlign(m), start, end, span)
        }
        start = ss!!.length
        ss!!.append(s)
        end = ss!!.length
        ss!!.setSpan(getAlign(m), start, end, span)
        composition_pos[0] = start
        composition_pos[1] = end
        ss!!.setSpan(CompositionSpan(), start, end, span)
        ss!!.setSpan(AbsoluteSizeSpan(text_size), start, end, span)
        if (m.containsKey("letter_spacing")) {
            val size = obtainFloat(m, "letter_spacing", 0f)
            if (size != 0f) ss!!.setSpan(LetterSpacingSpan(size), start, end, span)
        }
        start = composition_pos[0] + r.selStartPos
        end = composition_pos[0] + r.selEndPos
        ss!!.setSpan(ForegroundColorSpan(hilited_text_color), start, end, span)
        ss!!.setSpan(BackgroundColorSpan(hilited_back_color), start, end, span)
        sep = obtainString(m, "end", "")
        if (!TextUtils.isEmpty(sep)) ss!!.append(sep)
    }

    /**
     * 计算悬浮窗显示候选词后，候选栏从第几个候选词开始展示 注意当 all_phrases==true 时，悬浮窗显示的候选词数量和候选栏从第几个开始，是不一致的
     *
     * @param min_length 候选词长度大于设定，才会显示到悬浮窗中
     * @param min_check 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
     * @return j
     */
    private fun calcStartNum(
        min_length: Int,
        min_check: Int,
    ): Int {
        Timber.d("setWindow calcStartNum() getCandidates")
        val candidates = candidatesOrStatusSwitches
        if (candidates.isEmpty()) return 0
        Timber.d("setWindow calcStartNum() getCandidates finish, size=%s", candidates.size)
        var j = if (min_check > max_entries) max_entries - 1 else min_check - 1
        if (j >= candidates.size) j = candidates.size - 1
        while (j >= 0) {
            val cand = candidates[j].text
            if (cand.length >= min_length) break
            j--
        }
        if (j < 0) j = 0
        while (j < max_entries && j < candidates.size) {
            val cand = candidates[j].text
            if (cand.length < min_length) {
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
        end_num: Int,
    ) {
        Timber.d("appendCandidates(): length = %s", length)
        var start: Int
        var end: Int
        val candidates = candidatesOrStatusSwitches
        if (candidates.isEmpty()) return
        var sep = obtainString(m, "start", "")
        highlightIndex = if (candidate_use_cursor) candHighlightIndex else -1
        val label_format = obtainString(m, "label", "")
        val candidate_format = obtainString(m, "candidate", "")
        val comment_format = obtainString(m, "comment", "")
        val line = obtainString(m, "sep", "")
        var line_length = 0
        var sticky_lines_now = sticky_lines
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            sticky_lines_now = sticky_lines_land
        }
        //    Timber.d("sticky_lines_now = %d", sticky_lines_now);
        val labels = selectLabels
        var i = -1
        candidate_num = 0
        for (o in candidates) {
            var cand = o.text
            if (TextUtils.isEmpty(cand)) cand = ""
            i++
            if (candidate_num >= max_entries) break
            if (!all_phrases && candidate_num >= end_num) break
            if (all_phrases && cand.length < length) {
                continue
            }
            cand = String.format(candidate_format, cand)
            val line_sep: String
            if (candidate_num == 0) {
                line_sep = sep
            } else if (sticky_lines_now > 0 && sticky_lines_now >= i || max_length > 0 && line_length + cand.length > max_length) {
                line_sep = "\n"
                line_length = 0
            } else {
                line_sep = line
            }
            if (!TextUtils.isEmpty(line_sep)) {
                start = ss!!.length
                ss!!.append(line_sep)
                end = ss!!.length
                ss!!.setSpan(getAlign(m), start, end, span)
            }
            if (!TextUtils.isEmpty(label_format) && labels.isNotEmpty()) {
                val label = String.format(label_format, labels[i])
                start = ss!!.length
                ss!!.append(label)
                end = ss!!.length
                ss!!.setSpan(
                    CandidateSpan(
                        i,
                        tfLabel,
                        hilited_label_color!!,
                        hilited_candidate_back_color,
                        label_color,
                    ),
                    start,
                    end,
                    span,
                )
                ss!!.setSpan(AbsoluteSizeSpan(label_text_size), start, end, span)
            }
            start = ss!!.length
            ss!!.append(cand)
            end = ss!!.length
            line_length += cand.length
            ss!!.setSpan(getAlign(m), start, end, span)
            ss!!.setSpan(
                CandidateSpan(
                    i,
                    tfCandidate,
                    hilited_candidate_text_color,
                    hilited_candidate_back_color,
                    candidate_text_color,
                ),
                start,
                end,
                span,
            )
            ss!!.setSpan(AbsoluteSizeSpan(candidate_text_size), start, end, span)
            var comment = o.comment
            if (show_comment && !TextUtils.isEmpty(comment_format) && !TextUtils.isEmpty(comment)) {
                comment = String.format(comment_format, comment)
                start = ss!!.length
                ss!!.append(comment)
                end = ss!!.length
                ss!!.setSpan(getAlign(m), start, end, span)
                ss!!.setSpan(
                    CandidateSpan(
                        i,
                        tfComment,
                        hilited_comment_text_color,
                        hilited_candidate_back_color,
                        comment_text_color,
                    ),
                    start,
                    end,
                    span,
                )
                ss!!.setSpan(AbsoluteSizeSpan(comment_text_size), start, end, span)
                line_length += comment.length
            }
            candidate_num++
        }
        sep = obtainString(m, "end", "")
        if (!TextUtils.isEmpty(sep)) ss!!.append(sep)
    }

    private fun appendButton(m: Map<String, Any?>) {
        if (m.containsKey("when")) {
            val `when` = obtainString(m, "when", "")
            if (`when`.contentEquals("paging") && !hasLeft()) return
            if (`when`.contentEquals("has_menu") && !hasMenu()) return
        }
        val label: String
        val e = Event(obtainString(m, "click", ""))
        label = if (m.containsKey("label")) obtainString(m, "label", "") else e.label
        var start: Int
        var end: Int
        var sep: String? = null
        if (m.containsKey("start")) sep = obtainString(m, "start", "")
        if (!TextUtils.isEmpty(sep)) {
            start = ss!!.length
            ss!!.append(sep)
            end = ss!!.length
            ss!!.setSpan(getAlign(m), start, end, span)
        }
        start = ss!!.length
        ss!!.append(label)
        end = ss!!.length
        ss!!.setSpan(getAlign(m), start, end, span)
        ss!!.setSpan(EventSpan(e), start, end, span)
        ss!!.setSpan(AbsoluteSizeSpan(key_text_size), start, end, span)
        sep = obtainString(m, "end", "")
        if (!TextUtils.isEmpty(sep)) ss!!.append(sep)
    }

    private fun appendMove(m: Map<String, Any?>) {
        val s = obtainString(m, "move", "")
        var start: Int
        var end: Int
        var sep = obtainString(m, "start", "")
        if (!TextUtils.isEmpty(sep)) {
            start = ss!!.length
            ss!!.append(sep)
            end = ss!!.length
            ss!!.setSpan(getAlign(m), start, end, span)
        }
        start = ss!!.length
        ss!!.append(s)
        end = ss!!.length
        ss!!.setSpan(getAlign(m), start, end, span)
        move_pos[0] = start
        move_pos[1] = end
        ss!!.setSpan(AbsoluteSizeSpan(key_text_size), start, end, span)
        ss!!.setSpan(ForegroundColorSpan(key_text_color), start, end, span)
        sep = obtainString(m, "end", "")
        if (!TextUtils.isEmpty(sep)) ss!!.append(sep)
    }

    /**
     * 设置悬浮窗文本
     *
     * @param charLength 候选词长度大于设定，才会显示到悬浮窗中
     * @param minCheck 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
     * @param maxPopup 最多在悬浮窗显示多少个候选词
     * @return 悬浮窗显示的候选词数量
     */
    fun setWindow(
        charLength: Int,
        minCheck: Int,
        maxPopup: Int,
    ): Int {
        return setWindow(charLength, minCheck)
    }

    /**
     * 设置悬浮窗文本
     *
     * @param stringMinLength 候选词长度大于设定，才会显示到悬浮窗中
     * @param candidateMinCheck 检查至少多少个候选词。当首选词长度不足时，继续检查后方候选词
     * @return 悬浮窗显示的候选词数量
     */
    private fun setWindow(
        stringMinLength: Int,
        candidateMinCheck: Int,
    ): Int {
        if (visibility != VISIBLE) return 0
        val stacks = Throwable().stackTrace
        Timber.d(
            "setWindow Rime.getComposition()" +
                ", [1]" +
                stacks[1].toString() +
                ", [2]" +
                stacks[2].toString() +
                ", [3]" +
                stacks[3].toString(),
        )
        val (_, _, _, _, s) = composition ?: return 0
        if (TextUtils.isEmpty(s)) return 0
        isSingleLine = true // 設置單行
        ss = SpannableStringBuilder()
        var start_num = 0
        for (m in windows_comps!!) {
            if (m.containsKey("composition")) {
                appendComposition(m)
            } else if (m.containsKey("candidate")) {
                start_num = calcStartNum(stringMinLength, candidateMinCheck)
                Timber.d(
                    "start_num = %s, min_length = %s, min_check = %s",
                    start_num,
                    stringMinLength,
                    candidateMinCheck,
                )
                appendCandidates(m, stringMinLength, start_num)
            } else if (m.containsKey("click")) {
                appendButton(m)
            } else if (m.containsKey("move")) {
                appendMove(m)
            }
        }
        if (candidate_num > 0 || ss.toString().contains("\n")) isSingleLine = false // 設置單行
        text = ss
        movementMethod = LinkMovementMethod.getInstance()
        isToolbarMode = false
        return start_num
    }

    /** 设置悬浮窗, 用于liquidKeyboard的悬浮窗工具栏  */
    fun changeToLiquidKeyboardToolbar() {
        if (visibility != VISIBLE) return
        if (liquid_keyboard_window_comp!!.isEmpty()) {
            this.visibility = GONE
            return
        }
        ss = SpannableStringBuilder()
        for (m in liquid_keyboard_window_comp!!) {
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
