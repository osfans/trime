// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

object AtomicLocalFileCopy {
    fun writeFromStream(
        destFile: File,
        copy: (OutputStream) -> Unit,
    ): Long {
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        tempFile.parentFile?.mkdirs()
        try {
            FileOutputStream(tempFile).use { output ->
                copy(output)
            }
            val bytes = tempFile.length()
            if (destFile.exists() && !destFile.delete()) {
                error("Failed to replace ${destFile.path}")
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            }
            return bytes
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
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
