package com.osfans.trime.util

fun String.removeRegexSet(regexSet: Set<Regex>): String {
    regexSet.forEach { replace(it, String.EMPTY) }
    return this
}

fun String.matchesAny(regexSet: Set<Regex>): Boolean = regexSet.any { it.matches(this) }
