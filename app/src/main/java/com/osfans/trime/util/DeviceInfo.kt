/**
 * Adapted from [fcitx5-android/DeviceInfo.kt](https://github.com/fcitx5-android/fcitx5-android/blob/e44c1c7/app/src/main/java/org/fcitx/fcitx5/android/utils/DeviceInfo.kt)
 */
package com.osfans.trime.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.osfans.trime.BuildConfig

// Adapted from https://gist.github.com/hendrawd/01f215fd332d84793e600e7f82fc154b
object DeviceInfo {
    fun get(context: Context) =
        buildString {
            appendLine("--------- Device Info")
            appendLine("OS Name: ${Build.DISPLAY}")
            appendLine("OS Version: ${System.getProperty("os.version")} (${Build.VERSION.INCREMENTAL})")
            appendLine("OS API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Model (product): ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Tags: ${Build.TAGS}")
            val metrics = context.resources.displayMetrics
            appendLine("Screen Size: ${metrics.widthPixels} x ${metrics.heightPixels}")
            appendLine("Screen Density: ${metrics.density}")
            appendLine(
                "Screen orientation: ${
                    when (context.resources.configuration.orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> "Portrait"
                        Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
                        Configuration.ORIENTATION_UNDEFINED -> "Undefined"
                        else -> "Unknown"
                    }
                }",
            )
            appendLine("--------- Build Info")
            appendLine("Package Name: ${BuildConfig.APPLICATION_ID}")
            appendLine("Builder: ${Const.builder}")
            appendLine("Version Code: ${BuildConfig.VERSION_CODE}")
            appendLine("Version Name: ${Const.displayVersionName}")
            appendLine("Build Time: ${iso8601UTCDateTime(Const.buildTimestamp)}")
            appendLine("Build Git Hash: ${Const.buildCommitHash}")
        }
}
