// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import java.io.File
import java.io.IOException

object FileUtils {
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
