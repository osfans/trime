/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

object StorageDocs {
    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    /**
     * Normalizes a URI to a *document* URI. A bare tree URI (as returned by
     * ACTION_OPEN_DOCUMENT_TREE, path `tree/<id>`) becomes its root document
     * URI; anything else is returned unchanged.
     */
    fun docUriOf(uri: Uri): Uri {
        val segments = uri.pathSegments
        return if (segments.size == 2 && segments[0] == "tree") {
            DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri),
            )
        } else {
            uri
        }
    }

    private fun Cursor.toSafDocumentFile(transform: (String) -> Uri): StorageDocument {
        val docId = getString(0)
        val mime = getString(2)
        val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
        return StorageDocument(
            uri = transform(docId),
            name = getString(1) ?: docId,
            isDir = isDir,
            length = if (isNull(3)) 0L else getLong(3),
            lastModified = if (isNull(4)) 0L else getLong(4),
            mimeType = if (isDir) null else mime,
        )
    }

    /** Metadata for [uri], or null when the document does not exist. */
    fun stat(context: Context, uri: Uri): StorageDocument? {
        val doc = docUriOf(uri)
        return try {
            context.contentResolver.query(doc, PROJECTION, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.toSafDocumentFile { uri } else null
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private fun <T> queryBlocking(block: suspend () -> T): T {
        var res: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                res = result
            }
        })
        return requireNotNull(res) {
            "This path actually suspended, call the suspend version instead"
        }.getOrThrow()
    }

    internal suspend fun queryChildren(context: Context, dirUri: Uri, visit: suspend (StorageDocument) -> Boolean) {
        val dirDoc = docUriOf(dirUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            dirDoc,
            DocumentsContract.getDocumentId(dirDoc),
        )
        val cursor = context.contentResolver.query(childrenUri, PROJECTION, null, null, null)
            ?: throw StorageNotFoundException("Cannot list $dirUri")
        cursor.use { c ->
            while (c.moveToNext()) {
                val doc = c.toSafDocumentFile { id -> DocumentsContract.buildDocumentUriUsingTree(dirDoc, id) }
                if (visit(doc)) return
            }
        }
    }

    internal fun queryChildrenBlocking(context: Context, dirUri: Uri, visit: (StorageDocument) -> Boolean) = queryBlocking { queryChildren(context, dirUri, visit) }

    /** Children of [dirUri] with full metadata from one cursor. */
    fun listChildren(context: Context, dirUri: Uri): List<StorageDocument> = buildList {
        queryChildrenBlocking(context, dirUri) {
            add(it)
        }
    }

    /** Resolves a descendant by name segments, or null if any segment is missing. */
    fun child(context: Context, dirUri: Uri, vararg names: String): StorageDocument? {
        var current = stat(context, dirUri) ?: return null
        for (name in names) {
            var found: StorageDocument? = null
            queryChildrenBlocking(context, current.uri) {
                if (it.name == name) {
                    found = it
                    true
                } else {
                    false
                }
            }
            current = found ?: return null
        }
        return current
    }

    /** Creates directory path [names] under [dirUri], returning the deepest dir. */
    fun mkdirp(context: Context, dirUri: Uri, vararg names: String): StorageDocument {
        var currentUri = docUriOf(dirUri)
        for (name in names) {
            val existing = child(context, currentUri, name)
            currentUri = if (existing != null) {
                if (!existing.isDir) {
                    throw StorageAlreadyExistsException("'$name' exists and is not a directory")
                }
                existing.uri
            } else {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    currentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name,
                ) ?: throw Exception("Failed to create directory '$name'")
            }
        }
        return stat(context, currentUri) ?: throw StorageNotFoundException("mkdirp result missing")
    }

    /** Creates a new (possibly auto-renamed) document in [dirUri]. */
    fun createFile(context: Context, dirUri: Uri, mime: String, name: String): Uri = DocumentsContract.createDocument(context.contentResolver, docUriOf(dirUri), mime, name)
        ?: throw Exception("Failed to create '$name' in $dirUri")

    /** Deletes a document (recursively for directories, per provider). */
    fun delete(context: Context, uri: Uri) {
        val ok = DocumentsContract.deleteDocument(context.contentResolver, docUriOf(uri))
        if (!ok) throw Exception("Failed to delete $uri")
    }

    /** Renames a document, returning its (possibly new) URI. */
    fun rename(context: Context, uri: Uri, newName: String): Uri {
        val doc = docUriOf(uri)
        // renameDocument returns the original URI when the rename succeeded without
        // changing it, and null only on failure — so null must not be mapped to doc.
        return DocumentsContract.renameDocument(context.contentResolver, doc, newName)
            ?: throw Exception("Failed to rename $uri to $newName")
    }

    /**
     * Copies raw contents from [src] into [dest].
     */
    fun copyContents(
        context: Context,
        src: Uri,
        dest: Uri,
    ) {
        val input = context.contentResolver.openInputStream(src)
            ?: throw StorageNotFoundException("Cannot open input $src")
        input.use { ins ->
            val output = context.contentResolver.openOutputStream(dest, "wt")
                ?: throw Exception("Cannot open output $dest")
            output.use { outs ->
                ins.copyTo(outs)
                outs.flush()
            }
        }
    }
}
