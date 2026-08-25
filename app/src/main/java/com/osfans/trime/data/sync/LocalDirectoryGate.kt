// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class LocalDirectoryGate {
    private val createdDirs = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val locks = ConcurrentHashMap<String, Any>()

    fun ensure(
        root: File,
        relativeDir: String,
    ) {
        if (relativeDir.isEmpty()) return
        val lock = locks[relativeDir] ?: locks.putIfAbsent(relativeDir, Any()) ?: locks[relativeDir]!!
        synchronized(lock) {
            if (!createdDirs.add(relativeDir)) {
                return
            }
            val dir = SyncRelativePath.resolveContained(root, relativeDir)
            check(dir.mkdirs() || dir.isDirectory) { "Failed to create directory $relativeDir" }
        }
    }
}
