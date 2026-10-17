// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data.fixture

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.osfans.trime.core.data.SyncFingerprint
import java.io.File
import java.util.UUID

object FixtureAtomicSafFileCopy {
    /**
     * Stage and read back before replacing an existing document. Providers without
     * rename support may receive new files, but cannot safely replace existing ones.
     * A failed rollback retains the operation-scoped backup for manual recovery.
     */
    fun copyFromFile(
        contentResolver: ContentResolver,
        treeUri: Uri,
        cache: FixtureSafPathCache,
        sourceFile: File,
        relativePath: String,
        parentDir: String,
        fileName: String,
    ): String {
        FixtureSafTreeWalker.requireName(fileName)
        check(relativePath == if (parentDir.isEmpty()) fileName else "$parentDir/$fileName")
        val parentId = cache.ensureDirectory(contentResolver, treeUri, parentDir)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        val existing = FixtureSafTreeWalker.findFileEntry(contentResolver, treeUri, parentId, fileName)
        check(existing?.mimeType != Document.MIME_TYPE_DIR) { "Destination is a directory: $fileName" }
        val existingUri = existing?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId) }
        if (existingUri != null) {
            check(supports(contentResolver, existingUri, Document.FLAG_SUPPORTS_RENAME)) {
                "Provider cannot safely replace $fileName: rename is unsupported; original retained"
            }
        }
        val operationId = UUID.randomUUID().toString()
        val expected = sourceFile.inputStream().use(SyncFingerprint::digest)
        var staged: Uri? = null
        var backup: Uri? = null
        var published: Uri? = null
        var committed = false
        try {
            staged = create(contentResolver, parentUri, ".trime-replace-$operationId.tmp")
            writeAndVerify(contentResolver, sourceFile, staged, expected)
            val canRename = supports(contentResolver, staged, Document.FLAG_SUPPORTS_RENAME)
            check(existingUri == null || canRename) { "Provider cannot rename staged documents; original retained" }
            if (existingUri != null) {
                backup = rename(contentResolver, existingUri, ".trime-replace-$operationId.bak")
            }
            published = if (canRename) {
                rename(contentResolver, staged, fileName).also { staged = null }
            } else {
                create(contentResolver, parentUri, fileName)
            }
            requireDisplayName(contentResolver, published, fileName)
            if (!canRename) writeAndVerify(contentResolver, sourceFile, published, expected)
            committed = true
            cache.rememberFile(relativePath, DocumentsContract.getDocumentId(published), sourceFile.length())
        } catch (failure: Exception) {
            if (!committed) {
                var removed = true
                published?.let {
                    runCatching { check(DocumentsContract.deleteDocument(contentResolver, it)) }
                        .onFailure { error ->
                            removed = false
                            failure.addSuppressed(error)
                        }
                }
                backup?.let {
                    if (removed) {
                        runCatching {
                            val restored = rename(contentResolver, it, fileName)
                            requireDisplayName(contentResolver, restored, fileName)
                            cache.rememberFile(relativePath, DocumentsContract.getDocumentId(restored), existing?.size)
                            backup = null
                        }.exceptionOrNull()?.let(failure::addSuppressed)
                    }
                }
            }
            throw failure
        } finally {
            staged?.let { runCatching { DocumentsContract.deleteDocument(contentResolver, it) } }
            // Never discard recovery data after an unsuccessful replacement/rollback.
            if (committed) backup?.let { runCatching { DocumentsContract.deleteDocument(contentResolver, it) } }
        }
        return SyncFingerprint.hex(expected)
    }

    private fun create(cr: ContentResolver, parent: Uri, name: String): Uri = DocumentsContract.createDocument(cr, parent, "application/octet-stream", name)
        ?: error("Cannot create document: $name")

    private fun rename(cr: ContentResolver, uri: Uri, name: String): Uri = DocumentsContract.renameDocument(cr, uri, name) ?: error("Cannot rename $uri to $name")

    private fun supports(cr: ContentResolver, uri: Uri, flag: Int): Boolean = cr.query(uri, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use {
        check(it.moveToFirst()) { "Missing document: $uri" }
        it.getInt(0) and flag != 0
    } ?: error("Cannot query document: $uri")

    private fun requireDisplayName(cr: ContentResolver, uri: Uri, name: String) {
        cr.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            check(it.moveToFirst() && it.getString(0) == name) { "Provider changed requested name: $name" }
        } ?: error("Cannot query document: $uri")
    }

    private fun writeAndVerify(cr: ContentResolver, file: File, uri: Uri, expected: ByteArray) {
        val outputPfd = cr.openFileDescriptor(uri, "wt") ?: error("Cannot write $uri")
        ParcelFileDescriptor.AutoCloseOutputStream(outputPfd).use { output ->
            file.inputStream().use { it.copyTo(output) }
        }
        val inputPfd = cr.openFileDescriptor(uri, "r") ?: error("Cannot read $uri")
        val actual = ParcelFileDescriptor.AutoCloseInputStream(inputPfd).use { input ->
            SyncFingerprint.digest(input).also { if (inputPfd.canDetectErrors()) inputPfd.checkError() }
        }
        check(actual.contentEquals(expected)) { "Document read-back mismatch: $uri" }
    }
}
