@file:Suppress("NOTHING_TO_INLINE")
package com.osfans.trime.common

inline fun CharSequence.startsWithAsciiChar(): Boolean {
    val firstCodePoint = this.toString().codePointAt(0)
    return firstCodePoint in 0x20 until 0x80
}
