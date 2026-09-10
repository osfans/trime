/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    fun listThemes(dir: File): MutableList<ThemeItem> {
        val files = dir.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val configId = file.nameWithoutExtension
                val name = readSourceName(configId, file)
                    ?: readDeployedName(configId)
                    ?: configId.removeSuffix(".trime")
                ThemeItem(configId, name)
            }.toMutableList()
    }

    /**
     * Reads the theme name from its source file, expanding the supported DSL
     * subset so a name provided by an `__include`d node is resolved too.
     */
    private fun readSourceName(configId: String, file: File): String? = runCatching { ThemeLoader.loadSourceNode(configId, file)?.mapping?.get("name")?.string }
        .getOrElse { e ->
            Timber.w("Failed to decode theme file ${file.absolutePath}: ${e.message}")
            null
        }

    /** Reads the theme name from the deployed artifact, when it exists. */
    private fun readDeployedName(configId: String): String? {
        val file = File(DataManager.resolveDeployedResourcePath(configId))
        if (!file.isFile) return null
        return runCatching { Yaml.parseToYamlNode(file.readText()).mapping?.get("name")?.string }
            .getOrElse { e ->
                Timber.w("Failed to read deployed theme ${file.absolutePath}: ${e.message}")
                null
            }
    }
}
