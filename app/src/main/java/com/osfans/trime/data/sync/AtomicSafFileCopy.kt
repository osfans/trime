// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AtomicSafFileCopy {
    /**
     * Atomically replaces [fileName] under [parentDir] with [sourceFile].
     *
     * Sequence: write `$fileName.tmp` and verify length; rename existing target to
     * `$fileName.bak` (never delete first); rename temp to [fileName]; delete backup
     * only after that succeeds. If replacement fails after the backup rename, restore
     * the backup to [fileName] before rethrowing.
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
        val tempName = "$fileName.tmp"
        val backupName = "$fileName.bak"
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
                backupUri =
                    DocumentsContract.renameDocument(contentResolver, targetUri, backupName)
                        ?: error("Failed to rename existing document to backup")
            }

            val finalUri = replaceTempWithFinalName(contentResolver, parentUri, tempUri, fileName)
            backupUri?.let { DocumentsContract.deleteDocument(contentResolver, it) }
            cache.rememberFile(relativePath, DocumentsContract.getDocumentId(finalUri), expectedBytes)
        } catch (e: Exception) {
            backupUri?.let { backup ->
                DocumentsContract.renameDocument(contentResolver, backup, fileName)
            }
            runCatching { DocumentsContract.deleteDocument(contentResolver, tempUri) }
            throw e
        }
    }

    private fun replaceTempWithFinalName(
        contentResolver: ContentResolver,
        parentUri: Uri,
        tempUri: Uri,
        fileName: String,
    ): Uri =
        DocumentsContract.renameDocument(contentResolver, tempUri, fileName)
            ?: run {
                val created =
                    DocumentsContract.createDocument(
                        contentResolver,
                        parentUri,
                        "application/octet-stream",
                        fileName,
                    ) ?: error("Failed to create document $fileName")
                copyDocument(contentResolver, tempUri, created)
                DocumentsContract.deleteDocument(contentResolver, tempUri)
                created
            }

    private fun documentLength(
        contentResolver: ContentResolver,
        uri: Uri,
    ): Long =
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
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
