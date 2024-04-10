// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.WeakHashSet
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

    private fun deserializeDataChecksums(raw: String): DataChecksums {
        return json.decodeFromString<DataChecksums>(raw)
    }

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    private val dataDir: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Timber.d("Using device protected storage")
            appContext.createDeviceProtectedStorageContext().dataDir
        } else {
            File(appContext.applicationInfo.dataDir)
        }

    private fun AssetManager.dataChecksums(): DataChecksums {
        return open(DATA_CHECKSUMS_NAME)
            .bufferedReader()
            .use { it.readText() }
            .let { deserializeDataChecksums(it) }
    }

    private val prefs get() = AppPrefs.defaultInstance()

    private val onDataDirChangeListeners = WeakHashSet<OnDataDirChangeListener>()

    fun interface OnDataDirChangeListener {
        fun onDataDirChange()
    }

    fun addOnChangedListener(listener: OnDataDirChangeListener) {
        onDataDirChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnDataDirChangeListener) {
        onDataDirChangeListeners.remove(listener)
    }

    fun dirFireChange() {
        onDataDirChangeListeners.forEach { it.onDataDirChange() }
    }

    @JvmStatic
    val sharedDataDir
        get() = File(AppPrefs.Profile.getAppShareDir())

    @JvmStatic
    val userDataDir
        get() = File(AppPrefs.Profile.getAppUserDir())

    /**
     * Return the absolute path of the compiled config file
     * based on given resource id.
     *
     * @param resourceId usually equals the config file name without the extension
     * @return the absolute path of the compiled config file
     */
    @JvmStatic
    fun resolveDeployedResourcePath(resourceId: String): String {
        val stagingDir = File(userDataDir, "build")
        val prebuiltDataDir = File(sharedDataDir, "build")

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
                    -> ResourceUtils.copyFile(it.path, sharedDataDir, "rime/")
                    is DataDiff.DeleteDir,
                    is DataDiff.DeleteFile,
                    -> FileUtils.delete(sharedDataDir.resolve(it.path.removePrefix("rime/"))).getOrThrow()
                }
            }

            ResourceUtils.copyFile(DATA_CHECKSUMS_NAME, dataDir)

            // FIXME：缺失 default.custom.yaml 会导致方案列表为空
            File(sharedDataDir, DEFAULT_CUSTOM_FILE_NAME).let {
                if (!it.exists()) {
                    Timber.d("Creating empty default.custom.yaml")
                    it.bufferedWriter().use { w -> w.write("") }
                }
            }

            Timber.d("Synced!")
        }
}
