/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import java.security.MessageDigest

/**
 * Holds sticky digests for app-owned sensitive clipboard writes.
 *
 * Android did not expose sensitive [android.content.ClipDescription] metadata before API 24, so
 * Trime also suppresses callbacks caused by its own writes. Clipboard callbacks read the current
 * value rather than an event snapshot, so callbacks queued before and after a sensitive write can
 * all expose its newest digest. Matching callbacks therefore remain suppressed until a different
 * readable identity arrives or the state is explicitly discarded.
 */
internal class OneShotClipboardSuppression {
    private data class Pending(
        val generation: Long,
        val fingerprint: ByteArray,
    )

    private val lock = Any()
    private var generation = 0L
    private val pending = ArrayDeque<Pending>()

    /** Arm before invoking [write], and disarm this generation if [write] fails. */
    fun <T> write(
        fingerprint: ByteArray,
        write: () -> T,
    ): T {
        val armed =
            synchronized(lock) {
                generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
                Pending(generation, fingerprint.copyOf()).also { pending.addLast(it) }
            }
        return try {
            val result = write()
            synchronized(lock) {
                if (pending.lastOrNull()?.generation == armed.generation) {
                    pending.clear()
                    pending.addLast(armed)
                } else {
                    pending.removeAll { it.generation == armed.generation }
                }
            }
            result
        } catch (failure: Throwable) {
            synchronized(lock) {
                pending.removeAll { it.generation == armed.generation }
            }
            throw failure
        }
    }

    /** Suppress every match; clear stale state and allow the first readable mismatch. */
    fun consume(fingerprint: ByteArray?): Boolean {
        synchronized(lock) {
            if (pending.isEmpty()) return false
            if (fingerprint == null) return true
            if (pending.none { MessageDigest.isEqual(it.fingerprint, fingerprint) }) {
                pending.clear()
                return false
            }
            return true
        }
    }

    /** Consume pending state when the callback content must not be read. */
    fun discard() {
        synchronized(lock) { pending.clear() }
    }
}
