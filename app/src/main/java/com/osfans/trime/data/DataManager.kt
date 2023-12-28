package com.osfans.trime.data

import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ResourceUtils
import com.osfans.trime.util.Const
import timber.log.Timber
import java.io.File

object DataManager {
    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"
    private val prefs get() = AppPrefs.defaultInstance()

    val defaultDataDirectory = File(PathUtils.getExternalStoragePath(), "rime")

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
}
