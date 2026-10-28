// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.provider

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.osfans.trime.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class RimeDataProvider : DocumentsProvider() {
    companion object {
        private const val MIME_TYPE_WILDCARD = "*/*"
        private const val MIME_TYPE_TEXT = "text/plain"
        private const val MIME_TYPE_BIN = "application/octet-stream"

        private val TEXT_EXTENSIONS =
            arrayOf(
                "lua",
                "yml",
                "yaml",
            )

        // path relative to baseDir that should be recognize as text files
        private val TEXT_FILES = emptyArray<String>()

        // The default columns to return information about a root if no specific
        // columns are requested in a query.
        private val DEFAULT_ROOT_PROJECTION =
            arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_FLAGS,
                Root.COLUMN_ICON,
                Root.COLUMN_TITLE,
                Root.COLUMN_DOCUMENT_ID,
                Root.COLUMN_MIME_TYPES,
            )

        // The default columns to return information about a document if no specific
        // columns are requested in a query.
        private val DEFAULT_DOCUMENT_PROJECTION =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_FLAGS,
                Document.COLUMN_SIZE,
            )

        private const val SEARCH_RESULTS_LIMIT = 50

        /**
         * Resolve the documents root from [externalFilesDir].
         *
         * Returns null when the external files dir is not ready yet (for example early after reboot).
         */
        internal fun resolveDocumentsRoot(externalFilesDir: File?): Pair<File, String>? {
            val base = externalFilesDir ?: return null
            val parentPath = base.parent ?: return null
            return base to "$parentPath${File.separator}"
        }
    }

    private var baseDir: File? = null
    private var docIdPrefix: String? = null
    private var textFilePaths: Array<String> = emptyArray()

    private val File.docId
        get() = absolutePath.removePrefix(docIdPrefix())

    private fun docIdPrefix(): String {
        if (!ensureBaseDir()) {
            throw FileNotFoundException("App files dir is not available")
        }
        return docIdPrefix!!
    }

    private fun fileFromDocId(docId: String) = File(docIdPrefix(), docId)

    private fun ensureBaseDir(): Boolean {
        if (baseDir != null && docIdPrefix != null) return true
        val (base, prefix) =
            resolveDocumentsRoot(context!!.getExternalFilesDir(null)) ?: return false
        baseDir = base
        docIdPrefix = prefix
        textFilePaths = Array(TEXT_FILES.size) { base.resolve(TEXT_FILES[it]).absolutePath }
        return true
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        if (!ensureBaseDir()) return cursor
        val root = baseDir!!
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, root.docId)
            add(
                Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_CREATE or Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD,
            )
            add(Root.COLUMN_ICON, R.mipmap.ic_app_icon)
            add(Root.COLUMN_TITLE, context!!.getString(R.string.trime_app_name))
            add(Root.COLUMN_DOCUMENT_ID, root.docId)
            add(Root.COLUMN_MIME_TYPES, MIME_TYPE_WILDCARD)
        }
        return cursor
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ) = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
        newRowFromFile(fileFromDocId(documentId))
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ) = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
        fileFromDocId(parentDocumentId).listFiles()?.forEach {
            newRowFromFile(it)
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(
        fileFromDocId(documentId),
        ParcelFileDescriptor.parseMode(mode),
    )

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        val file = fileFromDocId(documentId)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val newFile = createAbstractFile(parentDocumentId, displayName)
        try {
            val ok =
                if (mimeType == Document.MIME_TYPE_DIR) {
                    newFile.mkdir()
                } else {
                    newFile.createNewFile()
                }
            if (!ok) {
                throw FileNotFoundException("createDocument id=${newFile.path} failed")
            }
        } catch (e: IOException) {
            throw FileNotFoundException("createDocument id=${newFile.path} failed: ${e.message}")
        }
        return newFile.docId
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        fileFromDocId(documentId).apply {
            val ok =
                if (isDirectory) {
                    deleteRecursively()
                } else {
                    delete()
                }
            if (!ok) {
                throw FileNotFoundException("deleteDocument id=$documentId failed")
            }
        }
    }

    override fun getDocumentType(documentId: String): String = fileFromDocId(documentId).mimeType

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean = documentId.startsWith(parentDocumentId)

    @Throws(FileNotFoundException::class)
    override fun copyDocument(
        sourceDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val oldFile = fileFromDocId(sourceDocumentId)
        val newFile = createAbstractFile(targetParentDocumentId, oldFile.name)
        oldFile.apply {
            try {
                val ok =
                    if (isDirectory) {
                        copyRecursively(newFile)
                    } else {
                        copyTo(newFile).exists()
                    }
                if (!ok) {
                    throw FileNotFoundException("copyDocument id=$sourceDocumentId to ${newFile.docId} failed")
                }
            } catch (e: Exception) {
                throw FileNotFoundException("copyDocument id=$sourceDocumentId to ${newFile.docId} failed: ${e.message}")
            }
        }
        return newFile.docId
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        val oldFile = fileFromDocId(documentId)
        val newFile = oldFile.resolveSibling(displayName)
        if (newFile.exists()) {
            throw FileNotFoundException("renameDocument id=$documentId to $displayName failed: target exists")
        }
        oldFile.renameTo(newFile)
        return newFile.docId
    }

    @Throws(FileNotFoundException::class)
    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val oldFile = fileFromDocId(sourceDocumentId)
        val newFile = createAbstractFile(targetParentDocumentId, oldFile.name)
        oldFile.renameTo(newFile)
        return newFile.docId
    }

    @Throws(FileNotFoundException::class)
    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<String>?,
    ) = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
        val q = query.lowercase()
        fileFromDocId(rootId)
            .walk()
            .filter { it.name.lowercase().contains(q) }
            .take(SEARCH_RESULTS_LIMIT)
            .forEach { newRowFromFile(it) }
    }

    private val File.mimeType: String
        get() =
            when {
                isDirectory -> Document.MIME_TYPE_DIR
                TEXT_EXTENSIONS.contains(extension) -> MIME_TYPE_TEXT
                textFilePaths.contains(absolutePath) -> MIME_TYPE_TEXT
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: MIME_TYPE_BIN
            }

    private fun createAbstractFile(
        parentDocumentId: String,
        displayName: String,
    ): File {
        val parent = fileFromDocId(parentDocumentId)
        var newFile = parent.resolve(displayName)
        var noConflictId = 2
        while (newFile.exists()) {
            newFile = parent.resolve("$displayName ($noConflictId)")
            noConflictId += 1
        }
        return newFile
    }

    @Throws(FileNotFoundException::class)
    private fun MatrixCursor.newRowFromFile(file: File) {
        if (!file.exists()) {
            throw FileNotFoundException("File(path=${file.absolutePath}) not found")
        }

        val mimeType = file.mimeType
        var flags = Document.FLAG_SUPPORTS_COPY
        if (file.canWrite()) {
            flags = flags or
                if (file.isDirectory) {
                    Document.FLAG_DIR_SUPPORTS_CREATE
                } else {
                    Document.FLAG_SUPPORTS_WRITE
                }
        }
        if (file.parentFile?.canWrite() == true) {
            flags = flags or
                Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME
            flags = flags or Document.FLAG_SUPPORTS_MOVE
        }
        if (mimeType.startsWith("image/")) {
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }

        newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, file.docId)
            add(Document.COLUMN_MIME_TYPE, mimeType)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, file.length())
        }
    }
}
