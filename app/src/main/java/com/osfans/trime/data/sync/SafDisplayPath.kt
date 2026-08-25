// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.net.Uri
import android.provider.DocumentsContract

object SafDisplayPath {
    private const val EMULATED_STORAGE_PREFIX = "/storage/emulated/0"

    fun fromTreeUri(uri: Uri): String? = fromDocumentId(DocumentsContract.getTreeDocumentId(uri))

    fun fromDocumentId(documentId: String): String? {
        val absolutePath =
            when {
                documentId.startsWith("raw:") -> documentId.removePrefix("raw:")
                documentId.contains(':') -> {
                    val (volumeId, relativePath) = documentId.split(':', limit = 2)
                    when (volumeId) {
                        "primary" -> "/$relativePath"
                        else -> "/storage/$volumeId/$relativePath"
                    }
                }
                else -> return null
            }
        return stripEmulatedStoragePrefix(absolutePath)
    }

    internal fun stripEmulatedStoragePrefix(path: String): String {
        if (path == EMULATED_STORAGE_PREFIX) return "/"
        if (path.startsWith("$EMULATED_STORAGE_PREFIX/")) {
            return path.removePrefix(EMULATED_STORAGE_PREFIX)
        }
        return path
    }
}
