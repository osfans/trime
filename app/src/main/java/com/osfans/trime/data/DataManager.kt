package com.osfans.trime.data

import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ResourceUtils
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.util.Const
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.appContext
import com.osfans.trime.util.errorRuntime
import com.osfans.trime.util.extract
import com.osfans.trime.util.withTempDir
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DataManager {
    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"
    private val prefs get() = AppPrefs.defaultInstance()

    val defaultDataDirectory = File(PathUtils.getExternalStoragePath(), "rime")

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
        get() = File(prefs.profile.sharedDataDir)

    @JvmStatic
    val userDataDir
        get() = File(prefs.profile.userDataDir)

    sealed class Diff {
        object New : Diff()

        object Update : Diff()

        object Keep : Diff()
    }

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

    private fun diff(
        old: String,
        new: String,
    ): Diff {
        return when {
            old.isBlank() -> Diff.New
            !new.contentEquals(old) -> Diff.Update
            else -> Diff.Keep
        }
    }

    @JvmStatic
    fun sync() {
        val newHash = Const.buildCommitHash
        val oldHash = prefs.internal.lastBuildGitHash

        diff(oldHash, newHash).run {
            Timber.d("Diff: $this")
            when (this) {
                is Diff.New ->
                    ResourceUtils.copyFileFromAssets(
                        "rime",
                        sharedDataDir.absolutePath,
                    )
                is Diff.Update ->
                    ResourceUtils.copyFileFromAssets(
                        "rime",
                        sharedDataDir.absolutePath,
                    )
                is Diff.Keep -> {}
            }
        }

        // FIXME：缺失 default.custom.yaml 会导致方案列表为空
        with(File(sharedDataDir, DEFAULT_CUSTOM_FILE_NAME)) {
            val customDefault = this
            if (!customDefault.exists()) {
                Timber.d("Creating empty default.custom.yaml ...")
                customDefault.createNewFile()
            }
        }

        Timber.i("Synced!")
    }

    private val json = Json { prettyPrint = true }

    @Serializable
    data class Metadata(
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val exportTime: Long,
    )

    private fun writeFileTree(
        srcDir: File,
        destPrefix: String,
        dest: ZipOutputStream,
    ) {
        dest.putNextEntry(ZipEntry("$destPrefix/"))
        srcDir.walkTopDown().forEach { f ->
            val related = f.relativeTo(srcDir)
            if (related.path != "") {
                if (f.isDirectory) {
                    dest.putNextEntry(ZipEntry("$destPrefix/${related.path}/"))
                } else if (f.isFile) {
                    dest.putNextEntry(ZipEntry("$destPrefix/${related.path}"))
                    f.inputStream().use { it.copyTo(dest) }
                }
            }
        }
    }

    private val sharedPrefsDir = File(appContext.applicationInfo.dataDir, "shared_prefs")
    private val dataBasesDir = File(appContext.applicationInfo.dataDir, "databases")
    private val symbolHistoryFile = appContext.filesDir.resolve(SymbolHistory.FILE_NAME)

    @OptIn(ExperimentalSerializationApi::class)
    fun export(
        dest: OutputStream,
        timestamp: Long = System.currentTimeMillis(),
    ) = runCatching {
        ZipOutputStream(dest.buffered()).use { zipStream ->
            // shared_prefs
            writeFileTree(sharedPrefsDir, "shared_prefs", zipStream)
            // databases
            writeFileTree(dataBasesDir, "databases", zipStream)
            // symbol_history
            zipStream.putNextEntry(ZipEntry(SymbolHistory.FILE_NAME))
            symbolHistoryFile.inputStream().use { it.copyTo(zipStream) }
            // metadata
            zipStream.putNextEntry(ZipEntry("metadata.json"))
            val metadata =
                Metadata(
                    BuildConfig.APPLICATION_ID,
                    BuildConfig.VERSION_CODE,
                    Const.displayVersionName,
                    timestamp,
                )
            json.encodeToStream(metadata, zipStream)
            zipStream.closeEntry()
        }
    }

    private fun copyDir(
        source: File,
        target: File,
    ) {
        val exists = source.exists()
        val isDir = source.isDirectory
        if (exists && isDir) {
            source.copyRecursively(target, overwrite = true)
        } else {
            source.toString()
            Timber.w("Cannot import user data: path='${source.path}', exists=$exists, isDir=$isDir")
        }
    }

    fun import(src: InputStream) =
        runCatching {
            ZipInputStream(src).use { zipStream ->
                withTempDir { tempDir ->
                    val extracted = zipStream.extract(tempDir)
                    val metadataFile =
                        extracted.find { it.name == "metadata.json" }
                            ?: errorRuntime(R.string.exception_app_data_metadata)
                    val metadata = json.decodeFromString<Metadata>(metadataFile.readText())
                    if (metadata.packageName != BuildConfig.APPLICATION_ID) {
                        errorRuntime(R.string.exception_app_data_package_name_mismatch)
                    }
                    copyDir(File(tempDir, "shared_prefs"), sharedPrefsDir)
                    copyDir(File(tempDir, "databases"), dataBasesDir)
                    copyDir(File(tempDir, "symbol_history"), appContext.filesDir)
                    metadata
                }
            }
        }
}
