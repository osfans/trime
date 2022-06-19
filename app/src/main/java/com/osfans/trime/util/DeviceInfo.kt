/**
 * Adapted from [fcitx5-android/DeviceInfo.kt](https://github.com/fcitx5-android/fcitx5-android/blob/e44c1c7/app/src/main/java/org/fcitx/fcitx5/android/utils/DeviceInfo.kt)
 */
package com.osfans.trime.util

import android.content.Context
import android.content.res.Configuration
import com.blankj.utilcode.util.ScreenUtils
import com.osfans.trime.BuildConfig

// Adapted from https://gist.github.com/hendrawd/01f215fd332d84793e600e7f82fc154b
object DeviceInfo {
    fun get(context: Context) =
        buildString {
            appendLine("App Package Name: ${BuildConfig.APPLICATION_ID}")
            appendLine("App Version Name: ${BuildConfig.VERSION_NAME}")
            appendLine("App Version Code: ${BuildConfig.VERSION_CODE}")
            appendLine("OS Name: ${android.os.Build.DISPLAY}")
            appendLine("OS Version: ${System.getProperty("os.version")} (${android.os.Build.VERSION.INCREMENTAL})")
            appendLine("OS API Level: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Device: ${android.os.Build.DEVICE}")
            appendLine("Model (product): ${android.os.Build.MODEL} (${android.os.Build.PRODUCT})")
            appendLine("Manufacturer: ${android.os.Build.MANUFACTURER}")
            appendLine("Tags: ${android.os.Build.TAGS}")
            appendLine("Screen Size: ${ScreenUtils.getScreenWidth()} x ${ScreenUtils.getScreenHeight()}")
            appendLine("Screen Density: ${ScreenUtils.getScreenDensity()}")
            appendLine(
                "Screen orientation: ${
                when (context.resources.configuration.orientation) {
                    Configuration.ORIENTATION_PORTRAIT -> "Portrait"
                    Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
                    Configuration.ORIENTATION_UNDEFINED -> "Undefined"
                    else -> "Unknown"
                }
                }"
            )
        }
}
