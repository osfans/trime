package com.osfans.trime.util

import com.osfans.trime.ime.core.Preferences
import java.io.File

object DataUtils {
    private val prefs get() = Preferences.defaultInstance()

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
