/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    private val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                    anchorsAndAliases = AnchorsAndAliases.Permitted(null),
                ),
        )

    fun listThemes(dir: File): MutableList<ThemeItem> {
        val files = dir.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val item =
                    runCatching {
                        val map =
                            yaml
                                .parseToYamlNode(it.bufferedReader().readText())
                                .yamlMap
                        val configId = it.nameWithoutExtension
                        val name = map.get<YamlScalar>("name")?.content ?: ""
                        ThemeItem(configId, name)
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode item
            }.toMutableList()
    }
}
