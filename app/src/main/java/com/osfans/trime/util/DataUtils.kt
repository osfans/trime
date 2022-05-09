package com.osfans.trime.util

import com.osfans.trime.data.AppPrefs
import java.io.File

object DataUtils {
    private val prefs get() = AppPrefs.defaultInstance()

    @JvmStatic
    val sharedDataDir: String get() = prefs.conf.sharedDataDir

    @JvmStatic
    val userDataDir: String get() = prefs.conf.userDataDir

    @JvmStatic
    fun getAssetsDir(childdir: String = ""): String {
        val path = File(sharedDataDir, childdir).path
        return if (File(path).exists()) {
            path
        } else {
            File(userDataDir, childdir).path
        }
    }
}
