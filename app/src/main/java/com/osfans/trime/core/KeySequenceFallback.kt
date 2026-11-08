/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

/**
 * Called after native KeySequence parsing succeeds without producing input or a commit.
 * Its grammar treats every nonterminal '{' as a command opener; a final '{' and '}'
 * remain literal characters. Never turn an executed control command back into source text.
 */
internal fun literalKeySequenceFallback(sequence: String): String? {
    if (sequence.isEmpty()) return null
    val commandStart = sequence.indexOf('{')
    return sequence.takeUnless { commandStart >= 0 && commandStart < sequence.lastIndex }
}
