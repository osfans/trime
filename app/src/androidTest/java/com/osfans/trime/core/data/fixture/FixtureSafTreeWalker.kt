// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data.fixture

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.util.ArrayDeque

data class FixtureSafFileEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val relativePath: String,
)

data class FixtureSafTreeListing(
    val files: List<FixtureSafFileEntry>,
    val directoryIds: Map<String, String>,
)

object FixtureSafTreeWalker {
    private const val SKIP_DIR = "build"
    private const val SKIP_DIR_SUBSTRING = ".userdb"
    private val operationArtifact = Regex("\\.trime-(?:replace|new|bak)-[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\.(?:tmp|bak)")

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
        val normalized = relativePath.trimStart('/').removePrefix("./")
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/')
        if (operationArtifact.matches(segments.last())) return true
        if (segments.any { it == SKIP_DIR }) return true
        if (!skipUserDb) return false
        val dirSegments = if (isDirectory) segments else segments.dropLast(1)
        return dirSegments.any { it.contains(SKIP_DIR_SUBSTRING) }
    }

    /**
     * Whether [relativePath] should be listed. [skipPrefix] drops that path and its
     * descendants. [limitToPrefix] keeps only ancestors of the prefix, the prefix
     * itself, and descendants. Applied before enqueue so skipped directories are
     * never queried.
     */
    fun shouldVisit(
        relativePath: String,
        skipPrefix: String? = null,
        limitToPrefix: String? = null,
    ): Boolean {
        if (!skipPrefix.isNullOrEmpty() &&
            (relativePath == skipPrefix || relativePath.startsWith("$skipPrefix/"))
        ) {
            return false
        }
        if (!limitToPrefix.isNullOrEmpty()) {
            val selfOrDescendant =
                relativePath == limitToPrefix || relativePath.startsWith("$limitToPrefix/")
            val ancestor = limitToPrefix.startsWith("$relativePath/")
            if (!selfOrDescendant && !ancestor) return false
        }
        return true
    }

    fun listFiles(
        contentResolver: ContentResolver,
        treeUri: Uri,
        rootDocumentId: String,
        skipUserDb: Boolean = true,
        skipPrefix: String? = null,
        limitToPrefix: String? = null,
    ): List<FixtureSafFileEntry> = listTree(
        contentResolver,
        treeUri,
        rootDocumentId,
        skipUserDb,
        skipPrefix,
        limitToPrefix,
    ).files

    fun listTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        rootDocumentId: String,
        skipUserDb: Boolean = true,
        skipPrefix: String? = null,
        limitToPrefix: String? = null,
    ): FixtureSafTreeListing {
        val result = mutableListOf<FixtureSafFileEntry>()
        val directoryIds = mutableMapOf("" to rootDocumentId)
        val visitedIds = mutableSetOf(rootDocumentId)
        val visitedPaths = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add("" to rootDocumentId)

        while (queue.isNotEmpty()) {
            val (relativePath, parentId) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor =
                contentResolver.query(childrenUri, documentProjection, null, null, null)
                    ?: throw FixtureSafQueryException(childrenUri, relativePath.ifEmpty { "." })
            cursor.use {
                requireComplete(cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING), childrenUri)
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol)
                    val mimeType = cursor.getString(mimeCol)
                    requireName(name)
                    check(!documentId.isNullOrEmpty() && !mimeType.isNullOrEmpty()) { "Invalid document metadata" }
                    check(visitedIds.add(documentId)) { "Repeated document ID: $documentId" }
                    val childPath =
                        if (relativePath.isEmpty()) {
                            name
                        } else {
                            "$relativePath/$name"
                        }
                    val isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == mimeType
                    if (shouldSkip(childPath, isDirectory, skipUserDb)) continue
                    if (!shouldVisit(childPath, skipPrefix, limitToPrefix)) continue
                    check(visitedPaths.add(childPath)) { "Duplicate document path: $childPath" }
                    if (isDirectory) {
                        directoryIds[childPath] = documentId
                        queue.add(childPath to documentId)
                    } else {
                        result.add(
                            FixtureSafFileEntry(
                                documentId = documentId,
                                displayName = name,
                                mimeType = mimeType,
                                size = if (cursor.isNull(sizeCol)) -1 else cursor.getLong(sizeCol),
                                lastModified = if (cursor.isNull(modCol)) 0 else cursor.getLong(modCol),
                                relativePath = childPath,
                            ),
                        )
                    }
                }
            }
        }
        return FixtureSafTreeListing(result, directoryIds)
    }

    fun findFileEntry(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
    ): FixtureSafFileEntry? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val cursor =
            contentResolver.query(childrenUri, documentProjection, null, null, null)
                ?: throw FixtureSafQueryException(childrenUri, displayName)
        cursor.use {
            requireComplete(it.extras.getBoolean(DocumentsContract.EXTRA_LOADING), childrenUri)
            val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            var found: FixtureSafFileEntry? = null
            while (it.moveToNext()) {
                val name = it.getString(nameCol)
                if (name == displayName) {
                    requireName(name)
                    check(found == null) { "Duplicate document name: $name" }
                    found = FixtureSafFileEntry(
                        documentId = it.getString(idCol),
                        displayName = name,
                        mimeType = it.getString(mimeCol),
                        size = if (it.isNull(sizeCol)) -1 else it.getLong(sizeCol),
                        lastModified = if (it.isNull(modCol)) 0 else it.getLong(modCol),
                        relativePath = name,
                    )
                }
            }
            return found
        }
    }

    internal fun requireName(name: String?) {
        require(
            !name.isNullOrBlank() && name != "." && name != ".." &&
                name.none { it == '/' || it == '\\' || it == '\u0000' },
        ) { "Unsafe document name: $name" }
    }

    private fun requireComplete(loading: Boolean, uri: Uri) {
        if (loading) throw IOException("Document listing is still loading: $uri; retry after provider finishes")
    }

    fun findChildDocumentId(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
    ): String? = findFileEntry(contentResolver, treeUri, parentDocumentId, displayName)
        ?.documentId
}
