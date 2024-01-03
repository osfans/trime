package com.osfans.trime.util

object StringUtils {
    private const val SECTION_DIVIDER = ",.?!~:，。：～？！…\t\r\n\\/"

    @JvmStatic
    fun findSectionAfter(
        cs: CharSequence?,
        startIndex: Int,
    ): Int {
        cs ?: return 0
        val index = startIndex.coerceAtLeast(0)
        for ((i, c) in cs.withIndex()) {
            if (i < index) continue
            if (SECTION_DIVIDER.contains(c)) return i
        }
        return cs.length
    }

    @JvmStatic
    fun findSectionBefore(
        cs: CharSequence?,
        startIndex: Int,
    ): Int {
        cs ?: return 0
        val index = startIndex.coerceAtMost(cs.length) - 1
        for ((i, c) in cs.withIndex().reversed()) {
            if (i > index) continue
            if (SECTION_DIVIDER.contains(c)) return i
        }
        return 0
    }

    /**
     * Remove all parts that match given [regexes] in the string.
     */
    @JvmStatic
    fun String.removeAll(regexes: Array<String>): String {
        if (this.isEmpty()) return ""
        var result = this
        for (r in regexes) {
            result = result.replace(r.toRegex(), "")
        }
        return result
    }

    /**
     * Verify if the string matches any regex in the given [regexes].
     */
    @JvmStatic
    fun String.matches(regexes: Array<String>): Boolean {
        if (this.isEmpty()) return false
        for (r in regexes) {
            if (this.matches(r.toRegex())) return true
        }
        return false
    }
}
