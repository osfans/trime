/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import android.content.ClipData
import android.content.ClipboardManager

/** Local fallback for Trime-created sensitive clips on API 21 through 23. */
internal object AppOwnedSensitiveClipboard {
    private val suppression = OneShotClipboardSuppression()

    fun setPrimaryClip(
        manager: ClipboardManager,
        clip: ClipData,
    ) {
        val marked = SensitiveClipboardCompat.markSensitive(clip)
        val fingerprint = ClipboardContentIdentity.sha256(marked)
        suppression.write(fingerprint) { manager.setPrimaryClip(marked) }
    }

    fun consumeIfOwned(fingerprint: ByteArray?): Boolean = suppression.consume(fingerprint)

    fun discardPending() {
        suppression.discard()
    }
}
