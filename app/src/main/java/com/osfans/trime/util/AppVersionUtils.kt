package com.osfans.trime.util

import android.content.Context
import androidx.preference.PreferenceManager

object AppVersionUtils {
    fun getRawVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "undefined"
        }
    }

    fun isDifferentVersion(context: Context): Boolean {
        val version = getRawVersionName(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prefVersion = prefs.getString("version_name", "")
        return !version.contentEquals(prefVersion).also {
            prefs.edit().putString("version_name", version).apply()
        }
    }
}