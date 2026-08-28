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
    private const val SKIP_DIR_SUBSTRING = ".userdb"

    private val documentProjection =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

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

    fun listFiles(
        contentResolver: ContentResolver,
        treeUri: Uri,
        rootDocumentId: String,
        skipUserDb: Boolean = true,
    ): List<SafFileEntry> = listTree(contentResolver, treeUri, rootDocumentId, skipUserDb).files

    fun listTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        rootDocumentId: String,
        skipUserDb: Boolean = true,
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
                    val isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == mimeType
                    if (shouldSkip(childPath, isDirectory, skipUserDb)) continue
                    if (isDirectory) {
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

    fun findFileEntry(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
        includeDirectories: Boolean = false,
    ): SafFileEntry? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val cursor =
            contentResolver.query(childrenUri, documentProjection, null, null, null)
                ?: throw SafQueryException(childrenUri, displayName)
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (it.moveToNext()) {
                val name = it.getString(nameCol)
                if (name == displayName) {
                    val mimeType = it.getString(mimeCol)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR && !includeDirectories) {
                        continue
                    }
                    return SafFileEntry(
                        documentId = it.getString(idCol),
                        displayName = name,
                        mimeType = mimeType,
                        size = it.getLong(sizeCol),
                        lastModified = it.getLong(modCol),
                        relativePath = name,
                    )
                }
            }
        }
        return null
    }

    fun findChildDocumentId(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
    ): String? = findFileEntry(contentResolver, treeUri, parentDocumentId, displayName, includeDirectories = true)
        ?.documentId
}
