/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.composition

/** Native caret positions are UTF-8 bytes; Android text offsets are UTF-16. */
internal fun preeditCharacterBoundary(text: String, offset: Int): Int {
    val bounded = offset.coerceIn(0, text.length)
    return if (bounded > 0 && bounded < text.length &&
        text[bounded - 1].isHighSurrogate() && text[bounded].isLowSurrogate()
    ) {
        bounded - 1
    } else {
        bounded
    }
}

internal fun preeditByteOffset(text: String, offset: Int): Int = text.substring(0, preeditCharacterBoundary(text, offset)).toByteArray(Charsets.UTF_8).size

internal fun adjacentPreeditCharacter(text: String, offset: Int, forward: Boolean): Int {
    val position = preeditCharacterBoundary(text, offset)
    if ((forward && position == text.length) || (!forward && position == 0)) return position
    return Character.offsetByCodePoints(text, position, if (forward) 1 else -1)
}
