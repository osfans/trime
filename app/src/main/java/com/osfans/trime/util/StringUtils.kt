package com.osfans.trime.util

import kotlin.math.max
import kotlin.math.min

object StringUtils {
    private const val SECTION_DIVIDER = ",.?!~:，。：～？！…\t\r\n\\/"

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

    fun String.replace(rules: Array<String>): String {
        var s = this
        for (r in rules) {
            s = s.replace(r.toRegex(), "")
            if (s.isEmpty()) return ""
        }
        return s
    }

    fun String.mismatch(rules: Array<String>): Boolean {
        if (this.isEmpty()) return false
        for (r in rules) {
            if (this.matches(r.toRegex())) return false
        }
        return true
    }
}
