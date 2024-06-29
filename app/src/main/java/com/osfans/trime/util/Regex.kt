// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

fun String.removeRegexSet(regexSet: Set<Regex>): String {
    regexSet.forEach { replace(it, String.EMPTY) }
    return this
}

fun String.matchesAny(regexSet: Set<Regex>): Boolean = regexSet.any { it.matches(this) }
