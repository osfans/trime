// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

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
    }
}
