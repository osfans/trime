/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.storage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import com.osfans.trime.util.appContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class StorageAccess(caller: ActivityResultCaller) {

    private val mutex = Mutex()

    private var directoryContinuation: CancellableContinuation<StorageDocument?>? = null

    private val directoryLauncher =
        caller.registerForActivityResult(StorageResultContracts.OpenDirectory()) { result ->
            directoryContinuation?.resumeWith(result)
            directoryContinuation = null
        }

    /** Opens the system directory picker (`ACTION_OPEN_DOCUMENT_TREE`).
     *
     * Returns the picked directory, or `null` if the user cancelled.
     * With [persistablePermission] (default) the grant survives app restarts;
     * check [persistedPermissions] on later launches instead of re-prompting. */
    suspend fun pickDirectory(
        initialUri: Uri? = null,
        writePermission: Boolean = true,
        persistablePermission: Boolean = true,
    ): StorageDocument? = mutex.withLock {
        val args = StorageResultContracts.OpenDirectory.Args(initialUri, writePermission, persistablePermission)
        try {
            awaitResult(directoryLauncher, args) {
                directoryContinuation = it
            }
        } catch (_: ActivityNotFoundException) {
            null
        }
    }

    private suspend fun <I, O> awaitResult(
        launcher: ActivityResultLauncher<I>,
        input: I,
        store: (CancellableContinuation<O>?) -> Unit,
    ): O = suspendCancellableCoroutine { continuation ->
        store(continuation)
        continuation.invokeOnCancellation { store(null) }
        try {
            launcher.launch(input)
        } catch (e: Exception) {
            store(null)
            throw e
        }
    }

    companion object {
        /** Lists all URI permissions the app currently persists. */
        fun persistedPermissions(): List<StoragePersistedPermission> = appContext.contentResolver.persistedUriPermissions
            .map {
                StoragePersistedPermission(
                    uri = it.uri,
                    read = it.isReadPermission,
                    write = it.isWritePermission,
                    persistedTime = it.persistedTime,
                )
            }

        /** Releases a persisted URI permission previously taken by a picker. */
        fun releasePersistedPermission(uri: Uri) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val segments = uri.pathSegments
            val target = if (segments.size == 4 && segments[0] == "tree" &&
                segments[2] == "document" && segments[3] == segments[1]
            ) {
                DocumentsContract.buildTreeDocumentUri(uri.authority, segments[1])
            } else {
                uri
            }
            try {
                appContext.contentResolver.releasePersistableUriPermission(target, flags)
            } catch (_: SecurityException) {
            }
        }

        /** Lists the children of [dirUri] with full metadata in a single query. */
        suspend fun list(dirUri: Uri): List<StorageDocument> = withContext(Dispatchers.IO) {
            StorageDocs.listChildren(appContext, dirUri)
        }

        /** Returns metadata for [uri], or `null` if no document exists there. */
        suspend fun stat(uri: Uri): StorageDocument? = withContext(Dispatchers.IO) {
            StorageDocs.stat(appContext, uri)
        }

        /** Resolves a descendant by name segments, e.g.
         * `child(dir.uri, "backups", "config.json")`. Returns `null` if any
         * segment is missing. */
        suspend fun child(dirUri: Uri, vararg names: String): StorageDocument? = withContext(Dispatchers.IO) {
            StorageDocs.child(appContext, dirUri, *names)
        }

        /** Creates the directory path [names] under [dirUri], creating
         * intermediate directories as needed. Returns the deepest directory. */
        suspend fun mkdirp(dirUri: Uri, vararg names: String): StorageDocument = withContext(Dispatchers.IO) {
            StorageDocs.mkdirp(appContext, dirUri, *names)
        }

        /** Deletes the document at [uri]. Directories are deleted recursively. */
        suspend fun delete(uri: Uri) = withContext(Dispatchers.IO) {
            StorageDocs.delete(appContext, uri)
        }

        /** Renames the document at [uri] to [newName] and returns it. */
        suspend fun rename(uri: Uri, newName: String): StorageDocument = withContext(Dispatchers.IO) {
            val renamed = StorageDocs.rename(appContext, uri, newName)
            StorageDocs.stat(appContext, renamed) ?: throw StorageNotFoundException("Renamed document missing")
        }

        /** Copies a file or directory (recursively) into [destDirUri]. */
        suspend fun copyTo(uri: Uri, destDirUri: Uri): StorageDocument = withContext(Dispatchers.IO) {
            copyOrMove(uri, destDirUri, move = false)
        }

        /** Moves a file or directory (recursively) into [destDirUri].
         * Implemented as copy-then-delete for provider compatibility. */
        suspend fun moveTo(uri: Uri, destDirUri: Uri): StorageDocument = withContext(Dispatchers.IO) {
            copyOrMove(uri, destDirUri, move = true)
        }

        /** Recursively walks [dirUri] depth-first, emits every descendant reachable within [maxDepth],
         * [descend] may reject a directory, pruning its whole subtree.
         *
         * Directories are emitted before their contents. */
        fun walk(
            dirUri: Uri,
            maxDepth: Int = Int.MAX_VALUE,
            descend: (StorageWalkEntry, Int) -> Boolean = { _, _ -> true },
        ): Flow<StorageWalkEntry> = flow {
            val stack = ArrayDeque<Triple<Uri, String, Int>>()
            stack.addLast(Triple(dirUri, "", 0))
            while (stack.isNotEmpty()) {
                val (dir, prefix, depth) = stack.removeLast()
                StorageDocs.queryChildren(appContext, dir) { child ->
                    val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                    val entry = StorageWalkEntry(child, rel)
                    emit(entry)
                    if (child.isDir && depth + 1 < maxDepth && descend(entry, depth + 1)) {
                        stack.addLast(Triple(child.uri, rel, depth + 1))
                    }
                    false
                }
            }
        }.flowOn(Dispatchers.IO)

        /** Reads a file with it's InputStream */
        suspend fun <R> readFile(
            uri: Uri,
            start: Long = 0,
            block: suspend (InputStream) -> R,
        ): R {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw StorageNotFoundException("Cannot open $uri")
            input.use {
                skipFully(input, start)
                return block(it)
            }
        }

        /** Reads a file's bytes. Use [start]/[count] to read a range. */
        suspend fun readFileBytes(
            uri: Uri,
            start: Long = 0L,
            count: Int? = null,
        ): ByteArray = withContext(Dispatchers.IO) {
            readFile(uri, start) { ins ->
                if (count == null) {
                    ins.readBytes()
                } else {
                    val buf = ByteArray(count)
                    var off = 0
                    while (off < count) {
                        val n = ins.read(buf, off, count - off)
                        if (n < 0) break
                        off += n
                    }
                    buf.copyOf(off)
                }
            }
        }

        /** Streams a file's bytes in chunks of [bufferSize] (default 8 MiB). */
        fun readFileStream(
            uri: Uri,
            start: Long = 0L,
            bufferSize: Int = DEFAULT_BUFFER_SIZE,
        ): Flow<ByteArray> = flow {
            readFile(uri, start) { ins ->
                val buf = ByteArray(bufferSize)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    emit(if (n == buf.size) buf.copyOf() else buf.copyOf(n))
                }
            }
        }.flowOn(Dispatchers.IO)

        /** Writes a file named [name] inside [dirUri] with OutputStream */
        suspend fun writeFile(
            dirUri: Uri,
            name: String,
            mime: String,
            overwrite: Boolean = false,
            append: Boolean = false,
            block: suspend (OutputStream) -> Unit,
        ): StorageDocument {
            val (target, _) = resolveWriteTarget(dirUri, name, mime, overwrite, append)
            val mode = if (append) "wa" else "wt"
            val out = appContext.contentResolver.openOutputStream(target, mode)
                ?: throw Exception("Cannot open output $target")
            truncateForOverwrite(out, append)
            out.use { block(it) }
            return StorageDocs.stat(appContext, target) ?: throw StorageNotFoundException("Written document missing")
        }

        /** Writes [data] as a file named [name] inside [dirUri].
         *
         * By default a name collision creates an auto-renamed file
         * (SAF behavior, e.g. `file (1).txt`); pass `overwrite = true` to truncate
         * the existing document, or `append = true` to append to it. */
        suspend fun writeFileBytes(
            dirUri: Uri,
            name: String,
            mime: String,
            data: ByteArray,
            overwrite: Boolean = false,
            append: Boolean = false,
        ): StorageDocument = withContext(Dispatchers.IO) {
            writeFile(dirUri, name, mime, overwrite, append) { it.write(data) }
        }

        /** Writes a whole [source] stream as a file in one call */
        suspend fun writeFileStream(
            dirUri: Uri,
            name: String,
            mime: String,
            source: Flow<ByteArray>,
            overwrite: Boolean = false,
            append: Boolean = false,
        ): StorageDocument = withContext(Dispatchers.IO) {
            writeFile(dirUri, name, mime, overwrite, append) {
                source.collect { data ->
                    it.write(data)
                }
            }
        }

        /** Copies a SAF document to a local filesystem path (e.g. app cache), so
         * the file can be handed to APIs that need a real path. */
        suspend fun copyToLocalFile(srcUri: Uri, destPath: String) = withContext(Dispatchers.IO) {
            val input = appContext.contentResolver.openInputStream(srcUri)
                ?: throw StorageNotFoundException("Cannot open $srcUri")
            input.use { ins ->
                FileOutputStream(destPath).use { outs ->
                    ins.copyTo(outs)
                }
            }
        }

        /** Copies a local file into a SAF directory. */
        suspend fun pasteLocalFile(
            srcPath: String,
            destDirUri: Uri,
            name: String,
            mime: String,
            overwrite: Boolean = false,
        ): StorageDocument = withContext(Dispatchers.IO) {
            val src = File(srcPath)
            if (!src.exists()) throw StorageNotFoundException("No local file $srcPath")
            FileInputStream(src).use { ins ->
                writeFile(destDirUri, name, mime, overwrite, append = false) { outs ->
                    ins.copyTo(outs)
                    outs.flush()
                }
            }
        }

        /** Copies the files directly inside [dirUri] into the local directory
         * [destDirPath] (which must already exist — e.g. your app's
         * `getExternalStorageDirectory()`), returning the local paths written.
         *
         * Restores the classic "cache a granted folder into my app" workflow —
         * e.g. pulling WhatsApp `.Statuses` out so they can be saved or shared.
         * Only top-level files are copied (sub-directories are skipped). Built on
         * [list] + [copyToLocalFile]. */
        suspend fun copyDirToLocal(dirUri: Uri, destDirPath: String): List<String> = withContext(Dispatchers.IO) {
            buildList {
                for (f in list(dirUri)) {
                    if (f.isDir) continue
                    val dest = "$destDirPath/${f.name}"
                    copyToLocalFile(f.uri, dest)
                    add(dest)
                }
            }
        }

        /** Opens a native file descriptor for [uri] in [mode] (one of `r`, `w`,
         * `rw`, `wt`), returning its raw [StorageFileDescriptor.pfd] and `/proc/self/fd/<pfd.fd>`
         * path for handing to path-based/native APIs. */
        suspend fun fileDescriptor(uri: Uri, mode: String): StorageFileDescriptor = withContext(Dispatchers.IO) {
            val pfd = appContext.contentResolver.openFileDescriptor(uri, mode)
                ?: throw StorageNotFoundException("Cannot open file descriptor for $uri")
            StorageFileDescriptor(pfd, "/proc/self/fd/${pfd.fd}")
        }

        /** Best-effort truncate so overwriting longer content with shorter content
         *  leaves no stale trailing bytes on providers that ignore the "wt" flag. */
        private fun truncateForOverwrite(out: OutputStream, append: Boolean) {
            if (!append) runCatching { (out as? FileOutputStream)?.channel?.truncate(0L) }
        }

        /** Existing-child + overwrite/append/auto-rename resolution for writes.
         *  Returns the target URI and whether it was newly created (vs pre-existing). */
        private fun resolveWriteTarget(
            dirUri: Uri,
            name: String,
            mime: String,
            overwrite: Boolean,
            append: Boolean,
        ): Pair<Uri, Boolean> {
            val existing = StorageDocs.child(appContext, dirUri, name)
            return when {
                existing != null && (overwrite || append) -> existing.uri to false
                else -> StorageDocs.createFile(appContext, dirUri, mime, name) to true
            }
        }

        private fun copyOrMove(uri: Uri, destDirUri: Uri, move: Boolean): StorageDocument {
            val src = StorageDocs.stat(appContext, uri) ?: throw StorageNotFoundException("Source missing: $uri")
            val isDir = src.isDir
            if (isDir) {
                // Copying a directory into itself (or its own subtree) would recurse
                // into the freshly created copy and never terminate.
                val srcDoc = StorageDocs.docUriOf(uri)
                val destDoc = StorageDocs.docUriOf(destDirUri)
                if (srcDoc.authority == destDoc.authority) {
                    val srcId = DocumentsContract.getDocumentId(srcDoc)
                    val destId = DocumentsContract.getDocumentId(destDoc)
                    val srcPrefix =
                        if (srcId.endsWith(":") || srcId.endsWith("/")) srcId else "$srcId/"
                    if (destId == srcId || destId.startsWith(srcPrefix)) {
                        throw Exception("Cannot copy or move a directory into itself or its own subtree")
                    }
                }
            }
            val base = longArrayOf(0L)
            val copied = copyRecursive(src, destDirUri, base, isDir)
            if (move) StorageDocs.delete(appContext, uri)
            return copied
        }

        /**
         * Copies [src] (file or directory) into [destDir]; returns the map of the
         * created root document. Progress is reported as [base]+perFileBytes; for
         * directory copies [reportTotalNull] is true (grand total is unknown).
         */
        private fun copyRecursive(
            src: StorageDocument,
            destDir: Uri,
            base: LongArray,
            reportTotalNull: Boolean,
        ): StorageDocument {
            val name = src.name
            return if (src.isDir) {
                val newDir = StorageDocs.mkdirp(appContext, destDir, name)
                StorageDocs.queryChildrenBlocking(appContext, src.uri) { child ->
                    copyRecursive(child, newDir.uri, base, reportTotalNull)
                    false
                }
                newDir
            } else {
                val mime = src.mimeType ?: "application/octet-stream"
                val target = StorageDocs.createFile(appContext, destDir, mime, name)
                val total = src.length
                StorageDocs.copyContents(appContext, src.uri, target)
                base[0] += total
                StorageDocs.stat(appContext, target) ?: throw StorageNotFoundException("Copied document missing")
            }
        }

        private fun skipFully(input: InputStream, count: Long) {
            var remaining = count
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
        }
    }
}
