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
import kotlinx.coroutines.Dispatchers
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

    suspend fun syncUserDataWithOptionalExport(
        context: Context = appContext,
        syncUserData: suspend () -> Boolean,
    ): Boolean {
        val dictSyncOk = syncUserData()
        if (!dictSyncOk) {
            Timber.w("Export skipped: Rime user sync failed")
            return false
        }
        val exportOk =
            when {
                !usesExternalSync(context) -> true
                !hasExternalAccess(context) -> {
                    Timber.w("Export skipped: no data path selected")
                    false
                }
                else -> exportToExternal(context).isSuccess
            }
        return exportOk
    }

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

    fun clearExternalTree(context: Context) {
        treeUri()?.let { releaseTreeUri(context, it) }
        prefs.externalRimeTreeUri.setValue("")
        prefs.externalRimeDisplayName.setValue("")
        SyncIndex.clear()
    }

    fun persistTreeUri(
        context: Context,
        uri: Uri,
    ) {
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
        return withContext(Dispatchers.IO) {
            runCatching {
                val treeUri = treeUri() ?: error("No data path selected")
                check(hasExternalAccess(context)) { "No access to data path" }
                val cr = context.contentResolver
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
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
                val files =
                    SafTreeWalker.listFiles(
                        cr,
                        treeUri,
                        rootId,
                        skipUserDb = skipUserDb,
                        skipPrefix = skipPrefix,
                    )
                val externalPaths = files.map { it.relativePath }.toSet()
                val toCopy = files.filter { SyncPathPolicy.shouldImport(it.relativePath, ownId, syncDir) }
                val createdDirs = LocalDirectoryGate()
                val copyResults =
                    BoundedCopyPool.mapParallel(toCopy, parallelism) { entry ->
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

    suspend fun exportConfigFilesToExternal(context: Context = appContext): Result<SyncStats> = withContext(Dispatchers.IO) {
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
            val cache =
                SafPathCache(
                    SafTreeListing(emptyList(), mapOf("" to rootId)),
                    rootId,
                )
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
    ): Result<SyncStats> = withContext(Dispatchers.IO) {
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

    suspend fun exportToExternal(context: Context = appContext): Result<SyncStats> = withContext(Dispatchers.IO) {
        if (!usesExternalSync(context)) {
            return@withContext Result.success(SyncStats())
        }
        runCatching {
            val treeUri = treeUri() ?: error("No data path selected")
            check(hasExternalAccess(context)) { "No access to data path" }
            val cr = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val ownId = SyncPathPolicy.readOwnInstallationId()
            if (ownId.isNullOrEmpty()) {
                Timber.w("Export skipped: installation_id unreadable")
                return@runCatching SyncStats()
            }
            val syncDirRaw = SyncPathPolicy.readOwnSyncDir()
            val userDataDir = DataManager.userDataDir
            val syncDir = SyncPathPolicy.treeRelativeSyncDir(syncDirRaw, userDataDir)
            val exportPrefix =
                runCatching { SyncPathPolicy.ownSyncPrefix(ownId, syncDir) }.getOrElse {
                    Timber.w("Export skipped: installation_id unreadable")
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
            val listing = SafTreeWalker.listTree(cr, treeUri, rootId, limitToPrefix = exportPrefix)
            val cache = SafPathCache(listing, rootId)
            val indexData = SyncIndex.withCurrentTree(index.entries)
            val parentDirsToEnsure =
                localFiles
                    .mapNotNull { file ->
                        val relativePath =
                            runCatching {
                                val localRel =
                                    SyncRelativePath.normalize(
                                        file.relativeTo(srcRoot).path.replace('\\', '/'),
                                    )
                                SyncRelativePath.normalize("$exportPrefix/$localRel")
                            }.getOrNull() ?: return@mapNotNull null
                        if (!SyncIndex.shouldCopy(
                                relativePath,
                                file.length(),
                                file.lastModified(),
                                indexData,
                            )
                        ) {
                            return@mapNotNull null
                        }
                        relativePath.substringBeforeLast('/', "")
                    }.distinct()
                    .sortedBy { it.count { c -> c == '/' } }
            for (parentDir in parentDirsToEnsure) {
                if (parentDir.isNotEmpty()) {
                    cache.ensureDirectory(cr, treeUri, parentDir)
                }
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
                        exportPathPrefix = exportPrefix,
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
