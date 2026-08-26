// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

object AtomicLocalFileCopy {
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

    fun copyFromInput(
        source: FileInputStream,
        destFile: File,
    ): Long = writeFromStream(destFile) { output ->
        source.copyTo(output)
    }
}
