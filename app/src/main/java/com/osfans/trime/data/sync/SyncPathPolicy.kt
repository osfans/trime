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
 * import peer `sync/<id>/` folders only, and export this device's `sync/<id>/` only.
 */
object SyncPathPolicy {
    fun readOwnInstallationId(): String? = readInstallationId(localInstallationFile())

    fun readInstallationId(text: String): String? {
        val node = Yaml.parseToYamlNode(text)
        val id = node.mapping?.get("installation_id")?.string?.trim().orEmpty()
        return id.takeIf { it.isNotEmpty() }
    }

    fun ownSyncPrefix(ownId: String): String = SyncRelativePath.normalize("sync/$ownId")

    /**
     * @param relativePath path relative to the external / user-data root
     *   (e.g. `installation.yaml`, `sync/peer-id/foo.userdb.txt`)
     * @param ownId this device's installation_id, or null if unknown
     */
    fun shouldImport(
        relativePath: String,
        ownId: String?,
    ): Boolean {
        val normalized =
            runCatching { SyncRelativePath.normalize(relativePath) }.getOrElse { return false }
        return !shouldPreserveLocal(normalized, ownId)
    }

    /**
     * Whether a local file must survive orphan cleanup. [relativePath] is already
     * normalized. True for [DataManager.INSTALLATION_FILE_NAME] and this device's
     * `sync/<ownId>/` tree when [ownId] is known.
     */
    fun shouldPreserveLocal(
        relativePath: String,
        ownId: String?,
    ): Boolean {
        if (relativePath == DataManager.INSTALLATION_FILE_NAME) return true
        if (ownId.isNullOrEmpty()) return false
        return isOwnSyncPath(relativePath, ownId)
    }

    private fun localInstallationFile(): File = File(DataManager.userDataDir, DataManager.INSTALLATION_FILE_NAME)

    private fun readInstallationId(file: File): String? {
        if (!file.isFile) return null
        return runCatching { readInstallationId(file.readText()) }
            .onFailure { Timber.w(it, "Failed to read installation_id from ${file.path}") }
            .getOrNull()
    }

    private fun isOwnSyncPath(
        normalizedRelativePath: String,
        ownId: String,
    ): Boolean {
        val prefix = runCatching { ownSyncPrefix(ownId) }.getOrElse { return false }
        return normalizedRelativePath == prefix || normalizedRelativePath.startsWith("$prefix/")
    }
}
