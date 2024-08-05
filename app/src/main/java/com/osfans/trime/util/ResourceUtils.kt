// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import timber.log.Timber
import java.io.File

object ResourceUtils {
    fun copyFile(
        path: String,
        dest: File,
        baseToDest: Boolean = false,
    ): Result<Long> =
        runCatching {
            val destFileName = if (baseToDest) path.substringAfterLast('/') else path
            val assets = appContext.assets.list(path)
            if (!assets.isNullOrEmpty()) {
                assets.fold(0L) { acc, asset ->
                    acc + copyFile("$path/$asset", File(dest, destFileName), baseToDest).getOrDefault(0L)
                }
            } else {
                appContext.assets.open(path).use { i ->
                    File(dest, destFileName)
                        .also { it.parentFile?.mkdirs() }
                        .outputStream()
                        .use { o -> i.copyTo(o) }
                }
            }
        }.onFailure { Timber.e(it, "Caught a error in copying assets") }
}
