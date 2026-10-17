// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data.fixture

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.osfans.trime.core.data.SyncRelativePath
import java.util.concurrent.ConcurrentHashMap

class FixtureSafPathCache(
    listing: FixtureSafTreeListing,
    private val rootDocumentId: String,
) {
    private val directoryIds = ConcurrentHashMap(listing.directoryIds)
    private val fileIds = ConcurrentHashMap(listing.files.associate { it.relativePath to it.documentId })
    private val fileSizes = ConcurrentHashMap(listing.files.associate { it.relativePath to it.size })

    fun hasFile(
        relativePath: String,
        expectedSize: Long,
    ): Boolean = fileIds.containsKey(relativePath) && fileSizes[relativePath] == expectedSize

    @Synchronized
    fun ensureDirectory(
        contentResolver: ContentResolver,
        treeUri: Uri,
        relativeDir: String,
    ): String {
        if (relativeDir.isEmpty()) return rootDocumentId
        check(SyncRelativePath.normalize(relativeDir) == relativeDir)
        directoryIds[relativeDir]?.let { return it }

        val parentDir = relativeDir.substringBeforeLast('/', "")
        val segment = relativeDir.substringAfterLast('/')
        val parentId = ensureDirectory(contentResolver, treeUri, parentDir)
        val documentId =
            FixtureSafTreeWalker.findFileEntry(contentResolver, treeUri, parentId, segment)?.let {
                check(it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) { "Not a directory: $relativeDir" }
                it.documentId
            }
                ?: run {
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                    val created =
                        DocumentsContract.createDocument(
                            contentResolver,
                            parentUri,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            segment,
                        ) ?: error("Failed to create directory '$segment'")
                    DocumentsContract.getDocumentId(created)
                }
        directoryIds[relativeDir] = documentId
        return documentId
    }

    fun createOrResolveFile(
        contentResolver: ContentResolver,
        treeUri: Uri,
        relativePath: String,
        parentDir: String,
        fileName: String,
    ): Uri {
        fileIds[relativePath]?.let {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, it)
        }
        val parentId = directoryIds[parentDir] ?: ensureDirectory(contentResolver, treeUri, parentDir)
        val existingId = FixtureSafTreeWalker.findChildDocumentId(contentResolver, treeUri, parentId, fileName)
        val docUri =
            if (existingId != null) {
                fileIds[relativePath] = existingId
                DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
            } else {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                val created =
                    DocumentsContract.createDocument(
                        contentResolver,
                        parentUri,
                        "application/octet-stream",
                        fileName,
                    ) ?: error("Failed to create document $fileName")
                fileIds[relativePath] = DocumentsContract.getDocumentId(created)
                created
            }
        return docUri
    }

    fun rememberFile(
        relativePath: String,
        documentId: String,
        size: Long? = null,
    ) {
        fileIds[relativePath] = documentId
        size?.let { fileSizes[relativePath] = it }
    }
}
