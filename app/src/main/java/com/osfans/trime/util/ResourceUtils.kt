// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import timber.log.Timber
import java.io.File

object ResourceUtils {
    fun copyFile(
        filename: String,
        dest: File,
        removedPrefix: String = "",
    ) = runCatching {
        appContext.assets.open(filename).use { i ->
            File(dest, filename.removePrefix(removedPrefix))
                .also { it.parentFile?.mkdirs() }
                .outputStream()
                .use { o -> i.copyTo(o) }
        }
    }.onFailure { Timber.e(it, "Caught a error in copying assets") }

    fun copyFiles(
        assetPath: String,
        destFile: File,
        removedPrefix: String = "",
    ): Result<Long> {
        return runCatching {
            val formattedDestPath = assetPath.removePrefix(removedPrefix)
            val files = appContext.assets.list(assetPath)
            if (files?.isNotEmpty() == true) {
                files.fold(0L) { acc, file ->
                    acc + copyFiles("$assetPath/$file", File(destFile, formattedDestPath), file).getOrDefault(0L)
                }
            } else {
                appContext.assets.open(assetPath).use { i ->
                    File(destFile, formattedDestPath.split(File.pathSeparator).last())
                        .also { it.parentFile?.mkdirs() }
                        .outputStream()
                        .use { o -> i.copyTo(o) }
                }
            }
        }.onFailure { Timber.e(it, "Caught a error in copying assets") }
    }
}
