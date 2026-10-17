/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

/** The trial accepts committed Han text, never unfinished pinyin or a restored EditText value. */
internal fun containsCommittedChinese(text: CharSequence?): Boolean {
    if (text == null) return false
    var offset = 0
    while (offset < text.length) {
        val codePoint = Character.codePointAt(text, offset)
        if (codePoint in 0x3400..0x4dbf || codePoint in 0x4e00..0x9fff ||
            codePoint in 0xf900..0xfaff || codePoint in 0x20000..0x323af
        ) {
            return true
        }
        offset += Character.charCount(codePoint)
    }
    return false
}
