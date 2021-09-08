package com.osfans.trime.util

import android.view.KeyEvent
import kotlin.math.max
import kotlin.math.min

object StringUtils {
    private const val SECTION_DIVIDER = ",.?!~:，。：～？！…\t\r\n\\/"

    @JvmStatic
    fun CharSequence.findNextSection(start: Int): Int {
        if (this.isNotEmpty()) {
            if (max(0, start) < this.length) {
                val char = this[max(0, start)]
                var judge = SECTION_DIVIDER.indexOf(char) < 0
                for (i in max(0, start) until this.length) {
                    if (SECTION_DIVIDER.indexOf(this[i]) < 0) {
                        judge = true
                    } else if (judge) {
                        return i
                    }
                }
            }
        }
        return 0
    }

    @JvmStatic
    fun CharSequence.findPrevSection(start: Int): Int {
        if (this.isNotEmpty()) {
            if (min(start, this.length) - 1 >= 0) {
                val char = SECTION_DIVIDER[min(start, this.length) - 1]
                var judge = SECTION_DIVIDER.indexOf(char) < 0
                for (i in min(start, this.length) - 1 downTo 0) {
                    if (SECTION_DIVIDER.indexOf(this[i]) < 0) {
                        judge = true
                    } else if (judge) {
                        return i
                    }
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

    /**
     * Custom method to get the chars by the keycodes.
     * Some OEM will modify devices' keycodes, while [KeyEvent.keyCodeToString]
     * is unable to get a key's char by a keycode, which is this method manage
     * to do.
     *
     * @param keyCode the pressed key's code
     * @return the character corresponding to the key
     */
    @JvmStatic
    fun toCharString(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_SPACE -> " "
            KeyEvent.KEYCODE_PLUS -> "+"
            KeyEvent.KEYCODE_MINUS -> "-"
            KeyEvent.KEYCODE_STAR -> "*"
            KeyEvent.KEYCODE_SLASH -> "/"
            KeyEvent.KEYCODE_EQUALS -> "="
            KeyEvent.KEYCODE_AT -> "@"
            KeyEvent.KEYCODE_POUND -> "#"
            KeyEvent.KEYCODE_APOSTROPHE -> "'"
            KeyEvent.KEYCODE_BACKSLASH -> "\\"
            KeyEvent.KEYCODE_COMMA -> ","
            KeyEvent.KEYCODE_PERIOD -> "."
            KeyEvent.KEYCODE_LEFT_BRACKET -> "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
            KeyEvent.KEYCODE_SEMICOLON -> ";"
            KeyEvent.KEYCODE_GRAVE -> "`"
            KeyEvent.KEYCODE_NUMPAD_ADD -> "+"
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> "-"
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "*"
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> "/"
            KeyEvent.KEYCODE_NUMPAD_EQUALS -> "="
            KeyEvent.KEYCODE_NUMPAD_COMMA -> ","
            KeyEvent.KEYCODE_NUMPAD_DOT -> "."
            KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> "("
            KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> ")"
            else -> {
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
    }
}
