/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import java.security.MessageDigest

/**
 * Quarantines callbacks that were queued while privacy mode still owned the current clipboard.
 *
 * A quarantine remains armed across repeated matches. A newer timestamp or different readable
 * identity clears it; missing identity stays fail closed.
 */
internal class DelayedPrivateClipboardQuarantine {
    private sealed interface Boundary {
        data class Timestamp(
            val fingerprint: ByteArray?,
            val value: Long,
        ) : Boundary

        data class Fingerprint(
            val value: ByteArray?,
        ) : Boundary
    }

    private val lock = Any()
    private var boundary: Boundary? = null

    fun arm(
        fingerprint: ByteArray?,
        timestamp: Long?,
        timestampsSupported: Boolean,
    ) {
        synchronized(lock) {
            boundary =
                if (timestampsSupported && timestamp != null && timestamp > 0L) {
                    Boundary.Timestamp(fingerprint?.copyOf(), timestamp)
                } else {
                    Boundary.Fingerprint(fingerprint?.copyOf())
                }
        }
    }

    fun clear() {
        synchronized(lock) { boundary = null }
    }

    /** Returns true while [fingerprint]/[timestamp] can still be the private boundary content. */
    fun shouldSuppress(
        fingerprint: ByteArray?,
        timestamp: Long?,
    ): Boolean = synchronized(lock) {
        when (val current = boundary ?: return@synchronized false) {
            is Boundary.Timestamp -> {
                val newerTimestamp = timestamp != null && timestamp > current.value
                val demonstrablyDifferent =
                    current.fingerprint != null &&
                        fingerprint != null &&
                        !MessageDigest.isEqual(current.fingerprint, fingerprint)
                if (newerTimestamp || demonstrablyDifferent) {
                    boundary = null
                    false
                } else {
                    true
                }
            }

            is Boundary.Fingerprint -> {
                compareFingerprint(current.value, fingerprint)
            }
        }
    }

    private fun compareFingerprint(
        privateFingerprint: ByteArray?,
        fingerprint: ByteArray?,
    ): Boolean = when {
        fingerprint == null -> true

        privateFingerprint == null -> {
            boundary = Boundary.Fingerprint(fingerprint.copyOf())
            true
        }

        MessageDigest.isEqual(privateFingerprint, fingerprint) -> true

        else -> {
            boundary = null
            false
        }
    }
}
