/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.sync

import android.net.Uri
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.DeployNotification
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.appContext
import io.github.whiredplanck.storageaccess.StorageAccess
import io.github.whiredplanck.storageaccess.StorageWalkEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Mirrors the user data dir against the folder the user picked in [DataStorageMode.EXTERNAL_SYNC]:
 * files are imported before a rime maintenance and exported after a successful one.
 */
object RimeDataSync {
    private const val DEFAULT_MIME = "application/octet-stream"

    /** Internal rime directories that never take part in the sync. */
    private const val SKIP_DIR = "build"
    private const val SKIP_DIR_SUBSTRING = ".userdb"

    private val parallelism = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)

    private val prefs get() = AppPrefs.defaultInstance().profile

    // region Storage mode and picked folder

    fun usesExternalSync(): Boolean = prefs.dataStorageMode.getValue() == DataStorageMode.EXTERNAL_SYNC

    fun treeUri(): Uri? = prefs.externalRimeTreeUri.getValue().takeIf { it.isNotEmpty() }?.let(Uri::parse)

    fun hasExternalAccess(): Boolean {
        val uriString = treeUri()?.toString() ?: return false
        return StorageAccess.persistedPermissions().any {
            it.uri.toString() == uriString && it.read && it.write
        }
    }

    fun isStorageAvailable(): Boolean = !usesExternalSync() || hasExternalAccess()

    /**
     * Whether the user finished the storage-mode setup step.
     *
     * This does not require runtime dirs under `Context.getExternalFilesDir`
     * to be writable yet, so late media after reboot does not reopen the setup wizard.
     */
    internal fun isStorageChoiceComplete(
        mode: DataStorageMode,
        treeUri: String,
    ): Boolean = when (mode) {
        DataStorageMode.APP_STORAGE -> true
        DataStorageMode.EXTERNAL_SYNC -> treeUri.isNotEmpty()
    }

    fun isStorageChoiceDone(): Boolean = isStorageChoiceComplete(
        prefs.dataStorageMode.getValue(),
        prefs.externalRimeTreeUri.getValue(),
    )

    /**
     * Remembers the folder picked by the user.
     *
     * The grant itself is already persisted by the picker; this only records which
     * folder the app should sync with and drops the grant of the previous one.
     */
    suspend fun persistTreeUri(uri: Uri) {
        val previous = treeUri()
        prefs.externalRimeTreeUri.setValue(uri.toString())
        if (previous != null && previous != uri) {
            StorageAccess.releasePersistedPermission(previous)
        }
        if (previous?.toString() != uri.toString()) {
            saveIndex(SyncIndexData(treeUri = uri.toString()))
        }
    }

    /**
     * Continues with app-specific storage. The picked folder and its grant are kept, so switching
     * back to external sync does not ask the user to pick a folder again.
     */
    fun fallbackToAppStorage(reason: Throwable? = null) {
        if (!usesExternalSync()) return
        Timber.w(reason, "External sync unavailable; falling back to app-specific storage")
        onStorageModeChanged(DataStorageMode.EXTERNAL_SYNC, DataStorageMode.APP_STORAGE)
        prefs.dataStorageMode.setValue(DataStorageMode.APP_STORAGE)
        DeployNotification.showExternalSyncFallback()
    }

    /**
     * Leaving external sync invalidates the `.userdb` migration: the local database
     * is authoritative afterwards, so a later switch back has to import it again.
     */
    fun onStorageModeChanged(
        from: DataStorageMode,
        to: DataStorageMode,
    ) {
        if (from == DataStorageMode.EXTERNAL_SYNC && to == DataStorageMode.APP_STORAGE) {
            prefs.userDbMigrated.setValue(false)
        }
    }

    private fun shouldImportUserDb(): Boolean = !prefs.userDbMigrated.getValue()

    private fun markUserDbImported() {
        prefs.userDbMigrated.setValue(true)
    }

    // endregion

    // region Sync entry points

    suspend fun importToLocal(
        keepNotificationUntilDeploySuccess: Boolean = false,
        showProgress: Boolean = true,
    ): Result<SyncStats> {
        if (!usesExternalSync()) {
            return Result.success(SyncStats())
        }
        if (showProgress) DeployNotification.showProgress()
        return withContext(Dispatchers.IO) {
            runCatching {
                val treeUri = treeUri() ?: error("No data path selected")
                check(hasExternalAccess()) { "No access to data path" }
                val destRoot = DataManager.userDataDir
                val index = loadIndex()
                val skipUserDb = !shouldImportUserDb()
                val ownId = SyncPathPolicy.readOwnInstallationId()
                val syncDir =
                    SyncPathPolicy.treeRelativeSyncDir(
                        SyncPathPolicy.readOwnSyncDir(),
                        destRoot,
                    )
                val skipPrefix =
                    ownId?.takeIf { it.isNotEmpty() }?.let {
                        runCatching { SyncPathPolicy.ownSyncPrefix(it, syncDir) }.getOrNull()
                    }
                val entries = listExternalEntries(treeUri, skipUserDb, skipPrefix)
                val externalPaths = entries.map { it.relativePath }.toSet()
                val toCopy = entries.filter {
                    !it.file.isDir && SyncPathPolicy.shouldImport(it.relativePath, ownId, syncDir)
                }
                val createdDirs = LocalDirectoryGate()
                val copyResults =
                    mapParallel(toCopy) { entry ->
                        importExternalFile(entry, destRoot, index, createdDirs)
                    }
                val removeResult = removeLocalOrphans(destRoot, externalPaths, ownId, syncDir)
                saveIndex(withCurrentTree(mergeIndexEntries(index.entries, copyResults)))
                val importStats = mergeStats(copyResults.map { it.result })
                if (shouldImportUserDb() && importStats.failed == 0) {
                    markUserDbImported()
                }
                val stats = importStats + removeResult
                DeployNotification.notifyPartialCopyIfNeeded(
                    stats.failed,
                    "importToLocal",
                )
                stats
            }.onFailure { Timber.e(it, "importToLocal failed") }
        }.also { result ->
            if (!keepNotificationUntilDeploySuccess || result.isFailure) {
                DeployNotification.cancel()
            }
        }
    }

    suspend fun exportConfigFilesToExternal(): Result<SyncStats> = withContext(Dispatchers.IO) {
        if (!usesExternalSync()) {
            return@withContext Result.success(SyncStats())
        }
        runCatching {
            Timber.d(
                "exportConfigFilesToExternal: exporting ${DataManager.POST_SCHEMA_DEPLOY_EXPORT_FILES}",
            )
            val treeUri = treeUri() ?: error("No data path selected")
            check(hasExternalAccess()) { "No access to data path" }
            val srcRoot = DataManager.userDataDir
            val index = loadIndex()
            val copyResults =
                DataManager.POST_SCHEMA_DEPLOY_EXPORT_FILES.map { fileName ->
                    val sourceFile = srcRoot.resolve(fileName)
                    if (!sourceFile.isFile) {
                        Timber.w("Skip exporting missing config file: $fileName")
                        IndexedCopyResult(CopyResult(skipped = 1), null)
                    } else {
                        exportLocalFile(
                            sourceFile = sourceFile,
                            destDirUri = treeUri,
                            relativePath = fileName,
                            index = index,
                            force = true,
                        )
                    }
                }
            saveIndex(withCurrentTree(mergeIndexEntries(index.entries, copyResults)))
            mergeStats(copyResults.map { it.result }).also { stats ->
                Timber.d(
                    "exportConfigFilesToExternal: copied=${stats.copied}, " +
                        "skipped=${stats.skipped}, failed=${stats.failed}",
                )
                check(stats.failed == 0) { "Failed to export ${stats.failed} config file(s)" }
            }
        }.onFailure { Timber.e(it, "exportConfigFilesToExternal failed") }
    }

    suspend fun importThemeToLocal(configId: String): Result<SyncStats> = withContext(Dispatchers.IO) {
        runCatching {
            if (!usesExternalSync() || !hasExternalAccess()) {
                return@runCatching SyncStats()
            }
            val treeUri = treeUri() ?: return@runCatching SyncStats()
            val themeFile = StorageAccess.child(treeUri, "$configId.yaml")
            if (themeFile == null) {
                Timber.d("Theme file '$configId.yaml' not found at external root, skip import")
                return@runCatching SyncStats()
            }
            val index = loadIndex()
            val copyResult =
                importExternalFile(
                    entry = StorageWalkEntry(themeFile, themeFile.name),
                    destRoot = DataManager.userDataDir,
                    index = index,
                    createdDirs = LocalDirectoryGate(),
                )
            saveIndex(withCurrentTree(mergeIndexEntries(index.entries, listOf(copyResult))))
            val stats = mergeStats(listOf(copyResult.result))
            DeployNotification.notifyPartialCopyIfNeeded(
                stats.failed,
                "importThemeToLocal for '$configId'",
            )
            stats
        }.onFailure { Timber.e(it, "importThemeToLocal failed for '$configId'") }
    }

    suspend fun exportToExternal(): Result<SyncStats> = withContext(Dispatchers.IO) {
        if (!usesExternalSync()) {
            return@withContext Result.success(SyncStats())
        }
        runCatching {
            val treeUri = treeUri() ?: error("No data path selected")
            check(hasExternalAccess()) { "No access to data path" }
            val ownId = SyncPathPolicy.readOwnInstallationId()
            if (ownId.isNullOrEmpty()) {
                Timber.w("Export skipped: installation_id unreadable")
                return@runCatching SyncStats()
            }
            val syncDirRaw = SyncPathPolicy.readOwnSyncDir()
            val userDataDir = DataManager.userDataDir
            val syncDir = SyncPathPolicy.treeRelativeSyncDir(syncDirRaw, userDataDir)
            val exportPrefix =
                runCatching { SyncPathPolicy.ownSyncPrefix(ownId, syncDir) }.getOrElse { e ->
                    Timber.w(e, "Export skipped: invalid installation_id or sync_dir")
                    return@runCatching SyncStats()
                }
            val srcRoot = SyncPathPolicy.localOwnSyncDir(ownId, syncDirRaw, userDataDir)
            if (!srcRoot.isDirectory) {
                Timber.w("Export skipped: local sync dir does not exist: ${srcRoot.path}")
                return@runCatching SyncStats()
            }
            val index = loadIndex()
            val localFiles = listLocalFiles(srcRoot)
            if (localFiles.isEmpty()) {
                return@runCatching SyncStats()
            }
            val pending =
                localFiles.mapNotNull { file ->
                    val localRelative =
                        runCatching {
                            SyncRelativePath.normalize(file.relativeTo(srcRoot).path.replace('\\', '/'))
                        }.getOrNull() ?: return@mapNotNull null
                    file to SyncRelativePath.normalize("$exportPrefix/$localRelative")
                }
            val dirUris = ensureRemoteDirectories(treeUri, pending.map { (_, relativePath) -> relativePath })
            val copyResults =
                mapParallel(pending) { (file, relativePath) ->
                    exportLocalFile(
                        sourceFile = file,
                        destDirUri = dirUris[relativePath.substringBeforeLast('/', "")] ?: treeUri,
                        relativePath = relativePath,
                        index = index,
                        force = false,
                    )
                }
            saveIndex(withCurrentTree(mergeIndexEntries(index.entries, copyResults)))
            mergeStats(copyResults.map { it.result }).also { stats ->
                DeployNotification.notifyPartialCopyIfNeeded(
                    stats.failed,
                    "exportToExternal",
                )
                check(stats.failed == 0) { "Failed to export ${stats.failed} file(s)" }
            }
        }.onFailure { Timber.e(it, "exportToExternal failed") }
    }

    // endregion

    // region Per-file copy

    /** Copies one external file into the local user data dir. */
    private suspend fun importExternalFile(
        entry: StorageWalkEntry,
        destRoot: File,
        index: SyncIndexData,
        createdDirs: LocalDirectoryGate,
    ): IndexedCopyResult {
        val relativePath = entry.relativePath
        val size = entry.file.length
        val lastModified = entry.file.lastModified
        val destFile =
            runCatching {
                SyncRelativePath.resolveContained(destRoot, relativePath)
            }.getOrElse {
                Timber.w(it, "Rejected unsafe import path $relativePath")
                return IndexedCopyResult(CopyResult(failed = 1), null)
            }
        return runCatching {
            if (!shouldCopy(relativePath, size, lastModified, index) &&
                destFile.exists() &&
                destFile.length() == size &&
                destFile.lastModified() >= lastModified
            ) {
                return@runCatching IndexedCopyResult(
                    CopyResult(skipped = 1),
                    relativePath to SyncEntry(size, lastModified),
                )
            }
            destFile.parentFile?.let { parent ->
                val parentRelative = parent.relativeTo(destRoot).path.replace('\\', '/')
                if (parentRelative.isNotEmpty()) {
                    createdDirs.ensure(destRoot, SyncRelativePath.normalize(parentRelative))
                }
            }
            val bytes = copyFromSaf(entry.file.uri, destFile)
            destFile.setLastModified(entry.file.lastModified)
            IndexedCopyResult(
                CopyResult(copied = 1, bytesCopied = bytes),
                relativePath to SyncEntry(size, lastModified),
            )
        }.getOrElse {
            Timber.w(it, "Failed to import $relativePath")
            IndexedCopyResult(CopyResult(failed = 1), null)
        }
    }

    /** Copies one local file into the external [destDirUri]. */
    private suspend fun exportLocalFile(
        sourceFile: File,
        destDirUri: Uri,
        relativePath: String,
        index: SyncIndexData,
        force: Boolean,
    ): IndexedCopyResult {
        val size = sourceFile.length()
        val lastModified = sourceFile.lastModified()
        return runCatching {
            val changed = force || shouldCopy(relativePath, size, lastModified, index)
            if (!changed) {
                val remote = StorageAccess.child(destDirUri, sourceFile.name)
                if (remote != null && remote.length == size) {
                    return@runCatching IndexedCopyResult(
                        CopyResult(skipped = 1),
                        relativePath to SyncEntry(size, lastModified),
                    )
                }
            }
            val written =
                StorageAccess.pasteLocalFile(
                    srcPath = sourceFile.path,
                    destDirUri = destDirUri,
                    name = sourceFile.name,
                    mime = DEFAULT_MIME,
                    overwrite = true,
                )
            check(written.length == size) {
                "Exported ${sourceFile.name} with ${written.length} bytes, expected $size"
            }
            IndexedCopyResult(
                CopyResult(copied = 1, bytesCopied = written.length),
                relativePath to SyncEntry(size, lastModified),
            )
        }.getOrElse {
            Timber.w(it, "Failed to export $relativePath")
            IndexedCopyResult(CopyResult(failed = 1), null)
        }
    }

    /** Creates every directory that the given [relativePaths] need, once per directory. */
    private suspend fun ensureRemoteDirectories(
        treeUri: Uri,
        relativePaths: List<String>,
    ): Map<String, Uri> = relativePaths
        .map { it.substringBeforeLast('/', "") }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { it.count { c -> c == '/' } }
        .associateWith { relativeDir ->
            StorageAccess.mkdirp(treeUri, *relativeDir.split('/').toTypedArray()).uri
        }

    /**
     * Copies the SAF document [srcUri] into [destFile] through a temporary file next to it,
     * so that a reader of [destFile] never observes a partially written file.
     *
     * @return the number of bytes written
     */
    private suspend fun copyFromSaf(
        srcUri: Uri,
        destFile: File,
    ): Long = StorageAccess.readFile(srcUri) { input ->
        writeFromStream(destFile) { output -> input.copyTo(output) }
    }

    /**
     * Writes [destFile] from [copy] without ever exposing a partially written file: the content
     * first goes to a temporary file next to it and that file replaces [destFile] only when it is
     * complete. The original content is restored when the copy fails.
     *
     * @return the number of bytes written
     */
    internal fun writeFromStream(
        destFile: File,
        copy: (OutputStream) -> Unit,
    ): Long {
        val parent = destFile.parentFile ?: error("No parent for ${destFile.path}")
        val operationId = UUID.randomUUID().toString()
        val incoming = File(parent, ".trime-new-$operationId.tmp")
        val backup = File(parent, ".trime-bak-$operationId.tmp")
        parent.mkdirs()
        var expectedBytes = -1L
        var backedUp = false
        try {
            FileOutputStream(incoming).use { output ->
                copy(output)
            }
            expectedBytes = incoming.length()

            if (destFile.exists()) {
                if (!destFile.renameTo(backup)) {
                    error("Failed to back up ${destFile.path}")
                }
                backedUp = true
            }

            if (!incoming.renameTo(destFile)) {
                incoming.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                incoming.delete()
            }

            if (backup.exists()) {
                backup.delete()
            }

            return expectedBytes
        } catch (e: Exception) {
            if (backedUp && backup.exists()) {
                if (!destFile.exists() || destFile.length() != expectedBytes) {
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    backup.renameTo(destFile)
                }
            }
            if (!backedUp && incoming.exists()) {
                incoming.delete()
            }
            throw e
        } finally {
            if (expectedBytes >= 0 && destFile.exists() && destFile.length() == expectedBytes) {
                if (incoming.exists()) {
                    incoming.delete()
                }
                if (backup.exists()) {
                    backup.delete()
                }
            }
        }
    }

    /** Applies [transform] to every item with a bounded number of workers, keeping the input order. */
    internal suspend fun <T, R> mapParallel(
        items: List<T>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        transform: suspend (T) -> R,
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val results = arrayOfNulls<Any?>(items.size)
        coroutineScope {
            val channel = Channel<Pair<Int, T>>(capacity = parallelism * 2)
            repeat(parallelism.coerceAtMost(items.size)) {
                launch(dispatcher) {
                    for ((index, item) in channel) {
                        results[index] = transform(item)
                    }
                }
            }
            items.forEachIndexed { index, item ->
                channel.send(index to item)
            }
            channel.close()
        }
        @Suppress("UNCHECKED_CAST")
        return results.map { checkNotNull(it) as R }
    }

    /** Creates every local directory only once, even when the copies run in parallel. */
    private class LocalDirectoryGate {
        private val createdDirs = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        private val locks = ConcurrentHashMap<String, Any>()

        fun ensure(
            root: File,
            relativeDir: String,
        ) {
            if (relativeDir.isEmpty()) return
            val lock = locks[relativeDir] ?: locks.putIfAbsent(relativeDir, Any()) ?: locks[relativeDir]!!
            synchronized(lock) {
                if (!createdDirs.add(relativeDir)) {
                    return
                }
                val dir = SyncRelativePath.resolveContained(root, relativeDir)
                check(dir.mkdirs() || dir.isDirectory) { "Failed to create directory $relativeDir" }
            }
        }
    }

    private fun listLocalFiles(root: File): List<File> {
        if (!root.exists()) return emptyList()
        return root
            .walkTopDown()
            .filter { it.isFile }
            .filter {
                val relative = it.relativeTo(root).path.replace('\\', '/')
                !shouldSkip(relative)
            }.toList()
    }

    // endregion

    // region Orphan cleanup

    /**
     * Deletes local files that are not part of the external [externalPaths] listing,
     * keeping [SyncPathPolicy.shouldPreserveLocal] paths and empty directories in check.
     */
    internal fun removeLocalOrphans(
        root: File,
        externalPaths: Set<String>,
        ownId: String? = null,
        syncDir: String = SyncPathPolicy.DEFAULT_SYNC_DIR,
    ): CopyResult {
        if (!root.exists()) return CopyResult()
        var deleted = 0
        var failed = 0
        root
            .walkBottomUp()
            .filter { it != root }
            .filter {
                val relative = it.relativeTo(root).path.replace('\\', '/')
                !shouldSkip(relative, it.isDirectory)
            }.forEach { file ->
                val relative =
                    runCatching {
                        SyncRelativePath.normalize(file.relativeTo(root).path.replace('\\', '/'))
                    }.getOrElse {
                        Timber.w(it, "Skip orphan cleanup for unsafe path")
                        return@forEach
                    }
                when {
                    file.isFile && SyncPathPolicy.shouldPreserveLocal(relative, ownId, syncDir) -> Unit

                    file.isFile && relative !in externalPaths -> {
                        val deleteResult = FileUtils.delete(file)
                        if (deleteResult.isSuccess) {
                            deleted++
                            Timber.i("Delete orphan $relative")
                        } else {
                            failed++
                            Timber.w(deleteResult.exceptionOrNull(), "Failed to delete orphan $relative")
                        }
                    }

                    file.isDirectory && file.list()?.isEmpty() == true -> {
                        if (file.delete()) {
                            deleted++
                        } else {
                            failed++
                            Timber.w("Failed to delete empty directory $relative")
                        }
                    }
                }
            }
        return CopyResult(deleted = deleted, failed = failed)
    }

    // endregion

    // region Path rules

    /** Whether [relativePath] belongs to an internal folder that the sync must not touch. */
    internal fun shouldSkip(
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

    /** Whether [relativePath] should be visited; [skipPrefix] drops that path and its descendants. */
    internal fun shouldVisit(
        relativePath: String,
        skipPrefix: String? = null,
    ): Boolean {
        if (skipPrefix.isNullOrEmpty()) return true
        return relativePath != skipPrefix && !relativePath.startsWith("$skipPrefix/")
    }

    private fun shouldSync(
        entry: StorageWalkEntry,
        skipUserDb: Boolean,
        skipPrefix: String?,
    ): Boolean = !shouldSkip(entry.relativePath, entry.file.isDir, skipUserDb) &&
        shouldVisit(entry.relativePath, skipPrefix)

    /**
     * Walks the external [treeUri] and lists every entry that should take part in the sync.
     *
     * Subtrees rejected by [shouldSync] are never queried, so excluded `build`/`*.userdb`
     * directories neither cost a provider query each nor fail the walk when inaccessible.
     */
    private suspend fun listExternalEntries(
        treeUri: Uri,
        skipUserDb: Boolean,
        skipPrefix: String?,
    ): List<StorageWalkEntry> {
        val keepEntry: (StorageWalkEntry) -> Boolean = { shouldSync(it, skipUserDb, skipPrefix) }
        return StorageAccess.walk(treeUri) { entry, _ -> keepEntry(entry) }
            .filter(keepEntry)
            .toList()
    }

    // endregion

    // region Sync index and stats

    @Serializable
    private data class SyncEntry(
        val size: Long,
        val lastModified: Long,
    )

    @Serializable
    private data class SyncIndexData(
        val treeUri: String = "",
        val entries: Map<String, SyncEntry> = emptyMap(),
    )

    /**
     * File-backed index of the synced paths, keyed by the tree URI of the picked folder.
     *
     * Not thread-safe: neither [loadIndex] nor [saveIndex] may run concurrently.
     */
    private const val INDEX_FILE = "rime_sync_index.json"

    private val indexJson = Json { ignoreUnknownKeys = true }

    private val indexFile: File
        get() = File(appContext.filesDir, INDEX_FILE)

    private fun loadIndex(): SyncIndexData {
        val stored =
            indexFile
                .takeIf { it.exists() }
                ?.readText()
                ?.let { runCatching { indexJson.decodeFromString<SyncIndexData>(it) }.getOrNull() }
                ?: SyncIndexData()
        val currentTreeUri = treeUri()?.toString().orEmpty()
        if (stored.treeUri != currentTreeUri) {
            return SyncIndexData(treeUri = currentTreeUri)
        }
        return stored
    }

    private fun saveIndex(data: SyncIndexData) {
        indexFile.writeText(indexJson.encodeToString(data))
    }

    private fun withCurrentTree(entries: Map<String, SyncEntry>): SyncIndexData = SyncIndexData(
        treeUri = treeUri()?.toString().orEmpty(),
        entries = entries,
    )

    private fun shouldCopy(
        relativePath: String,
        size: Long,
        lastModified: Long,
        index: SyncIndexData,
    ): Boolean {
        val cached = index.entries[relativePath] ?: return true
        return cached.size != size || cached.lastModified != lastModified
    }

    private fun mergeIndexEntries(
        existing: Map<String, SyncEntry>,
        results: List<IndexedCopyResult>,
    ): Map<String, SyncEntry> {
        val merged = existing.toMutableMap()
        results.forEach { indexed ->
            indexed.indexEntry?.let { (path, entry) -> merged[path] = entry }
        }
        return merged
    }

    private fun mergeStats(results: List<CopyResult>): SyncStats = results.fold(SyncStats()) { acc, r ->
        acc.copy(
            copied = acc.copied + r.copied,
            skipped = acc.skipped + r.skipped,
            deleted = acc.deleted + r.deleted,
            failed = acc.failed + r.failed,
            bytesCopied = acc.bytesCopied + r.bytesCopied,
        )
    }

    private operator fun SyncStats.plus(other: CopyResult): SyncStats = copy(
        deleted = deleted + other.deleted,
        failed = failed + other.failed,
    )

    // endregion

    // region Copy results

    internal data class CopyResult(
        val copied: Int = 0,
        val skipped: Int = 0,
        val deleted: Int = 0,
        val failed: Int = 0,
        val bytesCopied: Long = 0,
    )

    private data class IndexedCopyResult(
        val result: CopyResult,
        val indexEntry: Pair<String, SyncEntry>?,
    )

    // endregion

    data class SyncStats(
        val copied: Int = 0,
        val skipped: Int = 0,
        val deleted: Int = 0,
        val failed: Int = 0,
        val bytesCopied: Long = 0,
    )
}
