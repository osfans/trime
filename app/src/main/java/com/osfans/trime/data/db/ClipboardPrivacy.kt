/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import com.osfans.trime.ime.core.PrivacyModeState

/** Compatibility policy for the sensitive-content marker carried by [ClipDescription]. */
internal object SensitiveClipboardCompat {
    // ClipDescription.EXTRA_IS_SENSITIVE was added in API 33. The documented key works for
    // producers compiled with an older SDK as well, while ClipDescription extras exist on API 24+.
    internal const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    fun isSensitive(description: ClipDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return isSensitiveAtLeastN(description)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun isSensitiveAtLeastN(description: ClipDescription): Boolean = isSensitive(Build.VERSION.SDK_INT) { key ->
        description.extras?.getBoolean(key)
    }

    /** Mark app-created content so Android and Trime both treat it as non-historical. */
    fun markSensitive(clipData: ClipData): ClipData {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) markSensitiveAtLeastN(clipData)
        return clipData
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun markSensitiveAtLeastN(clipData: ClipData) {
        val extras = clipData.description.extras?.let(::PersistableBundle) ?: PersistableBundle()
        extras.putBoolean(EXTRA_IS_SENSITIVE, true)
        clipData.description.extras = extras
    }

    internal fun isSensitive(
        sdkInt: Int,
        readBooleanExtra: (String) -> Boolean?,
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.N) return false
        // Malformed unparcelled metadata must not turn a marked secret into clipboard history.
        return runCatching { readBooleanExtra(EXTRA_IS_SENSITIVE) == true }.getOrDefault(true)
    }
}

/** Makes the process-wide persistence lease explicit and testable. */
internal class ClipboardPersistenceGate(
    private val acquirePermit: () -> Long?,
    private val acquireObservedPermit: (Long) -> Long? = { acquirePermit() },
    private val validatePermit: (Long) -> Boolean,
    private val releasePermit: (Long) -> Unit = {},
) {
    fun acquire(isSensitive: Boolean): Long? = if (isSensitive) null else acquirePermit()

    fun acquire(
        isSensitive: Boolean,
        observedEpoch: Long,
    ): Long? = if (isSensitive) null else acquireObservedPermit(observedEpoch)

    fun allows(permit: Long): Boolean = validatePermit(permit)

    fun release(permit: Long) = releasePermit(permit)

    /**
     * Check both sides of a Room transaction's mutation block. A granted lease stays valid across
     * a later privacy boundary, which linearizes the write before that boundary without blocking
     * the IME thread. The checks still catch misuse such as an early release.
     */
    suspend fun <T> guardTransaction(
        permit: Long,
        block: suspend () -> T,
    ): T {
        requireAllowed(permit)
        val value = block()
        requireAllowed(permit)
        return value
    }

    private fun requireAllowed(permit: Long) {
        if (!allows(permit)) throw PersistencePermitExpiredException()
    }
}

internal class PersistencePermitExpiredException : RuntimeException()

internal val clipboardPersistenceGate =
    ClipboardPersistenceGate(
        acquirePermit = PrivacyModeState::acquirePersistencePermit,
        acquireObservedPermit = PrivacyModeState::acquirePersistencePermit,
        validatePermit = PrivacyModeState::allowsPersistence,
        releasePermit = PrivacyModeState::releasePersistencePermit,
    )
