// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.net.Uri
import com.osfans.trime.storage.StorageAccess
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

object AtomicLocalFileCopy {
    /**
     * Copies the SAF document [srcUri] into [destFile] through a temporary file next to it,
     * so that a reader of [destFile] never observes a partially written file.
     *
     * @return the number of bytes written
     */
    suspend fun copyFromSaf(
        srcUri: Uri,
        destFile: File,
    ): Long = StorageAccess.readFile(srcUri) { input ->
        writeFromStream(destFile) { output -> input.copyTo(output) }
    }

    fun writeFromStream(
        destFile: File,
        copy: (OutputStream) -> Unit,
    ): Long {
        val parent = destFile.parentFile ?: error("No parent for ${destFile.path}")
        val operationId = UUID.randomUUID().toString()
        val incoming = File(parent, ".trime-new-$operationId.tmp")
        val backup = File(parent, ".trime-bak-$operationId.tmp")
        parent.mkdirs()
        var expectedBytes = -1L
        var backedUp = false
        try {
            FileOutputStream(incoming).use { output ->
                copy(output)
            }
            expectedBytes = incoming.length()

            if (destFile.exists()) {
                if (!destFile.renameTo(backup)) {
                    error("Failed to back up ${destFile.path}")
                }
                backedUp = true
            }

            if (!incoming.renameTo(destFile)) {
                incoming.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                incoming.delete()
            }

            if (backup.exists()) {
                backup.delete()
            }

            return expectedBytes
        } catch (e: Exception) {
            if (backedUp && backup.exists()) {
                if (!destFile.exists() || destFile.length() != expectedBytes) {
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    backup.renameTo(destFile)
                }
            }
            if (!backedUp && incoming.exists()) {
                incoming.delete()
            }
            throw e
        } finally {
            if (expectedBytes >= 0 && destFile.exists() && destFile.length() == expectedBytes) {
                if (incoming.exists()) {
                    incoming.delete()
                }
                if (backup.exists()) {
                    backup.delete()
                }
            }
        }
    }
}
