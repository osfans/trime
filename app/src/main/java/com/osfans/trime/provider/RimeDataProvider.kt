// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.provider

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.osfans.trime.R
import com.osfans.trime.core.data.SyncRelativePath
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
            return try {
                val canonicalBase = base.canonicalFile
                if (!canonicalBase.isDirectory || !canonicalBase.canRead()) return null
                val canonicalParent = canonicalBase.parentFile ?: return null
                canonicalBase to "${canonicalParent.path}${File.separator}"
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

        /** Resolve an opaque document id while keeping it below the canonical provider root. */
        internal fun resolveDocument(
            root: File,
            docIdPrefix: String,
            documentId: String,
        ): File? {
            if (documentId.isEmpty() || File(documentId).isAbsolute) return null
            return try {
                val canonicalRoot = root.canonicalFile
                val canonicalPrefix = File(docIdPrefix).canonicalFile
                if (canonicalRoot.parentFile != canonicalPrefix) return null
                val requested = File(canonicalPrefix, documentId).absoluteFile
                requested.canonicalFile.takeIf {
                    it == requested && SyncRelativePath.isContained(canonicalRoot, it)
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }

    private data class RootState(
        val baseDir: File,
        val docIdPrefix: String,
        val textFilePaths: Set<String>,
    )

    private val rootStateLock = Any()

    @Volatile
    private var cachedRootState: RootState? = null

    private fun File.docId(root: RootState): String {
        val canonical = canonicalFileOrNull() ?: throw FileNotFoundException("Cannot resolve document path")
        if (!isContained(root.baseDir, canonical)) {
            throw FileNotFoundException("Document escapes provider root")
        }
        return canonical.path.removePrefix(root.docIdPrefix)
    }

    private fun fileFromDocId(
        docId: String,
        root: RootState,
    ): File = resolveDocument(root.baseDir, root.docIdPrefix, docId)
        ?: throw FileNotFoundException("Invalid document id")

    private fun resolveRootState(): RootState? = synchronized(rootStateLock) {
        val resolved = try {
            resolveDocumentsRoot(context?.getExternalFilesDir(null))
        } catch (_: SecurityException) {
            null
        }
        if (resolved == null) {
            cachedRootState = null
            return@synchronized null
        }
        val (base, prefix) = resolved
        cachedRootState?.takeIf { it.baseDir == base && it.docIdPrefix == prefix }?.let {
            return@synchronized it
        }
        RootState(
            baseDir = base,
            docIdPrefix = prefix,
            textFilePaths = TEXT_FILES.mapNotNullTo(mutableSetOf()) { base.resolve(it).canonicalFileOrNull()?.path },
        ).also { cachedRootState = it }
    }

    private fun requireRootState(): RootState = resolveRootState() ?: throw FileNotFoundException("App files dir is not available")

    private fun File.canonicalFileOrNull(): File? = try {
        canonicalFile
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun isContained(
        root: File,
        file: File,
    ): Boolean = try {
        SyncRelativePath.isContained(root, file)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun isDirectPath(
        root: File,
        file: File,
    ): Boolean = try {
        SyncRelativePath.isDirectPath(root, file)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val root = resolveRootState() ?: return cursor
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, root.baseDir.docId(root))
            add(
                Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_CREATE or Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_IS_CHILD,
            )
            add(Root.COLUMN_ICON, R.mipmap.ic_app_icon)
            add(Root.COLUMN_TITLE, context!!.getString(R.string.trime_app_name))
            add(Root.COLUMN_DOCUMENT_ID, root.baseDir.docId(root))
            add(Root.COLUMN_MIME_TYPES, MIME_TYPE_WILDCARD)
        }
        return cursor
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ) = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
        val root = requireRootState()
        newRowFromFile(fileFromDocId(documentId, root), root)
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ) = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
        val root = requireRootState()
        val children = fileFromDocId(parentDocumentId, root).listFiles()
            ?: throw FileNotFoundException("Cannot list $parentDocumentId")
        children.filter { isDirectPath(root.baseDir, it) }.forEach {
            newRowFromFile(it, root)
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val root = requireRootState()
        return ParcelFileDescriptor.open(
            fileFromDocId(documentId, root),
            ParcelFileDescriptor.parseMode(mode),
        )
    }

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor {
        val root = requireRootState()
        val file = fileFromDocId(documentId, root)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val root = requireRootState()
        val newFile = createAbstractFile(parentDocumentId, displayName, root)
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
        return newFile.docId(root)
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        val root = requireRootState()
        fileFromDocId(documentId, root).apply {
            check(this != root.baseDir) { "Cannot delete provider root" }
            requireContainedTree(this, root)
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

    override fun getDocumentType(documentId: String): String {
        val root = requireRootState()
        return fileFromDocId(documentId, root).mimeType(root)
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean = runCatching {
        val root = requireRootState()
        val parent = fileFromDocId(parentDocumentId, root)
        val child = fileFromDocId(documentId, root)
        parent != child && isContained(parent, child)
    }.getOrDefault(false)

    @Throws(FileNotFoundException::class)
    override fun copyDocument(
        sourceDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val root = requireRootState()
        val oldFile = fileFromDocId(sourceDocumentId, root)
        requireContainedTree(oldFile, root)
        val newFile = createAbstractFile(targetParentDocumentId, oldFile.name, root)
        check(!isContained(oldFile, newFile)) { "Cannot copy into the source tree" }
        oldFile.apply {
            try {
                val ok =
                    if (isDirectory) {
                        copyRecursively(newFile)
                    } else {
                        copyTo(newFile).exists()
                    }
                if (!ok) {
                    throw FileNotFoundException("copyDocument id=$sourceDocumentId to ${newFile.docId(root)} failed")
                }
            } catch (e: Exception) {
                throw FileNotFoundException("copyDocument id=$sourceDocumentId to ${newFile.docId(root)} failed: ${e.message}")
            }
        }
        return newFile.docId(root)
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        val root = requireRootState()
        val oldFile = fileFromDocId(documentId, root)
        check(oldFile != root.baseDir) { "Cannot rename provider root" }
        SyncRelativePath.requireSafeName(displayName)
        val newFile = oldFile.resolveSibling(displayName)
        if (newFile.exists()) {
            throw FileNotFoundException("renameDocument id=$documentId to $displayName failed: target exists")
        }
        if (!oldFile.renameTo(newFile)) throw FileNotFoundException("Cannot rename $documentId")
        return newFile.docId(root)
    }

    @Throws(FileNotFoundException::class)
    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val root = requireRootState()
        val oldFile = fileFromDocId(sourceDocumentId, root)
        check(oldFile != root.baseDir) { "Cannot move provider root" }
        check(oldFile.parentFile == fileFromDocId(sourceParentDocumentId, root)) { "Incorrect source parent" }
        val newFile = createAbstractFile(targetParentDocumentId, oldFile.name, root)
        if (!oldFile.renameTo(newFile)) throw FileNotFoundException("Cannot move $sourceDocumentId")
        return newFile.docId(root)
    }

    @Throws(FileNotFoundException::class)
    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<String>?,
    ) = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
        val root = requireRootState()
        val q = query.lowercase()
        val visited = mutableSetOf<String>()
        fileFromDocId(rootId, root)
            .walk()
            .onEnter { file ->
                file.canonicalFileOrNull()?.takeIf {
                    isDirectPath(root.baseDir, file) && isContained(root.baseDir, it)
                }?.path?.let(visited::add) == true
            }
            .filter { isDirectPath(root.baseDir, it) }
            .filter { it.name.lowercase().contains(q) }
            .take(SEARCH_RESULTS_LIMIT)
            .forEach { newRowFromFile(it, root) }
    }

    private fun File.mimeType(root: RootState): String = when {
        isDirectory -> Document.MIME_TYPE_DIR
        TEXT_EXTENSIONS.contains(extension) -> MIME_TYPE_TEXT
        root.textFilePaths.contains(absolutePath) -> MIME_TYPE_TEXT
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: MIME_TYPE_BIN
    }

    private fun requireContainedTree(
        treeRoot: File,
        root: RootState,
    ) {
        val visited = mutableSetOf<String>()
        treeRoot.walkTopDown().onEnter {
            val canonicalPath = it.canonicalFileOrNull()?.path
            check(isDirectPath(root.baseDir, it) && canonicalPath != null && visited.add(canonicalPath)) {
                "Unsafe or recursive document tree"
            }
            true
        }.forEach {
            check(isDirectPath(root.baseDir, it)) { "Document escapes provider root" }
        }
    }

    private fun createAbstractFile(
        parentDocumentId: String,
        displayName: String,
        root: RootState,
    ): File {
        SyncRelativePath.requireSafeName(displayName)
        val parent = fileFromDocId(parentDocumentId, root)
        check(parent.isDirectory) { "Not a directory: $parentDocumentId" }
        var newFile = parent.resolve(displayName)
        var noConflictId = 2
        while (newFile.exists()) {
            newFile = parent.resolve("$displayName ($noConflictId)")
            noConflictId += 1
        }
        return newFile
    }

    @Throws(FileNotFoundException::class)
    private fun MatrixCursor.newRowFromFile(
        file: File,
        root: RootState,
    ) {
        val safeFile = file.canonicalFileOrNull()?.takeIf {
            isDirectPath(root.baseDir, file) && isContained(root.baseDir, it)
        }
            ?: throw FileNotFoundException("Invalid file path")
        if (!safeFile.exists()) {
            throw FileNotFoundException("File(path=${safeFile.absolutePath}) not found")
        }

        val mimeType = safeFile.mimeType(root)
        var flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Document.FLAG_SUPPORTS_COPY else 0
        if (safeFile.canWrite()) {
            flags = flags or
                if (safeFile.isDirectory) {
                    Document.FLAG_DIR_SUPPORTS_CREATE
                } else {
                    Document.FLAG_SUPPORTS_WRITE
                }
        }
        if (safeFile != root.baseDir && safeFile.parentFile?.canWrite() == true) {
            flags = flags or
                Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                flags = flags or Document.FLAG_SUPPORTS_MOVE
            }
        }
        if (mimeType.startsWith("image/")) {
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }

        newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, safeFile.docId(root))
            add(Document.COLUMN_MIME_TYPE, mimeType)
            add(Document.COLUMN_DISPLAY_NAME, safeFile.name)
            add(Document.COLUMN_LAST_MODIFIED, safeFile.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, safeFile.length())
        }
    }
}
