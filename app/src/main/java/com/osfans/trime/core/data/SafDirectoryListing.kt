/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.github.whiredplanck.storageaccess.StorageDocument
import java.io.IOException

/**
 * Complete direct-child listings for synchronization and atomic replacement.
 * Read the provider cursor directly to reject loading, failed or incomplete listings.
 */
internal object SafDirectoryListing {
    internal data class Row(
        val documentId: String?,
        val name: String?,
        val mimeType: String?,
        val size: Long? = null,
        val lastModified: Long? = null,
    )

    /** Keeps the completeness checks and row traversal testable without Android stubs. */
    internal interface Rows {
        val loading: Boolean
        val error: String?
        fun moveToNext(): Boolean
        fun current(): Row
    }

    internal fun readRows(rows: Rows?, excludedDocumentIds: Set<String> = emptySet()): List<Row> {
        if (rows == null) throw IOException("Document provider returned no listing")
        fun requireComplete() {
            if (rows.loading) throw IOException("Document listing is still loading")
            rows.error?.takeIf(String::isNotBlank)?.let {
                throw IOException("Document provider failed to list: $it")
            }
        }
        requireComplete()
        val result = mutableListOf<Row>()
        val ids = excludedDocumentIds.toMutableSet()
        val names = mutableSetOf<String>()
        while (rows.moveToNext()) {
            val row = rows.current()
            require(!row.documentId.isNullOrEmpty()) { "External entry has no document ID" }
            SyncRelativePath.requireSafeName(row.name)
            require(!row.mimeType.isNullOrEmpty()) { "External entry has no MIME type: ${row.documentId}" }
            require(row.size == null || row.size >= 0) { "External entry has negative size: ${row.documentId}" }
            require(row.lastModified == null || row.lastModified >= 0) { "External entry has negative timestamp: ${row.documentId}" }
            check(ids.add(row.documentId)) { "Remote directory contains repeated document ID: ${row.documentId}" }
            check(names.add(checkNotNull(row.name))) { "Remote directory contains duplicate name: ${row.name}" }
            result.add(row)
        }
        requireComplete()
        return result
    }

    fun list(resolver: ContentResolver, dirUri: Uri): List<StorageDocument> {
        val segments = dirUri.pathSegments
        require(
            dirUri.scheme == "content" && !dirUri.authority.isNullOrEmpty() &&
                segments.size in setOf(2, 4) && segments[0] == "tree" &&
                (segments.size == 2 || segments[2] == "document"),
        ) { "Expected a SAF tree directory URI: $dirUri" }
        val rootId = DocumentsContract.getTreeDocumentId(dirUri)
        require(rootId.isNotEmpty()) { "External tree has no root document ID" }
        val directoryId = if (segments.size == 2) rootId else DocumentsContract.getDocumentId(dirUri)
        require(directoryId.isNotEmpty()) { "External directory has no document ID" }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, directoryId)
        val cursor = resolver.query(childrenUri, PROJECTION, null, null, null)
            ?: throw IOException("Document provider returned no listing for $childrenUri")
        return cursor.use {
            val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            readRows(
                object : Rows {
                    override val loading: Boolean
                        get() = it.extras.getBoolean(DocumentsContract.EXTRA_LOADING)
                    override val error: String?
                        get() = it.extras.getString(DocumentsContract.EXTRA_ERROR)
                    override fun moveToNext(): Boolean = it.moveToNext()
                    override fun current(): Row = Row(
                        it.getString(idColumn),
                        it.getString(nameColumn),
                        it.getString(mimeColumn),
                        if (it.isNull(sizeColumn)) null else it.getLong(sizeColumn),
                        if (it.isNull(modifiedColumn)) null else it.getLong(modifiedColumn),
                    )
                },
                setOf(rootId, directoryId),
            ).map { row ->
                val mimeType = checkNotNull(row.mimeType)
                val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                StorageDocument(
                    uri = DocumentsContract.buildDocumentUriUsingTree(dirUri, checkNotNull(row.documentId)),
                    name = checkNotNull(row.name),
                    isDir = isDirectory,
                    length = row.size ?: 0L,
                    lastModified = row.lastModified ?: 0L,
                    mimeType = if (isDirectory) null else mimeType,
                )
            }
        }
    }

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
}
