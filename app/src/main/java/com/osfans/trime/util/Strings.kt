// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

val String.Companion.EMPTY: String
    get() = ""

private const val SECTION_DIVIDER = ",.?!~:，。：～？！…\t\r\n\\/"

fun CharSequence.findSectionFrom(
    start: Int,
    forward: Boolean = false,
): Int {
    if (start !in 0..lastIndex) return -1
    return if (forward) {
        val subSequence = subSequence(0, start)
        subSequence.indexOfLast { SECTION_DIVIDER.contains(it) }
    } else {
        val subSequence = subSequence(start, length)
        start + subSequence.indexOfFirst { SECTION_DIVIDER.contains(it) }
    }
}
