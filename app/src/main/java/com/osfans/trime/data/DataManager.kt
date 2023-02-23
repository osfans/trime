package com.osfans.trime.data

import android.os.Environment
import com.osfans.trime.util.appContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DataManager {
    private val prefs get() = AppPrefs.defaultInstance()
    private const val dataChecksumName = "checksums.json"

    val defaultDataDirectory = File(Environment.getExternalStorageDirectory().absolutePath, "rime")
    @JvmStatic
    val sharedDataDir = File(appContext.getExternalFilesDir(null), "rime")
    @JvmStatic
    val userDataDir = File(prefs.profile.userDataDir)
    @JvmStatic
    val buildDir = File(userDataDir, "build")

    private val externalFilesDir = appContext.getExternalFilesDir(null)
    private val dataDir = File(appContext.applicationInfo.dataDir)
    private val destChecksumFile = File(dataDir, dataChecksumName)

    private val lock = ReentrantLock()

    @Serializable
    data class DataChecksum(
        val sha256: String,
        val files: Map<String, String>
    )

    sealed class Diff {
        abstract val key: String
        abstract val order: Int

        data class New(override val key: String, val new: String) : Diff() {
            override val order: Int get() = 3
        }

        data class Update(override val key: String, val old: String, val new: String) : Diff() {
            override val order: Int get() = 2
        }

        data class Delete(override val key: String, val old: String) : Diff() {
            override val order: Int get() = 0
        }

        data class DeleteDir(override val key: String) : Diff() {
            override val order: Int get() = 1
        }
    }

    private fun deserialize(raw: String) = runCatching {
        Json.decodeFromString<DataChecksum>(raw)
    }

    private fun diff(old: DataChecksum, new: DataChecksum): List<Diff> =
        if (old.sha256 == new.sha256)
            listOf()
        else
            new.files.mapNotNull {
                when {
                    // empty sha256 -> dir
                    it.key !in old.files && it.value.isNotBlank() -> Diff.New(it.key, it.value)
                    old.files[it.key] != it.value ->
                        // if the new one is not a dir
                        if (it.value.isNotBlank())
                            Diff.Update(
                                it.key,
                                old.files.getValue(it.key),
                                it.value
                            )
                        else null
                    else -> null
                }
            }.toMutableList().apply {
                addAll(
                    old.files.filterKeys { it !in new.files }
                        .map {
                            if (it.value.isNotBlank())
                                Diff.Delete(it.key, it.value)
                            else
                                Diff.DeleteDir(it.key)
                        }
                )
            }

    @JvmStatic
    fun sync() = lock.withLock {
        val destDescriptor =
            destChecksumFile
                .takeIf { it.exists() && it.isFile }
                ?.runCatching { readText() }
                ?.getOrNull()
                ?.let { deserialize(it) }
                ?.getOrNull()
                ?: DataChecksum("", mapOf())

        val bundledDescriptor =
            appContext.assets
                .open(dataChecksumName)
                .bufferedReader()
                .use { it.readText() }
                .let { deserialize(it) }
                .getOrThrow()

        val d = diff(destDescriptor, bundledDescriptor).sortedBy { it.order }
        d.forEach {
            Timber.d("Diff: $it")
            when (it) {
                is Diff.Delete -> deleteFile(it.key)
                is Diff.DeleteDir -> deleteDir(it.key)
                is Diff.New -> copyFile(
                    externalFilesDir!!.absolutePath,
                    it.key
                )
                is Diff.Update -> copyFile(
                    externalFilesDir!!.absolutePath,
                    it.key
                )
            }
        }

        copyFile(dataDir.absolutePath, dataChecksumName)

        Timber.i("Synced!")
    }

    fun deleteAndSync() {
        externalFilesDir!!.deleteRecursively()
        sync()
    }

    private fun deleteFile(path: String) {
        val file = File(externalFilesDir, path)
        if (file.exists() && file.isFile)
            file.delete()
    }

    private fun deleteDir(path: String) {
        val dir = File(externalFilesDir, path)
        if (dir.exists() && dir.isDirectory)
            dir.deleteRecursively()
    }

    private fun copyFile(dest: String, filename: String) {
        with(appContext.assets) {
            open(filename).use { i ->
                File(dest, filename)
                    .also { it.parentFile?.mkdirs() }
                    .outputStream().use { o ->
                        i.copyTo(o)
                        Unit
                    }
            }
        }
    }
}
