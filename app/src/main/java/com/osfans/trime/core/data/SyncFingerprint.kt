// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal object SyncFingerprint {
    const val BUFFER_SIZE = 32 * 1024

    fun digest(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest()
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun of(file: File): String = file.inputStream().use { hex(digest(it)) }
}
