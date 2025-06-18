/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.core.RimeConfig
import com.osfans.trime.data.base.DataManager
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
        val deployedMap = hashMapOf<String, String>()
        DataManager.stagingDir.list()?.forEach {
            deployedMap[it] = it
        }
        DataManager.prebuiltDataDir.list()?.forEach {
            deployedMap[it] = it
        }
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val item =
                    runCatching {
                        val configId = it.nameWithoutExtension
                        val name =
                            if (deployedMap[it.name] != null) {
                                RimeConfig.openConfig(configId).use {
                                    it.getString("name")
                                }
                            } else {
                                configId.removeSuffix(".trime")
                            }
                        ThemeItem(configId, name)
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode item
            }.toMutableList()
    }
}
