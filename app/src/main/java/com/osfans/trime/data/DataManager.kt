package com.osfans.trime.data

import java.io.File

object DataManager {
    private val prefs get() = AppPrefs.defaultInstance()

    @JvmStatic
    fun getDataDir(child: String = ""): String {
        return if (File(prefs.conf.sharedDataDir, child).exists()) {
            File(prefs.conf.sharedDataDir, child).absolutePath
        } else {
            File(prefs.conf.userDataDir, child).absolutePath
        }
    }
}
