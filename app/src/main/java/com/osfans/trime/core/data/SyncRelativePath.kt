// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data

import java.io.File

object SyncRelativePath {
    class PathEscapeException(
        relativePath: String,
    ) : RuntimeException("Relative path escapes sync root: $relativePath")

    fun normalize(relativePath: String): String {
        val normalized = relativePath.replace('\\', '/').trimStart('/').removePrefix("./")
        if (normalized.isBlank()) {
            throw PathEscapeException(relativePath)
        }
        val segments = normalized.split('/')
        if (segments.any { it == "." || it == ".." || it.isEmpty() || '\u0000' in it }) {
            throw PathEscapeException(relativePath)
        }
        return segments.joinToString("/")
    }

    fun requireSafeName(name: String?) {
        require(
            !name.isNullOrBlank() && name != "." && name != ".." &&
                name.none { it == '/' || it == '\\' || it == '\u0000' },
        ) { "Unsafe document name: $name" }
    }

    fun isContained(root: File, file: File): Boolean = file.canonicalPath == root.canonicalPath ||
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)

    // A link inside the root can still bypass exclusions such as *.userdb.
    fun isDirectPath(root: File, file: File): Boolean = isContained(root, file) &&
        file.canonicalFile == File(root.canonicalFile, file.relativeTo(root).path)

    fun resolveContained(
        root: File,
        relativePath: String,
    ): File {
        val normalized = normalize(relativePath)
        val rootCanonical = root.canonicalFile
        val requested = File(rootCanonical, normalized)
        val resolved = requested.canonicalFile
        if (requested != resolved || !isContained(rootCanonical, resolved)) {
            throw PathEscapeException(relativePath)
        }
        return resolved
    }
}
