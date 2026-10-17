// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import com.osfans.trime.core.data.SyncRelativePath
import com.osfans.trime.util.FileUtils
import java.io.File

internal fun resolveSharedAssetDestination(
    sharedDir: File,
    path: String,
): File {
    require(path.startsWith("shared/")) { "Not a shared resource: $path" }
    val relativePath = path.removePrefix("shared/")
    require(SyncRelativePath.normalize(relativePath) == relativePath) {
        "Non-canonical shared resource: $path"
    }
    return SyncRelativePath.resolveContained(sharedDir, relativePath)
}

internal object DataResourceInstaller {
    fun install(
        sharedDir: File,
        changes: List<DataDiff>,
        copyAsset: (String, File) -> Unit,
        publishChecksums: () -> Unit,
    ) {
        // Validate the whole plan before modifying any file. Never resolve an
        // asset into the adjacent runtime user directory or flatten subpaths.
        val plan = changes.sortedByDescending { it.ordinal }.map { change ->
            change to resolveSharedAssetDestination(sharedDir, change.path)
        }
        plan.forEach { (change, destination) ->
            when (change) {
                is DataDiff.CreateFile, is DataDiff.UpdateFile -> copyAsset(change.path, destination)
                is DataDiff.DeleteDir, is DataDiff.DeleteFile -> FileUtils.delete(destination).getOrThrow()
            }
        }
        publishChecksums()
    }
}
