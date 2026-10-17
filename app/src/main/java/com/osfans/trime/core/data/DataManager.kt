// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data

import android.content.res.AssetManager
import android.os.Build
import com.osfans.trime.TrimeApplication
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.appContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val DATA_CHECKSUMS_NAME = "checksums.json"
private const val DIRECT_BOOT_DATA_CHECKSUMS_NAME = "checksums.direct-boot.json"

internal fun resolveDataChecksumsName(isDirectBootMode: Boolean): String = if (isDirectBootMode) DIRECT_BOOT_DATA_CHECKSUMS_NAME else DATA_CHECKSUMS_NAME

/**
 * Resolve [name] under [parent].
 *
 * Returns null when [parent] is null, cannot be created, or the resulting directory
 * is not writable. Callers must not cache a failed result permanently; retry when
 * storage may have become available (for example after reboot).
 */
internal fun resolveWritableChildDir(
    parent: File?,
    name: String,
): File? {
    if (parent == null) return null
    if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\\' in name) return null
    return try {
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) return null
        val canonicalParent = parent.canonicalFile
        if (!canonicalParent.isDirectory || !canonicalParent.canWrite()) return null

        val requested = File(canonicalParent, name)
        if (!requested.exists() && !requested.mkdir() && !requested.isDirectory) return null
        requested.canonicalFile.takeIf {
            it == requested.absoluteFile &&
                it.parentFile == canonicalParent &&
                it.isDirectory &&
                it.canWrite()
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

/**
 * Select the root that backs the runtime data hierarchy.
 *
 * During direct boot the normal app-scoped external directory is not guaranteed to be
 * available, so the hierarchy must live in device-protected storage. After unlock we keep
 * using app-scoped external storage and preserve a null result so callers can retry when
 * that storage becomes available later.
 */
internal fun selectRuntimeStorageParent(
    externalFilesDir: File?,
    deviceProtectedFilesDir: File,
    isDirectBootMode: Boolean,
): File? = if (isDirectBootMode) deviceProtectedFilesDir else externalFilesDir

object DataManager {
    const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"
    const val USER_CONFIG_FILE_NAME = "user.yaml"
    const val INSTALLATION_FILE_NAME = "installation.yaml"

    val POST_SCHEMA_DEPLOY_EXPORT_FILES =
        listOf(
            DEFAULT_CUSTOM_FILE_NAME,
            USER_CONFIG_FILE_NAME,
        )

    private const val SHARED_DIR_NAME = "shared"
    private const val USER_DIR_NAME = "rime"

    private val lock = ReentrantLock()

    private val json by lazy { Json }

    private fun deserializeDataChecksums(raw: String): DataChecksums = json.decodeFromString<DataChecksums>(raw)

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    private val dataDir: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Timber.d("Using device protected storage")
            appContext.createDeviceProtectedStorageContext().dataDir
        } else {
            File(appContext.applicationInfo.dataDir)
        }

    private fun AssetManager.dataChecksums(): DataChecksums = open(DATA_CHECKSUMS_NAME)
        .bufferedReader()
        .use { it.readText() }
        .let { deserializeDataChecksums(it) }

    @Volatile
    private var cachedSharedDataDir: File? = null

    @Volatile
    private var cachedUserDataDir: File? = null

    private fun runtimeStorageParent(): File? = try {
        val application = TrimeApplication.getInstance()
        val context = application.directBootAwareContext
        selectRuntimeStorageParent(
            externalFilesDir = if (application.isDirectBootMode) null else context.getExternalFilesDir(null),
            deviceProtectedFilesDir = context.filesDir,
            isDirectBootMode = application.isDirectBootMode,
        )
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private fun resolveAppScopedDir(
        cached: () -> File?,
        name: String,
        store: (File?) -> Unit,
    ): File? = lock.withLock {
        val resolved = resolveWritableChildDir(runtimeStorageParent(), name)
        if (resolved == null) {
            store(null)
            return@withLock null
        }

        val current = cached()
        val currentIsResolved =
            try {
                current != null &&
                    current.isDirectory &&
                    current.canWrite() &&
                    current.canonicalFile == resolved
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
        if (currentIsResolved) return@withLock current

        store(resolved)
        resolved
    }

    /** Writable shared assets dir, or null when external app files are not ready yet. */
    fun resolvedSharedDataDir(): File? = resolveAppScopedDir({ cachedSharedDataDir }, SHARED_DIR_NAME) { cachedSharedDataDir = it }

    /** Writable Rime user dir, or null when external app files are not ready yet. */
    fun resolvedUserDataDir(): File? = resolveAppScopedDir({ cachedUserDataDir }, USER_DIR_NAME) { cachedUserDataDir = it }

    val sharedDataDir: File
        get() =
            resolvedSharedDataDir()
                ?: error("Shared data dir is not available")

    /** App-scoped path used by Rime at runtime. */
    val userDataDir: File
        get() =
            resolvedUserDataDir()
                ?: error("User data dir is not available")

    val prebuiltDataDir: File
        get() = File(sharedDataDir, "build")
    val stagingDir get() = File(userDataDir, "build")

    /** Only permission to attempt recovery; native dictionary loading must still validate it. */
    fun hasDeployedResources(): Boolean = runCatching {
        File(sharedDataDir, "default.yaml").isFile &&
            File(resolveDeployedResourcePath("default")).isFile &&
            listOf(stagingDir, prebuiltDataDir).any { dir ->
                dir.listFiles()?.any { it.name.endsWith(".schema.yaml") && it.isFile && it.canRead() } == true
            }
    }.getOrDefault(false)

    /**
     * Return the absolute path of the compiled config file
     * based on given resource id.
     *
     * @param resourceId usually equals the config file name without the extension
     * @return the absolute path of the compiled config file
     */
    @JvmStatic
    fun resolveDeployedResourcePath(resourceId: String): String {
        val defaultPath = File(stagingDir, "$resourceId.yaml")
        if (!defaultPath.exists()) {
            val fallbackPath = File(prebuiltDataDir, "$resourceId.yaml")
            if (fallbackPath.exists()) return fallbackPath.absolutePath
        }
        return defaultPath.absolutePath
    }

    fun sync() = lock.withLock {
        // Direct-boot and normal mode can resolve to different roots. Keep separate
        // descriptors so syncing one hierarchy never makes the other look current.
        val checksumsName = resolveDataChecksumsName(TrimeApplication.getInstance().isDirectBootMode)
        val destChecksumsFile = File(dataDir, checksumsName)
        val oldChecksums =
            destChecksumsFile
                .runCatching { deserializeDataChecksums(bufferedReader().use { it.readText() }) }
                .getOrElse { DataChecksums("", emptyMap()) }

        val newChecksums = appContext.assets.dataChecksums()
        val sharedDir = sharedDataDir

        DataResourceInstaller.install(
            sharedDir,
            DataDiff.diff(oldChecksums, newChecksums) { path ->
                resolveSharedAssetDestination(sharedDir, path).isFile
            },
            copyAsset = { path, destination -> ResourceUtils.copyFile(path, destination.absolutePath).getOrThrow() },
            publishChecksums = {
                ResourceUtils.copyFile(DATA_CHECKSUMS_NAME, destChecksumsFile.absolutePath).getOrThrow()
            },
        )

        UserDataManager.ensureDefaultCustomConfig()

        Timber.d("Synced!")
    }
}
