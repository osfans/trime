package com.osfans.trime.util

import java.util.Locale

object LocaleUtils {
    private val DELIMITER = """[_-]""".toRegex()

    fun stringToLocale(string: String): Locale {
        return when {
            string.contains(DELIMITER) -> {
                val lc = string.split(DELIMITER)
                if (lc.size == 3) {
                    Locale(lc[0], lc[1], lc[2])
                } else {
                    Locale(lc[0], lc[1])
                }
            }
            else -> Locale(string)
        }
    }
}
