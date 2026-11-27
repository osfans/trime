// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.opencc

import com.osfans.trime.core.data.DataManager
import com.osfans.trime.core.data.UserDataManager
import timber.log.Timber
import java.io.File

object OpenCCDictManager {
    private const val TEXT_EXTENSION = "txt"
    private const val BINARY_EXTENSION = "ocd2"

    init {
        System.loadLibrary("rime_jni")
    }

    // built-in dictionaries are already .ocd2; these are the only places a text
    // dictionary can come from
    private fun openccDirs(): List<File> = listOf(
        File(DataManager.sharedDataDir, "opencc"),
        File(UserDataManager.userDataDir, "opencc"),
    ).onEach { it.mkdirs() }

    /**
     * Compile the text dictionaries in [openccDirs], so a dictionary the user
     * adds there becomes usable and edits to a compiled one are picked up.
     */
    @JvmStatic
    fun buildOpenCCDict() {
        val outdated =
            openccDirs().flatMap { dir ->
                dir.listFiles { file -> file.isFile && file.extension == TEXT_EXTENSION }
                    .orEmpty()
                    .map { source -> source to source.resolveSibling("${source.nameWithoutExtension}.$BINARY_EXTENSION") }
                    .filterNot { (source, dest) -> dest.isFile && dest.lastModified() >= source.lastModified() }
            }
        outdated.forEach { (source, dest) ->
            runCatching { openCCDictConv(source.absolutePath, dest.absolutePath) }
                .onSuccess { Timber.d("Converted $source to $dest") }
                .onFailure {
                    // a half written dictionary would look up to date next time
                    dest.delete()
                    Timber.e(it, "Failed to convert $source")
                }
        }
    }

    @JvmStatic
    external fun openCCDictConv(
        src: String,
        dest: String,
    )
}
