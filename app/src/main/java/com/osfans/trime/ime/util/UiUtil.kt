package com.osfans.trime.ime.util

import android.content.Context
import android.content.res.Configuration
import com.osfans.trime.data.AppPrefs.Companion.defaultInstance
import timber.log.Timber

object UiUtil {
    fun isDarkMode(context: Context): Boolean {
        var isDarkMode = false
        if (defaultInstance().themeAndColor.autoDark) {
            val nightModeFlags =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        } else {
            Timber.i("auto dark off")
        }
        return isDarkMode
    }
}
