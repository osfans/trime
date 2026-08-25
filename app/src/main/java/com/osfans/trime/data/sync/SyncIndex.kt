// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import com.osfans.trime.util.appContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SyncEntry(
    val size: Long,
    val lastModified: Long,
)

@Serializable
data class SyncIndexData(
    val entries: Map<String, SyncEntry> = emptyMap(),
)

object SyncIndex {
    private const val INDEX_FILE = "rime_sync_index.json"

    private val json = Json { ignoreUnknownKeys = true }

    private val indexFile: File
        get() = File(appContext.filesDir, INDEX_FILE)

    fun load(): SyncIndexData =
        indexFile
            .takeIf { it.exists() }
            ?.readText()
            ?.let { runCatching { json.decodeFromString<SyncIndexData>(it) }.getOrNull() }
            ?: SyncIndexData()

    fun save(data: SyncIndexData) {
        indexFile.writeText(json.encodeToString(data))
    }

    fun shouldCopy(
        relativePath: String,
        size: Long,
        lastModified: Long,
        index: SyncIndexData,
    ): Boolean {
        val cached = index.entries[relativePath] ?: return true
        return cached.size != size || cached.lastModified != lastModified
    }
}
