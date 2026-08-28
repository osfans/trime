// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.DeployNotification
import com.osfans.trime.util.appContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

data class SyncStats(
    val copied: Int = 0,
    val skipped: Int = 0,
    val deleted: Int = 0,
    val failed: Int = 0,
    val bytesCopied: Long = 0,
)

object RimeDataSync {
    /**
     * Export requests queued by deploy/sync initiators before scheduling a
     * maintenance run. The daemon consumes the oldest request when a
     * maintenance completes and completes it with the export outcome.
     * Because librime executes maintenance in submission order and requests
     * are consumed in the same order, each initiator reliably awaits its own
     * export. Startup maintenance (which imports nothing) finds an empty
     * queue and exports nothing. All access is serialized by [syncMutex].
     */
    private val exportRequests = ArrayDeque<ExportRequest>()

    // Whether the maintenance that is currently running started with queued
    // requests. Set by [maintenanceStarted] and consumed by the matching
    // completion: a maintenance that started with an empty queue (e.g. the
    // startup maintenance) must not consume requests that were queued while
    // it was running, because those belong to the maintenance that follows.
    private var startedWithRequests = false

    // A queued export request: the deferred outcome for the initiator and
    // whether the maintenance's user-data sync/ is to be exported too (only
    // requests preceded by a successful external import or user-data sync
    // may overwrite the external dictionaries).
    data class ExportRequest(
        val deferred: CompletableDeferred<Boolean>,
        val exportUserData: Boolean,
    )

    // Serializes external-tree imports against maintenance runs: the
    // maintenance thread reads and writes the same local tree, so an import
    // overlapping it could mutate the maintenance's inputs. The lease is
    // reentrant (a deploy may acquire inside a [withMaintenanceLease] block
    // after a manual import): each acquire increments [maintenanceLeaseHolders]
    // and the underlying mutex is released when the last holder releases.
    // A deploy/sync keeps its lease until the maintenance it starts
    // terminates ([maintenanceFinished]); a maintenance without an owning
    // request (e.g. the startup maintenance) takes the lease itself when it
    // starts ([maintenanceStarted]).
    private val maintenanceMutex = Mutex()
    private var maintenanceLeaseHolders = 0

    /**
     * Takes the maintenance lease, waiting for any active maintenance to
     * terminate. The caller must be a deploy/sync that is about to import
     * the external tree: the lease is kept until the maintenance it starts
     * terminates (released by [maintenanceFinished]), and when librime
     * rejects the scheduled task it is released by the running maintenance's
     * own termination, so the caller never releases it directly.
     */
    suspend fun acquireMaintenanceLease() {
        // Wait without holding syncMutex: the maintenance that owns the
        // lease can only release it via [maintenanceFinished], which needs
        // syncMutex, so acquiring while holding syncMutex would deadlock
        // both sides (the terminal handling would never reach the release).
        maintenanceMutex.lock()
        syncMutex.withLock {
            maintenanceLeaseHolders++
        }
    }

    /**
     * Runs [block] while holding the maintenance lease, waiting for any
     * active maintenance to terminate first. For one-off local tree
     * mutations that are not followed by a maintenance (e.g. manual imports
     * or theme imports); deploy/sync use [acquireMaintenanceLease] instead
     * because their lease must span the maintenance they start.
     */
    suspend fun <T> withMaintenanceLease(block: suspend () -> T): T {
        acquireMaintenanceLease()
        try {
            return block()
        } finally {
            releaseMaintenanceLease()
        }
    }

    /**
     * Releases one lease held by a caller that acquired it but will not
     * schedule a maintenance (e.g. a sync whose external import failed
     * before the task could be scheduled): the daemon's terminal handling
     * would never run, so the lease would otherwise block later
     * imports/deploys/syncs forever. The underlying mutex is unlocked when
     * the last holder releases.
     */
    suspend fun releaseMaintenanceLease() = syncMutex.withLock {
        maintenanceLeaseHolders--
        if (maintenanceLeaseHolders == 0 && maintenanceMutex.isLocked) {
            maintenanceMutex.unlock()
        }
    }

    /**
     * Queues an export request for the maintenance that follows and returns
     * the deferred export outcome. The daemon completes the deferred after
     * the post-maintenance export, or with `false` if the maintenance fails.
     * When [exportUserData] is false only the post-schema-deploy config files
     * are exported (e.g. for local config deployments that skipped the
     * external import); exporting `sync/` then could overwrite newer
     * external dictionaries with a stale local snapshot.
     */
    suspend fun requestExportAfterMaintenance(
        exportUserData: Boolean = false,
    ): CompletableDeferred<Boolean> = syncMutex.withLock {
        CompletableDeferred<Boolean>().also {
            exportRequests.addLast(ExportRequest(it, exportUserData))
        }
    }

    /**
     * Records that a maintenance run started, so that its completion only
     * consumes requests that were queued before the run started. Requests
     * queued during the run belong to the maintenance that follows (they are
     * scheduled only after this one finishes), and consuming them here would
     * export a snapshot this maintenance never produced. Also takes the
     * maintenance lease, which [maintenanceFinished] releases.
     */
    suspend fun maintenanceStarted() = syncMutex.withLock {
        startedWithRequests = exportRequests.isNotEmpty()
        // A deploy/sync that started this maintenance already holds the
        // lease (acquired before its import). Take the lease ourselves only
        // for maintenance runs without an owner (e.g. the startup
        // maintenance). Either way [maintenanceFinished] releases one share
        // when the run terminates.
        if (maintenanceLeaseHolders == 0 && !maintenanceMutex.isLocked) {
            maintenanceMutex.lock()
        }
    }

    /**
     * Releases one lease share when a maintenance terminates, letting
     * waiting imports proceed. Also releases the share of a deploy/sync
     * whose task was rejected by librime: that share is owned by this
     * running maintenance.
     */
    suspend fun maintenanceFinished() = syncMutex.withLock {
        if (maintenanceLeaseHolders > 0) {
            maintenanceLeaseHolders--
        }
        if (maintenanceLeaseHolders == 0 && maintenanceMutex.isLocked) {
            maintenanceMutex.unlock()
        }
    }

    /**
     * Consumes the export request that this maintenance run owns and runs the
     * post-maintenance exports for it, completing the request with the export
     * outcome. Returns null when the run started without requests (e.g. a
     * startup maintenance), in which case nothing is exported.
     */
    suspend fun exportPendingRequest(): Boolean? = syncMutex.withLock {
        if (!startedWithRequests) return@withLock null
        startedWithRequests = false
        val request = exportRequests.removeFirstOrNull() ?: return@withLock null
        val results =
            buildList {
                add(exportConfigFilesToExternalLocked(appContext))
                if (request.exportUserData) {
                    add(exportToExternalLocked(appContext))
                }
            }
        results.forEach { result ->
            result.exceptionOrNull()?.let { Timber.e(it, "Failed to export after maintenance") }
        }
        val ok = results.all { it.isSuccess }
        request.deferred.complete(ok)
        ok
    }

    /**
     * Completes the export request that this failed maintenance run owns with
     * `false`, so that waiters can retry instead of hanging until their
     * timeout.
     */
    suspend fun failPendingRequest() = syncMutex.withLock {
        if (startedWithRequests) {
            startedWithRequests = false
            exportRequests.removeFirstOrNull()?.deferred?.complete(false)
        }
    }

    /**
     * Withdraws a queued export request (e.g. when scheduling was rejected by
     * librime) and completes it with `false` so the waiter can retry. Does
     * nothing when the request was already consumed by a maintenance.
     */
    suspend fun cancelExportRequest(request: CompletableDeferred<Boolean>) = syncMutex.withLock {
        val index = exportRequests.indexOfFirst { it.deferred === request }
        if (index >= 0) {
            exportRequests.removeAt(index)
            request.complete(false)
        }
    }

    /**
     * Serializes external-tree operations: the pre-maintenance imports and the
     * daemon's post-maintenance exports share local files and the index, so
     * they must not overlap (librime only guards its own maintenance thread).
     */
    private val syncMutex = Mutex()

    private val parallelism = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)

    private val uriPermissionFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private val prefs get() = AppPrefs.defaultInstance().profile

    fun treeUri(): Uri? = prefs.externalRimeTreeUri.getValue().takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }

    fun hasExternalAccess(context: Context = appContext): Boolean {
        val uri = treeUri() ?: return false
        val uriString = uri.toString()
        return context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uriString && it.isReadPermission && it.isWritePermission
        }
    }

    fun isRuntimeReady(): Boolean = DataManager.userDataDir.canWrite() && DataManager.sharedDataDir.canWrite()

    fun usesExternalSync(context: Context = appContext): Boolean = AppPrefs.defaultInstance().profile.dataStorageMode.getValue() ==
        DataStorageMode.EXTERNAL_SYNC

    fun isStorageAvailable(context: Context = appContext): Boolean = isRuntimeReady() && (!usesExternalSync(context) || hasExternalAccess(context))

    fun releaseTreeUri(
        context: Context,
        uri: Uri,
    ) {
        val stillGranted =
            context.contentResolver.persistedUriPermissions.any { it.uri == uri }
        if (!stillGranted) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, uriPermissionFlags)
        }.onFailure { Timber.w(it, "Failed to release URI permission: $uri") }
    }

    suspend fun clearExternalTree(context: Context) = syncMutex.withLock {
        treeUri()?.let { releaseTreeUri(context, it) }
        prefs.externalRimeTreeUri.setValue("")
        prefs.externalRimeDisplayName.setValue("")
        SyncIndex.clear()
    }

    suspend fun persistTreeUri(
        context: Context,
        uri: Uri,
    ) = syncMutex.withLock {
        val previous = treeUri()
        context.contentResolver.takePersistableUriPermission(uri, uriPermissionFlags)
        prefs.externalRimeTreeUri.setValue(uri.toString())
        prefs.externalRimeDisplayName.setValue(resolveDisplayName(context, uri))
        if (previous != null && previous != uri) {
            releaseTreeUri(context, previous)
        }
        if (previous?.toString() != uri.toString()) {
            SyncIndex.save(SyncIndexData(treeUri = uri.toString()))
        }
    }

    suspend fun importToLocal(
        context: Context = appContext,
        keepNotificationUntilDeploySuccess: Boolean = false,
        showProgress: Boolean = true,
    ): Result<SyncStats> {
        if (!usesExternalSync(context)) {
            return Result.success(SyncStats())
        }
        if (showProgress) DeployNotification.showProgress()
        // Callers serialize this against maintenance runs via the
        // maintenance lease: deploy/sync hold it from before the import
        // until their maintenance terminates ([acquireMaintenanceLease]),
        // one-off imports use [withMaintenanceLease].
        return syncMutex.withLock { importToLocalLocked(context) }
            .onFailure { Timber.e(it, "importToLocal failed") }
            .also { result ->
                if (!keepNotificationUntilDeploySuccess || result.isFailure) {
                    DeployNotification.cancel()
                }
            }
    }

    private suspend fun importToLocalLocked(context: Context): Result<SyncStats> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = treeUri() ?: error("No data path selected")
            check(hasExternalAccess(context)) { "No access to data path" }
            val cr = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val destRoot = DataManager.userDataDir
            val index = SyncIndex.load()
            val skipUserDb = !UserDbMigration.shouldImportUserDb()
            val files = SafTreeWalker.listFiles(cr, treeUri, rootId, skipUserDb = skipUserDb)
            val externalPaths = files.map { it.relativePath }.toSet()
            val createdDirs = LocalDirectoryGate()
            val copyResults =
                BoundedCopyPool.mapParallel(files, parallelism) { entry ->
                    copySafToLocal(
                        context,
                        treeUri,
                        entry,
                        destRoot,
                        index.entries,
                        externalWins = true,
                        createdDirs,
                    )
                }
            val removeResult = OrphanCleaner.removeLocalOrphans(destRoot, externalPaths)
            SyncIndex.save(SyncIndex.withCurrentTree(mergeIndexEntries(index.entries, copyResults)))
            val importStats = mergeStats(copyResults.map { it.result })
            if (UserDbMigration.shouldImportUserDb() && importStats.failed == 0) {
                UserDbMigration.markImported()
            }
            DeployNotification.notifyPartialCopyIfNeeded(
                importStats + removeResult.toCopyResult(),
                "importToLocal",
            )
        }
    }

    private suspend fun exportConfigFilesToExternalLocked(context: Context = appContext): Result<SyncStats> = withContext(Dispatchers.IO) {
        if (!usesExternalSync(context)) {
            return@withContext Result.success(SyncStats())
        }
        runCatching {
            Timber.d(
                "exportConfigFilesToExternal: exporting ${DataManager.POST_SCHEMA_DEPLOY_EXPORT_FILES}",
            )
            val treeUri = treeUri() ?: error("No data path selected")
            check(hasExternalAccess(context)) { "No access to data path" }
            val cr = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val srcRoot = DataManager.userDataDir
            val index = SyncIndex.load()
            val listing = SafTreeWalker.listTree(cr, treeUri, rootId)
            val cache = SafPathCache(listing, rootId)
            val copyResults =
                DataManager.POST_SCHEMA_DEPLOY_EXPORT_FILES.map { fileName ->
                    val sourceFile = srcRoot.resolve(fileName)
                    if (!sourceFile.isFile) {
                        Timber.w("Skip exporting missing config file: $fileName")
                        IndexedCopyResult(CopyResult(0, 1, deleted = 0, failed = 0, bytesCopied = 0), null)
                    } else {
                        copyLocalToSaf(
                            cr,
                            treeUri,
                            sourceFile,
                            srcRoot,
                            index.entries,
                            cache,
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

    suspend fun importThemeToLocal(
        context: Context = appContext,
        configId: String,
    ): Result<SyncStats> = withMaintenanceLease {
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!usesExternalSync(context) || !hasExternalAccess(context)) {
                        return@runCatching SyncStats()
                    }
                    val treeUri = treeUri() ?: return@runCatching SyncStats()
                    val cr = context.contentResolver
                    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                    val destRoot = DataManager.userDataDir
                    val themeFileName = "$configId.yaml"
                    val index = SyncIndex.load()
                    val entry =
                        SafTreeWalker.findFileEntry(cr, treeUri, rootId, themeFileName)
                    if (entry == null) {
                        Timber.d("Theme file '$themeFileName' not found at external root, skip import")
                        return@runCatching SyncStats()
                    }
                    val createdDirs = LocalDirectoryGate()
                    val copyResult =
                        copySafToLocal(
                            context,
                            treeUri,
                            entry,
                            destRoot,
                            index.entries,
                            externalWins = true,
                            createdDirs,
                        )
                    SyncIndex.save(SyncIndex.withCurrentTree(mergeIndexEntries(index.entries, listOf(copyResult))))
                    DeployNotification.notifyPartialCopyIfNeeded(
                        mergeStats(listOf(copyResult.result)),
                        "importThemeToLocal for '$configId'",
                    )
                }.onFailure { Timber.e(it, "importThemeToLocal failed for '$configId'") }
            }
        }
    }

    private suspend fun exportToExternalLocked(context: Context = appContext): Result<SyncStats> = withContext(Dispatchers.IO) {
        if (!usesExternalSync(context)) {
            return@withContext Result.success(SyncStats())
        }
        runCatching {
            val treeUri = treeUri() ?: error("No data path selected")
            check(hasExternalAccess(context)) { "No access to data path" }
            val cr = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val srcRoot = DataManager.userDataDir.resolve("sync")
            if (!srcRoot.exists()) {
                return@runCatching SyncStats()
            }
            val index = SyncIndex.load()
            val localFiles = listLocalFiles(srcRoot)
            val listing = SafTreeWalker.listTree(cr, treeUri, rootId)
            val cache = SafPathCache(listing, rootId)
            val indexData = SyncIndex.withCurrentTree(index.entries)
            val parentDirsToEnsure =
                localFiles
                    .filter { file ->
                        val relativePath = file.relativeTo(srcRoot).path.replace('\\', '/')
                        SyncIndex.shouldCopy(
                            relativePath,
                            file.length(),
                            file.lastModified(),
                            indexData,
                        )
                    }.map { file ->
                        file.relativeTo(srcRoot).path.replace('\\', '/').substringBeforeLast('/', "")
                    }.distinct()
                    .sortedBy { it.count { c -> c == '/' } }
            for (parentDir in parentDirsToEnsure) {
                val externalDir = if (parentDir.isEmpty()) "sync" else "sync/$parentDir"
                cache.ensureDirectory(cr, treeUri, externalDir)
            }
            val copyResults =
                BoundedCopyPool.mapParallel(localFiles, parallelism) { file ->
                    copyLocalToSaf(
                        cr,
                        treeUri,
                        file,
                        srcRoot,
                        index.entries,
                        cache,
                        exportPathPrefix = "sync",
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
        val copied: Int,
        val skipped: Int,
        val deleted: Int = 0,
        val failed: Int,
        val bytesCopied: Long,
    )

    private data class IndexedCopyResult(
        val result: CopyResult,
        val indexEntry: Pair<String, SyncEntry>?,
    )

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

    private fun copySafToLocal(
        context: Context,
        treeUri: Uri,
        entry: SafFileEntry,
        destRoot: File,
        indexEntries: Map<String, SyncEntry>,
        externalWins: Boolean,
        createdDirs: LocalDirectoryGate,
    ): IndexedCopyResult {
        val destFile =
            runCatching {
                SyncRelativePath.resolveContained(destRoot, entry.relativePath)
            }.getOrElse {
                Timber.w(it, "Rejected unsafe import path ${entry.relativePath}")
                return IndexedCopyResult(CopyResult(0, 0, deleted = 0, failed = 1, bytesCopied = 0), null)
            }
        return runCatching {
            if (!SyncIndex.shouldCopy(entry.relativePath, entry.size, entry.lastModified, SyncIndex.withCurrentTree(indexEntries))) {
                if (destFile.exists() &&
                    destFile.length() == entry.size &&
                    destFile.lastModified() >= entry.lastModified
                ) {
                    return IndexedCopyResult(
                        CopyResult(0, 1, deleted = 0, failed = 0, bytesCopied = 0),
                        entry.relativePath to SyncEntry(entry.size, entry.lastModified),
                    )
                }
            }
            if (!externalWins && destFile.exists() && destFile.lastModified() > entry.lastModified) {
                return IndexedCopyResult(
                    CopyResult(0, 1, deleted = 0, failed = 0, bytesCopied = 0),
                    entry.relativePath to SyncEntry(entry.size, entry.lastModified),
                )
            }
            destFile.parentFile?.let { parent ->
                val parentRelative = parent.relativeTo(destRoot).path.replace('\\', '/')
                if (parentRelative.isNotEmpty()) {
                    val normalized = SyncRelativePath.normalize(parentRelative)
                    createdDirs.ensure(destRoot, normalized)
                }
            }
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)
            val bytes =
                context.contentResolver.openFileDescriptor(docUri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        AtomicLocalFileCopy.copyFromInput(input, destFile)
                    }
                } ?: error("Cannot open $docUri")
            destFile.setLastModified(entry.lastModified)
            IndexedCopyResult(
                CopyResult(1, 0, deleted = 0, failed = 0, bytesCopied = bytes),
                entry.relativePath to SyncEntry(entry.size, entry.lastModified),
            )
        }.getOrElse {
            Timber.w(it, "Failed to import ${entry.relativePath}")
            IndexedCopyResult(CopyResult(0, 0, deleted = 0, failed = 1, bytesCopied = 0), null)
        }
    }

    private fun copyLocalToSaf(
        contentResolver: ContentResolver,
        treeUri: Uri,
        sourceFile: File,
        srcRoot: File,
        indexEntries: Map<String, SyncEntry>,
        cache: SafPathCache,
        force: Boolean = false,
        exportPathPrefix: String = "",
    ): IndexedCopyResult {
        val localRelativePath =
            runCatching {
                SyncRelativePath.normalize(sourceFile.relativeTo(srcRoot).path.replace('\\', '/'))
            }.getOrElse {
                Timber.w(it, "Rejected unsafe export path")
                return IndexedCopyResult(CopyResult(0, 0, deleted = 0, failed = 1, bytesCopied = 0), null)
            }
        val relativePath =
            if (exportPathPrefix.isEmpty()) {
                localRelativePath
            } else {
                SyncRelativePath.normalize("$exportPathPrefix/$localRelativePath")
            }
        val size = sourceFile.length()
        val lastModified = sourceFile.lastModified()
        return runCatching {
            if (!force &&
                !SyncIndex.shouldCopy(relativePath, size, lastModified, SyncIndex.withCurrentTree(indexEntries))
            ) {
                if (cache.hasFile(relativePath, size)) {
                    return IndexedCopyResult(
                        CopyResult(0, 1, deleted = 0, failed = 0, bytesCopied = 0),
                        relativePath to SyncEntry(size, lastModified),
                    )
                }
            }
            val parentDir = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            AtomicSafFileCopy.copyFromFile(
                contentResolver,
                treeUri,
                cache,
                sourceFile,
                relativePath,
                parentDir,
                fileName,
            )
            IndexedCopyResult(
                CopyResult(1, 0, deleted = 0, failed = 0, bytesCopied = size),
                relativePath to SyncEntry(size, lastModified),
            )
        }.getOrElse {
            Timber.w(it, "Failed to export $relativePath")
            IndexedCopyResult(CopyResult(0, 0, deleted = 0, failed = 1, bytesCopied = 0), null)
        }
    }

    private fun OrphanCleaner.Result.toCopyResult(): CopyResult = CopyResult(0, 0, deleted = deleted, failed = failed, bytesCopied = 0)

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

    private fun resolveDisplayName(
        context: Context,
        uri: Uri,
    ): String {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
        context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0) ?: docId
                }
            }
        return docId
    }
}
