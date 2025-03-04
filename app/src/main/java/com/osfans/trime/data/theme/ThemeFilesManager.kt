/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    fun listThemes(dir: File): MutableList<Theme> {
        val files = dir.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val theme =
                    runCatching {
                        Theme(it.nameWithoutExtension, parseOnly = true)
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode theme
            }.toMutableList()
    }
}
