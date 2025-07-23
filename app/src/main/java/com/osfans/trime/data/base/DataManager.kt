// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.appContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DataManager {
    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"

    private const val DATA_CHECKSUMS_NAME = "checksums.json"

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

    private fun AssetManager.dataChecksums(): DataChecksums =
        open(DATA_CHECKSUMS_NAME)
            .bufferedReader()
            .use { it.readText() }
            .let { deserializeDataChecksums(it) }

    private val prefs by lazy { AppPrefs.defaultInstance() }

    val defaultDataDir = File(Environment.getExternalStorageDirectory(), "rime")

    val sharedDataDir = File(appContext.getExternalFilesDir(null), "shared").also { it.mkdirs() }

    val userDataDir
        get() = File(prefs.profile.userDataDir.getValue()).also { it.mkdirs() }

    val prebuiltDataDir = File(sharedDataDir, "build")
    val stagingDir get() = File(userDataDir, "build")

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

    fun sync() =
        lock.withLock {
            val oldChecksumsFile = File(dataDir, DATA_CHECKSUMS_NAME)
            val oldChecksums =
                oldChecksumsFile
                    .runCatching { deserializeDataChecksums(bufferedReader().use { it.readText() }) }
                    .getOrElse { DataChecksums("", emptyMap()) }

            val newChecksums = appContext.assets.dataChecksums()

            DataDiff.diff(oldChecksums, newChecksums).sortedByDescending { it.ordinal }.forEach {
                Timber.d("Diff: $it")
                when (it) {
                    is DataDiff.CreateFile,
                    is DataDiff.UpdateFile,
                    -> {
                        val destPath = sharedDataDir.resolveSibling(it.path).absolutePath
                        ResourceUtils.copyFile(it.path, destPath)
                    }
                    is DataDiff.DeleteDir,
                    is DataDiff.DeleteFile,
                    -> FileUtils.delete(sharedDataDir.resolve(it.path.substringAfterLast('/'))).getOrThrow()
                }
            }

            ResourceUtils.copyFile(DATA_CHECKSUMS_NAME, dataDir.resolve(DATA_CHECKSUMS_NAME).absolutePath)

            // 创建 default.custom.yaml 并用空方案列表覆盖 default.yaml 默认的方案列表
            // TODO: 内置明月拼音等方案，提供最小开箱即用环境
            runCatching {
                val defaultCustom = File(userDataDir, DEFAULT_CUSTOM_FILE_NAME)
                if (defaultCustom.createNewFile()) {
                    defaultCustom.writeText(
                        """
                        patch:
                            schema_list: []
                        """.trimIndent(),
                    )
                }
            }.getOrElse { Timber.e(it, "Failed to create default.custom.yaml") }

            Timber.d("Synced!")
        }
}
