// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

@Suppress("NOTHING_TO_INLINE")
inline fun Char.isAsciiPrintable(): Boolean = code in 32 until 127
