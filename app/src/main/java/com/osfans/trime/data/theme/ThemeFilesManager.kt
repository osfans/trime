/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    private val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                    anchorsAndAliases = AnchorsAndAliases.Permitted(200u),
                ),
        )

    fun listThemes(dir: File): MutableList<ThemeStub> {
        val files = dir.listFiles { _, name -> name.endsWith("trime.yaml") } ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val theme =
                    runCatching {
                        yaml
                            .decodeFromString(
                                ThemeStub.serializer(),
                                it.inputStream().bufferedReader().readText(),
                            ).apply {
                                configId = it.nameWithoutExtension
                            }
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode theme
            }.toMutableList()
    }
}
