package com.osfans.trime.util

import com.blankj.utilcode.util.AppUtils
import com.osfans.trime.ime.core.Preferences

object AppVersionUtils {
    /**
     * Compare the application's version name
     * to check if this version is a newer version.
     *
     * @param prefs the [Preferences] instance.
     */
    fun isDifferentVersion(prefs: Preferences): Boolean {
        val currentVersionName = AppUtils.getAppVersionName()
        val lastVersionName = prefs.general.lastVersionName
        return !currentVersionName.contentEquals(lastVersionName).also {
            prefs.general.lastVersionName = currentVersionName
        }
    }
}