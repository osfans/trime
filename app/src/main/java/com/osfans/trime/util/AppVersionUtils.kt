package com.osfans.trime.util

import android.net.Uri
import androidx.preference.Preference
import com.blankj.utilcode.util.AppUtils
import com.osfans.trime.ime.core.Preferences

object AppVersionUtils {
    /**
     * Compare the application's version name
     * to check if this version is a newer/older version.
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

    fun Preference.writeLibraryVersionToSummary(versionCode: String) {
        val commitHash = if (versionCode.contains("-g")) {
            versionCode.replace("^(.*-g)([0-9a-f]+)(.*)$".toRegex(), "$2")
        } else {
            versionCode.replace("^([^-]*)(-.*)$".toRegex(), "$1")
        }
        this.summary = versionCode
        val intent = this.intent
        intent.data = Uri.withAppendedPath(intent.data, "commits/$commitHash")
        this.intent = intent
    }
}
