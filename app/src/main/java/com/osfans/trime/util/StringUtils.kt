package com.osfans.trime.util

import android.view.KeyEvent
import kotlin.math.max
import kotlin.math.min

object StringUtils {
    private const val SECTION_DIVIDER = ",.?!~:，。：～？！…\t\r\n\\/"

    fun findNextSection(str: CharSequence?, start: Int): Int {
        if (str != null) {
            var i = Math.max(0, start)
            if (i < str.length) {
                var c = str[i]
                var judge =
                    if (SECTION_DIVIDER.indexOf(c) < 0) true else false
                while (i < str.length) {
                    c = str[i]
                    if (SECTION_DIVIDER.indexOf(c) < 0) judge =
                        true else if (judge) {
                        return i
                    }
                    i++
                }
            }
        }
        return str!!.length
    }

    fun findPrevSection(str: CharSequence?, start: Int): Int {
        if (str != null) {
            var i = Math.min(start, str.length) - 1
            if (i >= 0) {
                var c = str[i]
                var judge =
                    if (SECTION_DIVIDER.indexOf(c) < 0) true else false
                while (i >= 0) {
                    c = str[i]
                    if (SECTION_DIVIDER.indexOf(c) < 0) judge =
                        true else if (judge) {
                        return i
                    }
                    i--
                }
            }
        }
        return 0
    }

    @JvmStatic
    fun String.replace(rules: Array<String>): String {
        var s = this
        for (r in rules) {
            s = s.replace(r.toRegex(), "")
            if (s.isEmpty()) return ""
        }
        return s
    }

    @JvmStatic
    fun String.mismatch(rules: Array<String>): Boolean {
        if (this.isEmpty()) return false
        for (r in rules) {
            if (this.matches(r.toRegex())) return false
        }
        return true
    }

    // KeyCode Android keycode -> 可录入的按键字符
    // 考虑到可能存在魔改机型的keycode有差异，而KeyEvent.keyCodeToString(keyCode)无法从keyCode获得按键字符，故重写这个从keyCode获取Char的方法。
    @JvmStatic
    fun toCharString(keyCode: Int): String {
        when (keyCode) {
            KeyEvent.KEYCODE_TAB -> return "\t"
            KeyEvent.KEYCODE_SPACE -> return " "
            KeyEvent.KEYCODE_PLUS -> return "+"
            KeyEvent.KEYCODE_MINUS -> return "-"
            KeyEvent.KEYCODE_STAR -> return "*"
            KeyEvent.KEYCODE_SLASH -> return "/"
            KeyEvent.KEYCODE_EQUALS -> return "="
            KeyEvent.KEYCODE_AT -> return "@"
            KeyEvent.KEYCODE_POUND -> return "#"
            KeyEvent.KEYCODE_APOSTROPHE -> return "'"
            KeyEvent.KEYCODE_BACKSLASH -> return "\\"
            KeyEvent.KEYCODE_COMMA -> return ","
            KeyEvent.KEYCODE_PERIOD -> return "."
            KeyEvent.KEYCODE_LEFT_BRACKET -> return "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> return "]"
            KeyEvent.KEYCODE_SEMICOLON -> return ";"
            KeyEvent.KEYCODE_GRAVE -> return "`"
            KeyEvent.KEYCODE_NUMPAD_ADD -> return "+"
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> return "-"
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> return "*"
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> return "/"
            KeyEvent.KEYCODE_NUMPAD_EQUALS -> return "="
            KeyEvent.KEYCODE_NUMPAD_COMMA -> return ","
            KeyEvent.KEYCODE_NUMPAD_DOT -> return "."
            KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> return "("
            KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> return ")"
        }
        var c = 0
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            c = '0'.code + keyCode - KeyEvent.KEYCODE_0
        } else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            c = '0'.code + keyCode - KeyEvent.KEYCODE_NUMPAD_0
        } else if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            c = 'a'.code + keyCode - KeyEvent.KEYCODE_A
        }
        return if (c > 0) c.toChar().toString() else ""
    }
}
