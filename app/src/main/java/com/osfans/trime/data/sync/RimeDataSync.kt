// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.net.Uri
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.storage.StorageAccess
import com.osfans.trime.storage.StorageWalkEntry
import com.osfans.trime.util.DeployNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class SyncStats(
    val copied: Int = 0,
    val skipped: Int = 0,
    val deleted: Int = 0,
    val failed: Int = 0,
    val bytesCopied: Long = 0,
)

object RimeDataSync {
    private const val DEFAULT_MIME = "application/octet-stream"

    private val parallelism = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)

    private val prefs get() = AppPrefs.defaultInstance().profile

    fun treeUri(): Uri? = prefs.externalRimeTreeUri.getValue().takeIf { it.isNotEmpty() }?.let(Uri::parse)

    fun hasExternalAccess(): Boolean {
        val uriString = treeUri()?.toString() ?: return false
        return StorageAccess.persistedPermissions().any {
            it.uri.toString() == uriString && it.read && it.write
        }
    }

    fun isRuntimeReady(): Boolean = DataManager.resolvedUserDataDir() != null && DataManager.resolvedSharedDataDir() != null

    fun usesExternalSync(): Boolean = prefs.dataStorageMode.getValue() == DataStorageMode.EXTERNAL_SYNC

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

    fun isStorageAvailable(): Boolean = isRuntimeReady() && (!usesExternalSync() || hasExternalAccess())

    suspend fun syncUserDataWithOptionalExport(syncUserData: suspend () -> Boolean): Boolean {
        val dictSyncOk = syncUserData()
        if (!dictSyncOk) {
            Timber.w("Export skipped: Rime user sync failed")
            return false
        }
        val exportOk =
            when {
                !usesExternalSync() -> true

                !hasExternalAccess() -> {
                    Timber.w("Export skipped: no data path selected")
                    false
                }

                else -> exportToExternal().isSuccess
            }
        return exportOk
    }

    fun clearExternalTree() {
        treeUri()?.let(StorageAccess::releasePersistedPermission)
        prefs.externalRimeTreeUri.setValue("")
        prefs.externalRimeDisplayName.setValue("")
        SyncIndex.clear()
    }

    /**
     * Remembers the folder picked by the user.
     *
     * The grant itself is already persisted by the picker; this only records which
     * folder the app should sync with and drops the grant of the previous one.
     */
    suspend fun persistTreeUri(uri: Uri) {
        val previous = treeUri()
        prefs.externalRimeTreeUri.setValue(uri.toString())
        prefs.externalRimeDisplayName.setValue(StorageAccess.stat(uri)?.name.orEmpty())
        if (previous != null && previous != uri) {
            StorageAccess.releasePersistedPermission(previous)
        }
        if (previous?.toString() != uri.toString()) {
            SyncIndex.save(SyncIndexData(treeUri = uri.toString()))
        }
    }

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
                val index = SyncIndex.load()
                val skipUserDb = !UserDbMigration.shouldImportUserDb()
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
                val entries = SafTreeWalker.listExternalEntries(treeUri, skipUserDb, skipPrefix)
                val externalPaths = entries.map { it.relativePath }.toSet()
                val toCopy = entries.filter {
                    !it.file.isDir && SyncPathPolicy.shouldImport(it.relativePath, ownId, syncDir)
                }
                val createdDirs = LocalDirectoryGate()
                val copyResults =
                    BoundedCopyPool.mapParallel(toCopy, parallelism) { entry ->
                        importExternalFile(entry, destRoot, index, createdDirs)
                    }
                val removeResult = OrphanCleaner.removeLocalOrphans(destRoot, externalPaths, ownId, syncDir)
                SyncIndex.save(SyncIndex.withCurrentTree(mergeIndexEntries(index.entries, copyResults)))
                val importStats = mergeStats(copyResults.map { it.result })
                if (UserDbMigration.shouldImportUserDb() && importStats.failed == 0) {
                    UserDbMigration.markImported()
                }
                DeployNotification.notifyPartialCopyIfNeeded(
                    importStats + removeResult.toCopyResult(),
                    "importToLocal",
                )
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
            val index = SyncIndex.load()
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
            SyncIndex.save(SyncIndex.withCurrentTree(mergeIndexEntries(index.entries, copyResults)))
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
            val index = SyncIndex.load()
            val copyResult =
                importExternalFile(
                    entry = StorageWalkEntry(themeFile, themeFile.name),
                    destRoot = DataManager.userDataDir,
                    index = index,
                    createdDirs = LocalDirectoryGate(),
                )
            SyncIndex.save(SyncIndex.withCurrentTree(mergeIndexEntries(index.entries, listOf(copyResult))))
            DeployNotification.notifyPartialCopyIfNeeded(
                mergeStats(listOf(copyResult.result)),
                "importThemeToLocal for '$configId'",
            )
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
            val index = SyncIndex.load()
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
                BoundedCopyPool.mapParallel(pending, parallelism) { (file, relativePath) ->
                    exportLocalFile(
                        sourceFile = file,
                        destDirUri = dirUris[relativePath.substringBeforeLast('/', "")] ?: treeUri,
                        relativePath = relativePath,
                        index = index,
                        force = false,
                    )
                }
            SyncIndex.save(SyncIndex.withCurrentTree(mergeIndexEntries(index.entries, copyResults)))
            mergeStats(copyResults.map { it.result }).also { stats ->
                DeployNotification.notifyPartialCopyIfNeeded(
                    stats,
                    "exportToExternal",
                )
                check(stats.failed == 0) { "Failed to export ${stats.failed} file(s)" }
            }
        }.onFailure { Timber.e(it, "exportToExternal failed") }
    }

    private data class CopyResult(
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
            if (!SyncIndex.shouldCopy(relativePath, size, lastModified, index) &&
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
            val bytes = AtomicLocalFileCopy.copyFromSaf(entry.file.uri, destFile)
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
            val changed = force || SyncIndex.shouldCopy(relativePath, size, lastModified, index)
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

    private fun listLocalFiles(root: File): List<File> {
        if (!root.exists()) return emptyList()
        return root
            .walkTopDown()
            .filter { it.isFile }
            .filter {
                val relative = it.relativeTo(root).path.replace('\\', '/')
                !SafTreeWalker.shouldSkip(relative)
            }.toList()
    }

    private fun OrphanCleaner.Result.toCopyResult(): CopyResult = CopyResult(deleted = deleted, failed = failed)

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
}
