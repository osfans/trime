package com.osfans.trime.util

@Suppress("NOTHING_TO_INLINE")
inline fun Char.isAsciiPrintable(): Boolean = code in 32 until 127
