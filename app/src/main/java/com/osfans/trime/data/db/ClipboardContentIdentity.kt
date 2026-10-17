/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import android.content.ClipData
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Computes a content identity without retaining any clipboard representation in plaintext. */
internal object ClipboardContentIdentity {
    fun sha256OrNull(clip: ClipData): ByteArray? = runCatching { sha256(clip) }.getOrNull()

    fun sha256(clip: ClipData): ByteArray = MessageDigest.getInstance("SHA-256").run {
        updateField(clip.description.label?.toString())
        updateInt(clip.description.mimeTypeCount)
        repeat(clip.description.mimeTypeCount) { updateField(clip.description.getMimeType(it)) }
        updateInt(clip.itemCount)
        repeat(clip.itemCount) { index ->
            val item = clip.getItemAt(index)
            updateField(item.text?.toString())
            updateField(item.htmlText)
            updateField(item.uri?.toString())
            updateField(item.intent?.toUri(0))
        }
        digest()
    }

    private fun MessageDigest.updateInt(value: Int) {
        update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
    }

    private fun MessageDigest.updateField(value: String?) {
        if (value == null) {
            update(0)
            return
        }
        update(1)
        val bytes = value.toByteArray(Charsets.UTF_8)
        updateInt(bytes.size)
        update(bytes)
    }

    private fun MessageDigest.update(value: Int) {
        update(value.toByte())
    }
}
