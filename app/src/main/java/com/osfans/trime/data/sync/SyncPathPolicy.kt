// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
import timber.log.Timber
import java.io.File

/**
 * Copy rules for EXTERNAL_SYNC: never import [DataManager.INSTALLATION_FILE_NAME],
 * import peer `<syncDir>/<id>/` folders only, and export this device's `<syncDir>/<id>/` only.
 */
object SyncPathPolicy {
    const val DEFAULT_SYNC_DIR = "sync"

    fun readOwnInstallationId(): String? = readInstallationId(localInstallationFile())

    fun readOwnSyncDir(): String = readSyncDir(localInstallationFile())

    fun readInstallationId(text: String): String? {
        val node = Yaml.parseToYamlNode(text)
        val id = node.mapping?.get("installation_id")?.string?.trim().orEmpty()
        return id.takeIf { it.isNotEmpty() }
    }

    fun readSyncDir(text: String): String {
        val node = Yaml.parseToYamlNode(text)
        val dir = node.mapping?.get("sync_dir")?.string?.trim().orEmpty()
        return cleanSyncDir(dir)
    }

    /**
     * Filesystem directory librime writes dumps into (`sync_dir`, or
     * `[userDataDir]/sync` when unset). Absolute [syncDir] is used as-is.
     */
    fun localSyncRoot(
        syncDir: String,
        userDataDir: File,
    ): File {
        val cleaned = cleanSyncDir(syncDir)
        val file = File(cleaned)
        return if (file.isAbsolute) file else File(userDataDir, cleaned)
    }

    fun localOwnSyncDir(
        ownId: String,
        syncDir: String,
        userDataDir: File,
    ): File = File(localSyncRoot(syncDir, userDataDir), ownId)

    /**
     * Tree-relative parent of installation folders on the mirrored user-data
     * tree. Absolute [syncDir] under [userDataDir] is relativized; otherwise the
     * last path segment is used.
     */
    fun treeRelativeSyncDir(
        syncDir: String,
        userDataDir: File? = null,
    ): String {
        val cleaned = cleanSyncDir(syncDir)
        val file = File(cleaned)
        if (file.isAbsolute) {
            if (userDataDir != null) {
                relativizeIfContained(userDataDir, file)?.let { rel ->
                    return runCatching { SyncRelativePath.normalize(rel) }.getOrDefault(DEFAULT_SYNC_DIR)
                }
            }
            val name = file.name.ifEmpty { DEFAULT_SYNC_DIR }
            return runCatching { SyncRelativePath.normalize(name) }.getOrDefault(DEFAULT_SYNC_DIR)
        }
        return runCatching { SyncRelativePath.normalize(cleaned) }.getOrDefault(DEFAULT_SYNC_DIR)
    }

    fun ownSyncPrefix(
        ownId: String,
        syncDir: String = DEFAULT_SYNC_DIR,
        userDataDir: File? = null,
    ): String = SyncRelativePath.normalize("${treeRelativeSyncDir(syncDir, userDataDir)}/$ownId")

    /**
     * @param relativePath path relative to the external / user-data root
     *   (e.g. `installation.yaml`, `sync/peer-id/foo.userdb.txt`)
     * @param ownId this device's installation_id, or null if unknown
     * @param syncDir tree-relative parent of installation sync folders
     */
    fun shouldImport(
        relativePath: String,
        ownId: String?,
        syncDir: String = DEFAULT_SYNC_DIR,
    ): Boolean {
        val normalized =
            runCatching { SyncRelativePath.normalize(relativePath) }.getOrElse { return false }
        return !shouldPreserveLocal(normalized, ownId, syncDir)
    }

    /**
     * Whether a local file must survive orphan cleanup. [relativePath] is already
     * normalized. True for [DataManager.INSTALLATION_FILE_NAME] and this device's
     * `<syncDir>/<ownId>/` tree when [ownId] is known.
     */
    fun shouldPreserveLocal(
        relativePath: String,
        ownId: String?,
        syncDir: String = DEFAULT_SYNC_DIR,
    ): Boolean {
        if (relativePath == DataManager.INSTALLATION_FILE_NAME) return true
        if (ownId.isNullOrEmpty()) return false
        return isOwnSyncPath(relativePath, ownId, syncDir)
    }

    private fun localInstallationFile(): File = File(DataManager.userDataDir, DataManager.INSTALLATION_FILE_NAME)

    private fun readInstallationId(file: File): String? {
        if (!file.isFile) return null
        return runCatching { readInstallationId(file.readText()) }
            .onFailure { Timber.w(it, "Failed to read installation_id from ${file.path}") }
            .getOrNull()
    }

    private fun readSyncDir(file: File): String {
        if (!file.isFile) return DEFAULT_SYNC_DIR
        return runCatching { readSyncDir(file.readText()) }
            .onFailure { Timber.w(it, "Failed to read sync_dir from ${file.path}") }
            .getOrDefault(DEFAULT_SYNC_DIR)
    }

    private fun cleanSyncDir(syncDir: String): String {
        val cleaned = syncDir.trim().replace('\\', '/').trimEnd('/')
        return cleaned.ifEmpty { DEFAULT_SYNC_DIR }
    }

    private fun relativizeIfContained(
        root: File,
        path: File,
    ): String? = runCatching {
        val rel = path.canonicalFile.relativeTo(root.canonicalFile).path.replace('\\', '/')
        rel.takeIf { it.isNotEmpty() && !it.startsWith("../") && it != ".." }
    }.getOrNull()

    private fun isOwnSyncPath(
        normalizedRelativePath: String,
        ownId: String,
        syncDir: String,
    ): Boolean {
        val prefix = runCatching { ownSyncPrefix(ownId, syncDir) }.getOrElse { return false }
        return normalizedRelativePath == prefix || normalizedRelativePath.startsWith("$prefix/")
    }
}
