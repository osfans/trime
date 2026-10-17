/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.common

import android.content.Context
import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import com.osfans.trime.data.prefs.AppPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Six real-Android resource cases invoked only by the explicit pilot mode; candidate not executed. */
internal object PublicWindowPilotConfigurationAssertions {
    fun verify(context: Context): JSONArray {
        val original = Configuration(context.resources.configuration)
        val systemNight = context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val rows = JSONArray()
        for ((width, height) in listOf(377 to 633, 633 to 377)) {
            val controlled = context.createConfigurationContext(
                Configuration(original).apply {
                    fontScale = 2f
                    setLocale(Locale.SIMPLIFIED_CHINESE)
                    densityDpi = original.densityDpi + 40
                    screenWidthDp = width
                    screenHeightDp = height
                    smallestScreenWidthDp = 377
                    orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                },
            )
            val before = Configuration(controlled.resources.configuration)
            for (mode in AppPrefs.Advanced.UiMode.values()) {
                val wrapped = PublicWindowPilotConfiguration.wrap(controlled, mode)
                val actual = wrapped.resources.configuration
                val expectedNight = when (mode) {
                    AppPrefs.Advanced.UiMode.AUTO -> systemNight
                    AppPrefs.Advanced.UiMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
                    AppPrefs.Advanced.UiMode.DARK -> Configuration.UI_MODE_NIGHT_YES
                }
                check((actual.uiMode and Configuration.UI_MODE_NIGHT_MASK) == expectedNight)
                check((actual.uiMode and Configuration.UI_MODE_TYPE_MASK) == (before.uiMode and Configuration.UI_MODE_TYPE_MASK))
                check(actual.fontScale == before.fontScale && actual.fontScale == 2f)
                check(actual.densityDpi == before.densityDpi)
                check(ConfigurationCompat.getLocales(actual) == ConfigurationCompat.getLocales(before))
                check(actual.screenWidthDp == width && actual.screenHeightDp == height)
                check(actual.smallestScreenWidthDp == before.smallestScreenWidthDp)
                check(actual.orientation == before.orientation)
                check(actual.layoutDirection == before.layoutDirection)
                check(actual.mcc == before.mcc && actual.mnc == before.mnc)
                check(actual.keyboard == before.keyboard && actual.keyboardHidden == before.keyboardHidden)
                check(actual.navigation == before.navigation && actual.navigationHidden == before.navigationHidden)
                check(actual.touchscreen == before.touchscreen)
                check(controlled.resources.configuration == before)
                check(context.resources.configuration == original)
                rows.put(
                    JSONObject().apply {
                        put("mode", mode.name)
                        put("width_dp", width)
                        put("height_dp", height)
                        put("font_scale", actual.fontScale)
                        put("night_bits", actual.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        put("input_context_unchanged", true)
                    },
                )
            }
        }
        return rows
    }
}
