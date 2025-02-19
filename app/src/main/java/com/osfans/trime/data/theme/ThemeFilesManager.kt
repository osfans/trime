/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    private const val CONFIG_VERSION_KEY = "config_version"

    fun listThemes(dir: File): MutableList<Theme> {
        val files = dir.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val deployed = DataManager.userDataDir.resolve("build/${it.name}")
                if (!deployed.exists()) {
                    if (!Rime.deployRimeConfigFile(it.name, CONFIG_VERSION_KEY)) {
                        Timber.w("Failed to deploy theme file ${it.absolutePath}")
                        return@decode null
                    }
                }
                val theme =
                    runCatching {
                        Theme(it.nameWithoutExtension)
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode theme
            }.toMutableList()
    }
}
