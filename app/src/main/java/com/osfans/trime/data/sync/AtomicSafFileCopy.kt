// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

object AtomicSafFileCopy {
    /**
     * Atomically replaces [fileName] under [parentDir] with [sourceFile].
     *
     * Uses operation-scoped `.trime-replace-<uuid>.tmp` / `.bak` artifacts so user
     * files such as `user.yaml.bak` are never deleted. If replacement fails after
     * the backup rename, restore the backup to [fileName] before rethrowing.
     */
    fun copyFromFile(
        contentResolver: ContentResolver,
        treeUri: Uri,
        cache: SafPathCache,
        sourceFile: File,
        relativePath: String,
        parentDir: String,
        fileName: String,
    ) {
        val operationId = UUID.randomUUID().toString()
        val tempName = ".trime-replace-$operationId.tmp"
        val backupName = ".trime-replace-$operationId.bak"
        val expectedBytes = sourceFile.length()
        val parentId = cache.ensureDirectory(contentResolver, treeUri, parentDir)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)

        deleteChildIfExists(contentResolver, treeUri, parentId, tempName)
        deleteChildIfExists(contentResolver, treeUri, parentId, backupName)
        val tempUri =
            DocumentsContract.createDocument(
                contentResolver,
                parentUri,
                "application/octet-stream",
                tempName,
            ) ?: error("Failed to create temp document $tempName")

        contentResolver.openFileDescriptor(tempUri, "wt")?.use { pfd ->
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
        } ?: error("Cannot open $tempUri for writing")

        val tempLength = documentLength(contentResolver, tempUri)
        if (tempLength != expectedBytes) {
            DocumentsContract.deleteDocument(contentResolver, tempUri)
            error("Temp document size mismatch: expected $expectedBytes, got $tempLength")
        }

        var backupUri: Uri? = null
        try {
            val existingTargetId =
                SafTreeWalker.findChildDocumentId(contentResolver, treeUri, parentId, fileName)
            if (existingTargetId != null) {
                val targetUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingTargetId)
                backupUri = moveDocument(contentResolver, parentUri, targetUri, backupName)
            }

            val finalUri = moveDocument(contentResolver, parentUri, tempUri, fileName)
            backupUri?.let { DocumentsContract.deleteDocument(contentResolver, it) }
            cache.rememberFile(relativePath, DocumentsContract.getDocumentId(finalUri), expectedBytes)
        } catch (e: Exception) {
            backupUri?.let { backup ->
                runCatching {
                    moveDocument(contentResolver, parentUri, backup, fileName)
                }.onFailure { restoreError ->
                    Timber.e(restoreError, "Failed to restore $fileName from backup")
                }
            }
            runCatching { DocumentsContract.deleteDocument(contentResolver, tempUri) }
            throw e
        }
    }

    /**
     * Moves [sourceUri] under [parentUri] to [fileName], renaming when the
     * provider supports it and otherwise falling back to a create + copy +
     * delete sequence. On failure the created document is deleted before
     * rethrowing, so no partial document is left at [fileName].
     */
    private fun moveDocument(
        contentResolver: ContentResolver,
        parentUri: Uri,
        sourceUri: Uri,
        fileName: String,
    ): Uri = DocumentsContract.renameDocument(contentResolver, sourceUri, fileName)
        ?: run {
            // Providers may not implement renameDocument; fall back to a
            // create + copy + delete sequence.
            val created =
                DocumentsContract.createDocument(
                    contentResolver,
                    parentUri,
                    "application/octet-stream",
                    fileName,
                ) ?: error("Failed to create document $fileName")
            try {
                copyDocument(contentResolver, sourceUri, created)
                if (!DocumentsContract.deleteDocument(contentResolver, sourceUri)) {
                    error("Failed to delete source document $sourceUri")
                }
            } catch (e: Exception) {
                // Leave no partial document at [fileName] so that a backup can
                // be restored to this name cleanly.
                runCatching { DocumentsContract.deleteDocument(contentResolver, created) }
                throw e
            }
            created
        }

    private fun documentLength(
        contentResolver: ContentResolver,
        uri: Uri,
    ): Long = contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        pfd.statSize
    } ?: error("Cannot stat $uri")

    private fun deleteChildIfExists(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentId: String,
        fileName: String,
    ) {
        val existingId = SafTreeWalker.findChildDocumentId(contentResolver, treeUri, parentId, fileName) ?: return
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
        DocumentsContract.deleteDocument(contentResolver, docUri)
    }

    private fun copyDocument(
        contentResolver: ContentResolver,
        sourceUri: Uri,
        destUri: Uri,
    ) {
        contentResolver.openFileDescriptor(sourceUri, "r")?.use { sourcePfd ->
            contentResolver.openFileDescriptor(destUri, "wt")?.use { destPfd ->
                FileInputStream(sourcePfd.fileDescriptor).use { input ->
                    FileOutputStream(destPfd.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
            } ?: error("Cannot open $destUri for writing")
        } ?: error("Cannot open $sourceUri for reading")
    }
}
