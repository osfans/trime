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
package com.osfans.trime.ime.keyboard

import android.text.TextUtils
import android.view.KeyEvent
import com.osfans.trime.core.Rime
import com.osfans.trime.core.Rime.Companion.currentSchemaName
import com.osfans.trime.core.Rime.Companion.getOption
import com.osfans.trime.core.Rime.Companion.getRimeKeycodeByName
import com.osfans.trime.core.Rime.Companion.isAsciiMode
import com.osfans.trime.core.RimeKeyMapping.keyCodeToVal
import com.osfans.trime.data.AppPrefs.Companion.defaultInstance
import com.osfans.trime.ime.enums.Keycode
import com.osfans.trime.ime.enums.Keycode.Companion.fromString
import com.osfans.trime.ime.enums.Keycode.Companion.getDisplayLabel
import com.osfans.trime.ime.enums.Keycode.Companion.isStdKey
import com.osfans.trime.ime.enums.Keycode.Companion.keyCodeOf
import com.osfans.trime.ime.enums.Keycode.Companion.keyNameOf
import com.osfans.trime.ime.enums.Keycode.Companion.parseSend
import com.osfans.trime.util.CollectionUtils.obtainBoolean
import com.osfans.trime.util.CollectionUtils.obtainString
import timber.log.Timber
import java.util.Locale

/** [按鍵][Key]的各種事件（單擊、長按、滑動等）
 *
 * @param keyboard 按键所在的键盘
 * @param s 具体事件要发送的字符串或者预设键，如 'q' '{Ctrl+F}' Return
 *
 * */
class Event(keyboard: Keyboard?, var s: String) {
    // private String TAG = "Event";
    private val mKeyboard: Keyboard?

    /** 定义在 [Keycode] */
    var code = 0

    /** 修饰键掩码 */
    var mask = 0
    var text: String = ""
        get() {
            if (field.isNotEmpty()) return adjustCase(s)
            if (mKeyboard != null && mKeyboard.needUpCase() &&
                mask == 0 && code >= KeyEvent.KEYCODE_A &&
                code <= KeyEvent.KEYCODE_Z
            ) {
                return adjustCase(label)
            }
            return field
        }

    var label: String = ""
        get() {
            val state = states?.get(if (getOption(toggle)) 1 else 0)
            if (state != null) return state
            if (mKeyboard == null) return adjustCase(field)

            if (mKeyboard.isOnlyShiftOn) {
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 && !defaultInstance().keyboard.hookShiftNum) {
                    return adjustCase(
                        shiftLabel,
                    )
                }
                if (code >= KeyEvent.KEYCODE_GRAVE &&
                    code <= KeyEvent.KEYCODE_SLASH ||
                    code == KeyEvent.KEYCODE_COMMA ||
                    code == KeyEvent.KEYCODE_PERIOD
                ) {
                    if (!defaultInstance().keyboard.hookShiftSymbol) return adjustCase(shiftLabel)
                }
            } else if (mKeyboard.modifer or mask and KeyEvent.META_SHIFT_ON != 0) {
                return adjustCase(shiftLabel)
            }
            return adjustCase(field)
        }

    private var shiftLabel: String = ""
    private var preview: String = ""
    private var states: List<String>? = null
    var command: String = ""
    var option: String = ""
    var select: String? = null
    var toggle: String = ""
        get() {
            return field.ifEmpty { "ascii_mode" }
        }

    var commit: String = ""
        private set
    var shiftLock: String? = null
    var isFunctional = false
    var isRepeatable = false
    var isSticky = false

    // 快速把字符串解析为event, 暂时只处理了comment类型 不能完全正确处理=，
    private fun parseAction(s: String): Boolean {
        var result = false
        val strs = s.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (str in strs) {
            val set = str.split("=".toRegex(), limit = 2).toTypedArray()
            if (set.size != 2) continue
            if (set[0] == "commit") {
                commit = set[1]
                result = true
            } else if (set[0] == "label") {
                label = set[1]
                result = true
            } else if (set[0] == "text") {
                text = set[1]
                result = true
            }
        }
        Timber.d("<Event> text=$text, commit=$commit, label=$label, s=$s")
        return result
    }

    // TODO 进一步解耦，在Event中去除mKeyboard
    private fun adjustCase(s: String): String {
        var s = s
        if (TextUtils.isEmpty(s)) return ""
        if (s.length == 1 && mKeyboard != null && mKeyboard.needUpCase()) {
            s = s.uppercase(Locale.getDefault())
        } else if (s.length == 1 && mKeyboard != null && !isAsciiMode &&
            mKeyboard.isLabelUppercase
        ) {
            s = s.uppercase(Locale.getDefault())
        }
        return s
    }

    val previewText: String
        get() = if (!TextUtils.isEmpty(preview)) preview else label

    private fun parseLabel() {
        if (label.isNotEmpty()) return
        val c = code
        if (c == KeyEvent.KEYCODE_SPACE) {
            label = currentSchemaName
        } else {
            if (c > 0) label = getDisplayLabel(c, mask)
        }
    }

    val isMeta: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_META_LEFT || c == KeyEvent.KEYCODE_META_RIGHT
        }
    val isAlt: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_ALT_LEFT || c == KeyEvent.KEYCODE_ALT_RIGHT
        }

    constructor(s: String) : this(null, s)

    init {
        mKeyboard = keyboard
        // 组合键 {Ctrl+F}
        if (s.matches(sendPattern)) {
            val keys = s.substring(1, s.length - 1) // 去除两边的大括号
            val sends = parseSend(keys) // send
            code = sends[0]
            mask = sends[1]
            /** 解析成功 */
            if (code > 0 || mask > 0 && parseAction(keys)) {
                s = keys
            } else {
                initHelper()
            }
        } else {
            initHelper()
        }
    }

    private fun initHelper() {
        // 预设按键，如 Return BackSpace
        if (Key.presetKeys!!.containsKey(s)) {
            // todo 把presetKeys缓存为presetKeyEvents，减少重新载入
            val presetKey = Key.presetKeys!![s]
            command = obtainString(presetKey, "command", "")
            option = obtainString(presetKey, "option", "")
            select = obtainString(presetKey, "select", "")
            toggle = obtainString(presetKey, "toggle", "")
            label = obtainString(presetKey, "label", "")
            preview = obtainString(presetKey, "preview", "")
            shiftLock = obtainString(presetKey, "shift_lock", "")
            commit = obtainString(presetKey, "commit", "")
            var send = obtainString(presetKey, "send", "")
            if (send.isEmpty() && command.isNotEmpty()) send = "function" // command默認發function
            val sends = parseSend(send)
            code = sends[0]
            mask = sends[1]
            parseLabel()
            text = obtainString(presetKey, "text", "")
            if (code < 0 && TextUtils.isEmpty(text)) text = s
            if (presetKey?.containsKey("states") == true) states = presetKey["states"] as List<String>?
            isSticky = obtainBoolean(presetKey, "sticky", false)
            isRepeatable = obtainBoolean(presetKey, "repeatable", false)
            isFunctional = obtainBoolean(presetKey, "functional", true)
        } else if (getClickCode(s).also { code = it } >= 0) {
            // 普通的单个按键 'q' '1' '!'
            parseLabel()
        } else {
            // '(){Left}'
            code = 0
            text = s
            label = text.replace(labelPattern, "")
        }
        shiftLabel = label
        if (isStdKey(code)) { // Android keycode区域
            if (Key.kcm.isPrintingKey(code)) {
                val mMask = KeyEvent.META_SHIFT_ON or mask
                val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, mMask)
                val k = event.getUnicodeChar(mMask)
                Timber.d(
                    "shiftLabel = $shiftLabel keycode=$code, mask=$mMask, k=$k",
                )
                if (k > 0) {
                    shiftLabel = "" + k.toChar()
                }
            }
        }
    }

    companion object {
        // {send|key}
        private val sendPattern = Regex("""\{[^{}]+\}""")
        private val labelPattern = Regex("""\{[^{}]+\}""")

        @JvmStatic
        fun getClickCode(s: String?): Int {
            var keyCode = -1
            if (TextUtils.isEmpty(s)) { // 空鍵
                keyCode = 0
            } else if (fromString(s!!) != Keycode.VoidSymbol) {
                keyCode = keyCodeOf(s)
            }
            return keyCode
        }

        fun hasModifier(
            mask: Int,
            modifier: Int,
        ): Boolean {
            return mask and modifier > 0
        }

        // KeyboardEvent 从软键盘的按键keycode（可能含有mask）和mask，分离出rimekeycode和mask构成的数组
        @JvmStatic
        fun getRimeEvent(
            code: Int,
            mask: Int,
        ): IntArray {
            var i = keyCodeToVal(code)
            if (i == 0xffffff) { // 如果不是Android keycode, 则直接使用获取rimekeycode
                val s = keyNameOf(code)
                i = getRimeKeycodeByName(s)
            }
            var m = 0
            if (hasModifier(mask, KeyEvent.META_SHIFT_ON)) m = m or Rime.META_SHIFT_ON
            if (hasModifier(mask, KeyEvent.META_CTRL_ON)) m = m or Rime.META_CTRL_ON
            if (hasModifier(mask, KeyEvent.META_ALT_ON)) m = m or Rime.META_ALT_ON
            if (hasModifier(mask, KeyEvent.META_SYM_ON)) m = m or Rime.META_SYM_ON
            if (hasModifier(mask, KeyEvent.META_META_ON)) m = m or Rime.META_META_ON
            if (mask == Rime.META_RELEASE_ON) m = m or Rime.META_RELEASE_ON
            Timber.d(
                "<Event> getRimeEvent()\tcode=%d, mask=%d, name=%s\toutput key=%d, meta=%d",
                code,
                mask,
                keyNameOf(code),
                i,
                m,
            )
            return intArrayOf(i, m)
        }
    }
}
