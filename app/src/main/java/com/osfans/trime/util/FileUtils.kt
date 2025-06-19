// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import java.io.File
import java.io.IOException

object FileUtils {
    fun rename(
        src: File,
        newName: String,
    ): Result<File> {
        if (!src.exists()) {
            return Result.failure(NoSuchFileException(src))
        }
        if (newName.isBlank()) {
            return Result.failure(IllegalArgumentException("New name is blank"))
        }
        if (newName == src.name) return Result.success(src)
        val newFile = src.resolveSibling(newName)
        return if (!newFile.exists() && src.renameTo(newFile)) {
            Result.success(newFile)
        } else {
            Result.failure(IllegalStateException("Rename file '${src.name}' to $newName failed"))
        }
    }

    fun delete(file: File) =
        runCatching {
            if (!file.exists()) return Result.success(Unit)
            val res =
                if (file.isDirectory) {
                    file
                        .walkBottomUp()
                        .fold(true) { acc, file ->
                            if (file.exists()) file.delete() else acc
                        }
                } else {
                    file.delete()
                }
            if (!res) {
                throw IOException("Cannot delete ${file.path}")
            }
        }
}
