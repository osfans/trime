// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import com.osfans.trime.core.data.UserDataManager
import timber.log.Timber
import java.io.File

object ResourceUtils {
    /** Copy files from assets */
    fun copyFile(
        path: String,
        dest: String,
    ): Result<Long> = runCatching {
        val assets = appContext.assets.list(path)
        if (!assets.isNullOrEmpty()) {
            assets.fold(0L) { acc, asset ->
                acc + copyFile("$path/$asset", "$dest/$asset").getOrThrow()
            }
        } else {
            appContext.assets.open(path).use { i ->
                UserDataManager.writeFromStream(File(dest)) { output -> i.copyTo(output) }
            }
        }
    }.onFailure { Timber.e(it, "Caught a error in copying assets") }
}
