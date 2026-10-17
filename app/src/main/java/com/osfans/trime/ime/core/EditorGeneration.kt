/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import java.util.concurrent.atomic.AtomicLong

/** Tokens are process-wide, so a recreated service cannot accept its predecessor's output. */
class EditorGeneration {
    @Volatile var token: Long = next.incrementAndGet()
        private set

    fun advance(): Long = next.incrementAndGet().also { token = it }

    fun accepts(origin: Long): Boolean = origin == token

    private companion object {
        val next = AtomicLong()
    }
}
