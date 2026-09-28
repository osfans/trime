// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.net.Uri
import com.osfans.trime.storage.StorageAccess
import com.osfans.trime.storage.StorageWalkEntry
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList

object SafTreeWalker {
    private const val SKIP_DIR = "build"
    private const val SKIP_DIR_SUBSTRING = ".userdb"

    fun shouldSkip(
        relativePath: String,
        isDirectory: Boolean = false,
        skipUserDb: Boolean = true,
    ): Boolean {
        val normalized = relativePath.trimStart('/').trim().removePrefix("./")
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/')
        if (segments.any { it == SKIP_DIR }) return true
        if (!skipUserDb) return false
        val dirSegments = if (isDirectory) segments else segments.dropLast(1)
        return dirSegments.any { it.contains(SKIP_DIR_SUBSTRING) }
    }

    /** Whether [relativePath] should be visited; [skipPrefix] drops that path and its descendants. */
    fun shouldVisit(
        relativePath: String,
        skipPrefix: String? = null,
    ): Boolean {
        if (skipPrefix.isNullOrEmpty()) return true
        return relativePath != skipPrefix && !relativePath.startsWith("$skipPrefix/")
    }

    /** Whether [entry] takes part in the sync, both as a file to copy and as a path to keep. */
    fun shouldSync(
        entry: StorageWalkEntry,
        skipUserDb: Boolean = true,
        skipPrefix: String? = null,
    ): Boolean = !shouldSkip(entry.relativePath, entry.file.isDir, skipUserDb) &&
        shouldVisit(entry.relativePath, skipPrefix)

    /** Walks the external [treeUri] and lists every entry that should take part in the sync. */
    suspend fun listExternalEntries(
        treeUri: Uri,
        skipUserDb: Boolean = true,
        skipPrefix: String? = null,
    ): List<StorageWalkEntry> = StorageAccess.walk(treeUri)
        .filter { shouldSync(it, skipUserDb, skipPrefix) }
        .toList()
}
