/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core.data

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.util.AtomicFile
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.data.UserDataManager.sync
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.DeployNotification
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.appContext
import io.github.whiredplanck.storageaccess.StorageAccess
import io.github.whiredplanck.storageaccess.StorageDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs

object UserDataManager {
    private const val SYNC_MANIFEST_NAME = ".sync-manifest.json"

    private const val DEFAULT_MIME = "application/octet-stream"

    private const val MTIME_TOLERANCE = 2000L

    private const val HASH_BUFFER_SIZE = 32 * 1024

    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"

    private const val SCHEMA_LIST_CUSTOM_PATCH = """
      patch:
        schema_list:
          - schema: luna_pinyin
          - schema: luna_pinyin_simp
    """

    private val lock = Mutex()

    private val json by lazy { Json }

    private fun createSyncManifest(files: Map<String, SyncManifest.Entry>) = SyncManifest(
        sha256(files),
        files,
    )

    private fun deserializeSyncManifest(raw: String) = json.decodeFromString<SyncManifest>(raw)

    private fun serializeSyncManifest(index: SyncManifest) = json.encodeToString(index)

    private fun sha256(files: Map<String, SyncManifest.Entry>): String {
        val hashes = files.entries
            .sortedBy { it.key }
            .mapNotNull { it.value.hash?.takeIf { hash -> hash.isNotEmpty() } }
        if (hashes.isEmpty()) return ""
        return MessageDigest.getInstance("SHA-256")
            .digest(hashes.joinToString("").encodeToByteArray())
            .let { Base64.encodeToString(it, 0).trim() }
    }

    private suspend fun hashStream(input: InputStream): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(HASH_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        Base64.encodeToString(digest.digest(), 0).trim()
    }

    private suspend fun localHash(file: File): String? = runCatching {
        file.inputStream().buffered(HASH_BUFFER_SIZE).use {
            hashStream(it)
        }
    }.getOrNull()

    private suspend fun remoteHash(file: StorageDocument): String? = runCatching {
        StorageAccess.readFile(file.uri) { hashStream(it.buffered(HASH_BUFFER_SIZE)) }
    }.getOrNull()

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    private val dataDir: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Timber.d("Using device protected storage")
            appContext.createDeviceProtectedStorageContext().dataDir
        } else {
            File(appContext.applicationInfo.dataDir)
        }

    private val externalFilesDir get() = TrimeApplication.getInstance().externalFilesDir

    val userDataDir = File(externalFilesDir, "rime")

    private val dataStorageMode by AppPrefs.defaultInstance().profile.dataStorageMode
    private var externalRimeTreeUri by AppPrefs.defaultInstance().profile.externalRimeTreeUri

    private val externalRimeDir: Uri
        @SuppressLint("UseKtx")
        get() = Uri.parse(externalRimeTreeUri)

    fun useExternalSync(): Boolean = dataStorageMode == DataStorageMode.EXTERNAL_SYNC

    fun hasExternalAccess(): Boolean = StorageAccess.persistedPermissions().any {
        it.uri == externalRimeDir && it.read && it.write
    }

    fun isStorageAvailable(): Boolean = !useExternalSync() || hasExternalAccess()

    fun isStorageChoiceDone(): Boolean = when (dataStorageMode) {
        DataStorageMode.APP_STORAGE -> true
        DataStorageMode.EXTERNAL_SYNC -> externalRimeTreeUri.isNotEmpty()
    }

    /**
     * Bootstraps a usable schema list into the user data dir on first run, so a
     * freshly installed app has something to deploy.
     */
    fun ensureDefaultCustomConfig() {
        val custom = File(userDataDir, DEFAULT_CUSTOM_FILE_NAME)
        if (!custom.exists() && custom.createNewFile()) {
            custom.writeText(SCHEMA_LIST_CUSTOM_PATCH.trimIndent())
        }
    }

    /**
     * Remembers the folder picked by the user.
     *
     * The grant itself is already persisted by the picker; this only records which
     * folder the app should sync with and drops the grant of the previous one. A
     * different folder invalidates the recorded manifest, so the next [sync] mirrors
     * the new tree from scratch.
     */
    fun persistTreeUri(uri: Uri) {
        val previous = externalRimeTreeUri.takeIf { it.isNotEmpty() }
            ?.let(Uri::parse)
        if (previous != uri) {
            previous?.let {
                StorageAccess.releasePersistedPermission(it)
            }
            externalRimeTreeUri = uri.toString()
            File(dataDir, SYNC_MANIFEST_NAME).delete()
        }
    }

    suspend fun sync(): Boolean = lock.withLock {
        if (!useExternalSync()) return@withLock true
        if (!hasExternalAccess()) {
            DeployNotification.promptDataPathPermissionRevoked()
            return@withLock false
        }

        withContext(Dispatchers.IO) {
            val manifestFile = File(dataDir, SYNC_MANIFEST_NAME)
            val oldManifest = manifestFile
                .runCatching { deserializeSyncManifest(bufferedReader().use { it.readText() }) }
                .getOrElse { SyncManifest("", emptyMap()) }

            val remoteMap = buildMap {
                StorageAccess.walk(externalRimeDir)
                    .collect { put(it.relativePath, it.file) }
            }
            val localMap = walkLocal(userDataDir)

            val allPaths = (localMap.keys + remoteMap.keys + oldManifest.files.keys)
                .sortedBy { it.count { c -> c == '/' } }

            // Fast path: when every path still matches the recorded state on both sides and no
            // path was added or removed, the prospective manifest equals the stored one. Reusing
            // the recorded hashes lets us fingerprint it and compare with the stored sha256
            // before doing any I/O or rewriting the manifest.
            if (allPaths.size == oldManifest.files.size) {
                val preview = buildMap {
                    for (p in allPaths) {
                        val entry = oldManifest.files[p]
                        if (entry == null || !isUnchanged(p, localMap, remoteMap, entry)) {
                            return@buildMap
                        }
                        put(p, entry)
                    }
                }
                if (preview.size == oldManifest.files.size &&
                    createSyncManifest(preview).sha256 == oldManifest.sha256
                ) {
                    Timber.d("No changes detected, skipping sync")
                    return@withContext
                }
            }

            val newMap = mutableMapOf<String, SyncManifest.Entry>()
            val removed = mutableSetOf<String>()

            for (path in allPaths) {
                if (removed.any { path == it || path.startsWith("$it/") }) continue

                try {
                    syncEntry(path, localMap, remoteMap, oldManifest, newMap, removed)
                } catch (e: IOException) {
                    throw e
                } catch (e: SecurityException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to sync $path")
                    oldManifest.files[path]?.let { newMap[path] = it }
                }
            }

            manifestFile.bufferedWriter().use {
                val newManifest = createSyncManifest(newMap)
                it.write(serializeSyncManifest(newManifest))
            }
        }

        Timber.d("Synced")
        true
    }

    private suspend fun syncEntry(
        path: String,
        localMap: Map<String, File>,
        remoteMap: Map<String, StorageDocument>,
        oldManifest: SyncManifest,
        newMap: MutableMap<String, SyncManifest.Entry>,
        removed: MutableSet<String>,
    ) {
        var local = localMap[path]?.takeIf { it.exists() }
        val remote = remoteMap[path]
        val inManifest = oldManifest.files[path]
        var localIsDir = local?.isDirectory == true
        val remoteIsDir = remote?.isDir == true

        // --- Deletion handling ---
        if (inManifest != null && local == null && remote != null) {
            Timber.d("Deleting external (local deletion detected): $path")
            StorageAccess.delete(remote.uri)
            if (remoteIsDir) removed += path
            return
        }
        if (inManifest != null && local != null && remote == null) {
            Timber.d("Deleting local (external deletion detected): $path")
            FileUtils.delete(local).getOrThrow()
            if (localIsDir) removed += path
            return
        }
        if (inManifest != null && local == null && remote == null) {
            return
        }

        // Type mismatch: prioritize the external side
        if (local != null && remote != null && localIsDir != remoteIsDir) {
            Timber.d("Type mismatch for $path, prioritizing external")
            if (localIsDir) {
                FileUtils.delete(local).getOrThrow()
                removed += path
            } else {
                local.delete()
            }
            local = null
            localIsDir = false
        }

        // --- Directory sync ---
        if (localIsDir || remoteIsDir) {
            // Remote-only directory: mirror it locally.
            if (remoteIsDir && !localIsDir) resolveLocal(path).mkdirs()
            // Local-only directory: mirror it on the external side.
            if (localIsDir && !remoteIsDir) remoteMkdir(path)
            newMap[path] = SyncManifest.Entry(0L, 0L, 0L, true, null)
            return
        }

        // --- File sync ---
        val localMtime = local?.lastModified() ?: 0L
        val localSize = local?.length() ?: 0L
        val remoteMtime = remote?.lastModified ?: 0L
        val remoteSize = remote?.length ?: 0L
        var finalHash = inManifest?.hash

        when {
            local != null && remote != null -> {
                val baseLocalMtime = inManifest?.localMtime ?: 0L
                val baseRemoteMtime = inManifest?.remoteMtime ?: 0L
                var localChanged = inManifest == null ||
                    abs(localMtime - baseLocalMtime) > MTIME_TOLERANCE ||
                    localSize != inManifest.length
                var remoteChanged = inManifest == null ||
                    abs(remoteMtime - baseRemoteMtime) > MTIME_TOLERANCE ||
                    remoteSize != inManifest.length

                // Digest both sides once and reuse the results below, so a transfer never
                // re-reads the file it just wrote.
                var localDigest: String? = null
                var remoteDigest: String? = null
                if ((localChanged || remoteChanged) && localSize == remoteSize) {
                    localDigest = localHash(local)
                    remoteDigest = remoteHash(remote)
                    if (localDigest != null && localDigest == remoteDigest) {
                        Timber.d("Content match for $path, skipping transfer")
                        localChanged = false
                        remoteChanged = false
                    }
                    finalHash = localDigest
                }

                var finalLocalMtime = localMtime
                var finalRemoteMtime = remoteMtime
                val takeLocal = when {
                    localChanged && !remoteChanged -> true
                    remoteChanged && !localChanged -> false
                    localChanged && remoteChanged -> localMtime > remoteMtime
                    else -> null
                }
                when (takeLocal) {
                    true -> {
                        val pasted = paste(local, path)
                        // The local content was not modified by the transfer; reuse its digest.
                        finalHash = localDigest ?: localHash(local) ?: finalHash
                        finalLocalMtime = local.lastModified()
                        finalRemoteMtime = pasted.lastModified
                    }

                    false -> {
                        copy(remote, local)
                        // The copy reproduced the remote bytes; reuse the remote digest.
                        finalHash = remoteDigest ?: localHash(local) ?: finalHash
                        finalLocalMtime = local.lastModified()
                        finalRemoteMtime = remote.lastModified
                    }

                    null -> {
                        if (finalHash == null) finalHash = remoteHash(remote)
                    }
                }
                newMap[path] = SyncManifest.Entry(
                    finalLocalMtime,
                    finalRemoteMtime,
                    local.length(),
                    false,
                    finalHash,
                )
            }

            // Single-sided files are necessarily new: a manifest-tracked entry that
            // went missing on one side was already resolved by the deletion handling
            // above, so there is nothing left to guard against here.
            local != null -> {
                val pasted = paste(local, path)
                newMap[path] = SyncManifest.Entry(
                    local.lastModified(),
                    pasted.lastModified,
                    local.length(),
                    false,
                    localHash(local),
                )
            }

            remote != null -> {
                val target = resolveLocal(path)
                copy(remote, target)
                newMap[path] = SyncManifest.Entry(
                    target.lastModified(),
                    remote.lastModified,
                    target.length(),
                    false,
                    localHash(target),
                )
            }
        }
    }

    private suspend fun paste(
        local: File,
        relativePath: String,
    ): StorageDocument {
        val parent = relativePath.substringBeforeLast('/', "")
        val dirUri =
            if (parent.isEmpty()) {
                externalRimeDir
            } else {
                StorageAccess.mkdirp(
                    externalRimeDir,
                    *parent.split('/').toTypedArray(),
                ).uri
            }
        return StorageAccess.pasteLocalFile(local.path, dirUri, local.name, DEFAULT_MIME, overwrite = true)
    }

    private suspend fun copy(
        remote: StorageDocument,
        target: File,
    ) {
        target.parentFile?.mkdirs()
        StorageAccess.readFile(remote.uri) { input ->
            val atomic = AtomicFile(target)
            val out = atomic.startWrite()
            try {
                input.copyTo(out)
                atomic.finishWrite(out)
            } catch (e: Exception) {
                atomic.failWrite(out)
                throw e
            }
        }
    }

    private suspend fun remoteMkdir(relativePath: String) {
        StorageAccess.mkdirp(
            externalRimeDir,
            *relativePath.split('/').toTypedArray(),
        )
    }

    private fun walkLocal(root: File): Map<String, File> {
        if (!root.exists()) return emptyMap()
        return root
            .walkTopDown()
            .filter { it != root }
            .associateBy { it.toRelativeString(root) }
    }

    private fun isUnchanged(
        path: String,
        localMap: Map<String, File>,
        remoteMap: Map<String, StorageDocument>,
        entry: SyncManifest.Entry,
    ): Boolean {
        val local = localMap[path]?.takeIf { it.exists() } ?: return false
        val remote = remoteMap[path] ?: return false
        val localIsDir = local.isDirectory
        if (localIsDir != remote.isDir || localIsDir != entry.isDir) return false
        if (localIsDir) return true
        if (local.length() != entry.length || remote.length != entry.length) return false
        if (abs(local.lastModified() - entry.localMtime) > MTIME_TOLERANCE) return false
        return abs(remote.lastModified - entry.remoteMtime) <= MTIME_TOLERANCE
    }

    private fun resolveLocal(relativePath: String): File {
        val normalized = relativePath.trim().trimStart('/').removePrefix("./")
        require(normalized.isNotEmpty() && normalized.split('/').none { it.isEmpty() || it == ".." }) {
            "Unsafe sync path: $relativePath"
        }
        val root = userDataDir.canonicalFile
        val resolved = File(root, normalized).canonicalFile
        require(resolved.path.startsWith(root.path + File.separator)) {
            "Sync path escapes root: $relativePath"
        }
        return resolved
    }
}
