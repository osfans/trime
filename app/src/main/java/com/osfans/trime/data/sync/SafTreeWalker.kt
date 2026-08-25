// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

data class SafFileEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val relativePath: String,
)

data class SafTreeListing(
    val files: List<SafFileEntry>,
    val directoryIds: Map<String, String>,
)

object SafTreeWalker {
    private const val SKIP_DIR = "build"

    private val documentProjection =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

    fun shouldSkip(relativePath: String): Boolean {
        val normalized = relativePath.trimStart('/').trim().removePrefix("./")
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/')
        return segments.any { it == SKIP_DIR }
    }

    fun listFiles(
        contentResolver: ContentResolver,
        treeUri: Uri,
        rootDocumentId: String,
    ): List<SafFileEntry> = listTree(contentResolver, treeUri, rootDocumentId).files

    fun listTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        rootDocumentId: String,
    ): SafTreeListing {
        val result = mutableListOf<SafFileEntry>()
        val directoryIds = mutableMapOf("" to rootDocumentId)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to rootDocumentId)

        while (queue.isNotEmpty()) {
            val (relativePath, parentId) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor =
                contentResolver.query(childrenUri, documentProjection, null, null, null)
                    ?: throw SafQueryException(childrenUri, relativePath.ifEmpty { "." })
            cursor.use {
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol)
                    val mimeType = cursor.getString(mimeCol)
                    val childPath =
                        if (relativePath.isEmpty()) {
                            name
                        } else {
                            "$relativePath/$name"
                        }
                    if (shouldSkip(childPath)) continue
                    if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                        directoryIds[childPath] = documentId
                        queue.add(childPath to documentId)
                    } else {
                        result.add(
                            SafFileEntry(
                                documentId = documentId,
                                displayName = name,
                                mimeType = mimeType,
                                size = cursor.getLong(sizeCol),
                                lastModified = cursor.getLong(modCol),
                                relativePath = childPath,
                            ),
                        )
                    }
                }
            }
        }
        return SafTreeListing(result, directoryIds)
    }

    fun findChildDocumentId(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val cursor =
            contentResolver.query(childrenUri, documentProjection, null, null, null)
                ?: throw SafQueryException(childrenUri, displayName)
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (it.moveToNext()) {
                if (it.getString(nameCol) == displayName) {
                    return it.getString(idCol)
                }
            }
        }
        return null
    }

    fun ensureDirectory(
        contentResolver: ContentResolver,
        treeUri: Uri,
        rootDocumentId: String,
        relativeDir: String,
    ): String {
        if (relativeDir.isEmpty()) return rootDocumentId
        var parentId = rootDocumentId
        for (segment in relativeDir.split('/').filter { it.isNotEmpty() }) {
            parentId =
                findChildDocumentId(contentResolver, treeUri, parentId, segment)
                    ?: run {
                        val parentUri =
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                        val created =
                            DocumentsContract.createDocument(
                                contentResolver,
                                parentUri,
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                segment,
                            ) ?: error("Failed to create directory '$segment'")
                        DocumentsContract.getDocumentId(created)
                    }
        }
        return parentId
    }
}
