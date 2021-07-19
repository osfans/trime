package com.osfans.trime.util

import android.content.Context
import android.net.Uri
import androidx.preference.Preference
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

    var Preference?.libVersion: String
        get() = this?.summary as String
        set(v) {
            val commit = if (v.contains("-g")) {
                v.replace("^(.*-g)([0-9a-f]+)(.*)$".toRegex(), "$2")
            } else {
                v.replace("^([^-]*)(-.*)$".toRegex(), "$1")
            }
            this?.summary = v
            val intent = this!!.intent
            intent.data = Uri.withAppendedPath(intent.data, "commits/$commit")
            this.intent = intent
        }
}