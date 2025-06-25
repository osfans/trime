/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.fasterxml.jackson.module.kotlin.readValue
import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.legacyYAMLMapper
import timber.log.Timber

object ThemeFilesManager {
    private const val CONFIG_VERSION_KEY = "config_version"

    fun listThemes(): MutableList<Theme> {
        val files =
            DataManager.stagingDir.listFiles { _, name ->
                name.endsWith("trime.yaml")
            } ?: return mutableListOf()
        return files
            .sortedByDescending { it.lastModified() }
            .mapNotNull decode@{
                val theme =
                    runCatching {
                        legacyYAMLMapper().readValue<Theme>(it.inputStream())
                    }.getOrElse { e ->
                        Timber.w("Failed to decode theme file ${it.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode theme.copy(
                    id = it.nameWithoutExtension.removeSuffix(".trime"),
                )
            }.toMutableList()
    }

    fun deployThemes(progress: ((Int, Int) -> Unit)? = null) {
        val users = DataManager.userDataDir.list() ?: emptyArray()
        val shareds = DataManager.sharedDataDir.list() ?: emptyArray()
        val distinct = (users + shareds).distinct()
        progress?.invoke(0, distinct.size)
        distinct.forEachIndexed { i, fileName ->
            Rime.deployRimeConfigFile(fileName.removeSuffix(".yaml"), CONFIG_VERSION_KEY)
            progress?.invoke(i + 1, distinct.size)
        }
        ThemeManager.refreshThemes()
    }
}
