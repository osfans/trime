/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.osfans.trime.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs

/** Public configuration-only subset of AppCompat's base-context strategy; owns no Window. */
internal object PublicWindowPilotConfiguration {
    fun wrap(
        base: Context,
        mode: AppPrefs.Advanced.UiMode,
        @StyleRes themeResId: Int = R.style.Theme_TrimePublicWindowPilot,
    ): ContextThemeWrapper {
        val baseConfig = base.resources.configuration
        // Preserve a custom Activity/display base overlay on older createConfigurationContext.
        // A delta (fontScale=0) also avoids pinning every current width/locale/font value forever.
        val referenceOverride = Configuration().apply {
            fontScale = 0f
            uiMode = -1
        }
        val reference = Configuration(base.createConfigurationContext(referenceOverride).resources.configuration)
        reference.uiMode = baseConfig.uiMode
        val configurationOverride = delta(reference, baseConfig)
        val night = when (mode) {
            AppPrefs.Advanced.UiMode.AUTO -> Configuration.UI_MODE_NIGHT_UNDEFINED
            AppPrefs.Advanced.UiMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            AppPrefs.Advanced.UiMode.DARK -> Configuration.UI_MODE_NIGHT_YES
        }
        configurationOverride.uiMode = (configurationOverride.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return ContextThemeWrapper(base, themeResId).apply {
            // Public API17; call once before this wrapper's resources/assets/theme are read.
            applyOverrideConfiguration(configurationOverride)
        }
    }

    @Suppress("DEPRECATION") // Public pre-24 locale field is required on supported Android 5.
    private fun delta(base: Configuration, changed: Configuration): Configuration = Configuration().apply {
        fontScale = 0f
        if (base.fontScale != changed.fontScale) fontScale = changed.fontScale
        if (base.mcc != changed.mcc) mcc = changed.mcc
        if (base.mnc != changed.mnc) mnc = changed.mnc
        if (Build.VERSION.SDK_INT >= 24) {
            Api24.copyLocales(base, changed, this)
        } else if (base.locale != changed.locale) {
            locale = changed.locale
        }
        if (base.touchscreen != changed.touchscreen) touchscreen = changed.touchscreen
        if (base.keyboard != changed.keyboard) keyboard = changed.keyboard
        if (base.keyboardHidden != changed.keyboardHidden) keyboardHidden = changed.keyboardHidden
        if (base.navigation != changed.navigation) navigation = changed.navigation
        if (base.navigationHidden != changed.navigationHidden) navigationHidden = changed.navigationHidden
        if (base.orientation != changed.orientation) orientation = changed.orientation
        for (mask in intArrayOf(
            Configuration.SCREENLAYOUT_SIZE_MASK,
            Configuration.SCREENLAYOUT_LAYOUTDIR_MASK,
            Configuration.SCREENLAYOUT_LONG_MASK,
        )) {
            if ((base.screenLayout and mask) != (changed.screenLayout and mask)) {
                screenLayout = screenLayout or (changed.screenLayout and mask)
            }
        }
        if (Build.VERSION.SDK_INT >= 23 &&
            (base.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK) !=
            (changed.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK)
        ) {
            screenLayout = screenLayout or (changed.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK)
        }
        if (Build.VERSION.SDK_INT >= 26) Api26.copyColorMode(base, changed, this)
        if ((base.uiMode and Configuration.UI_MODE_TYPE_MASK) != (changed.uiMode and Configuration.UI_MODE_TYPE_MASK)) {
            uiMode = uiMode or (changed.uiMode and Configuration.UI_MODE_TYPE_MASK)
        }
        if ((base.uiMode and Configuration.UI_MODE_NIGHT_MASK) != (changed.uiMode and Configuration.UI_MODE_NIGHT_MASK)) {
            uiMode = uiMode or (changed.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        }
        if (base.screenWidthDp != changed.screenWidthDp) screenWidthDp = changed.screenWidthDp
        if (base.screenHeightDp != changed.screenHeightDp) screenHeightDp = changed.screenHeightDp
        if (base.smallestScreenWidthDp != changed.smallestScreenWidthDp) smallestScreenWidthDp = changed.smallestScreenWidthDp
        if (base.densityDpi != changed.densityDpi) densityDpi = changed.densityDpi
        // Same compatibility boundary as current AppCompat: no private assets/window configuration.
    }

    @RequiresApi(24)
    private object Api24 {
        @DoNotInline
        fun copyLocales(base: Configuration, changed: Configuration, out: Configuration) {
            if (base.locales != changed.locales) out.setLocales(changed.locales)
        }
    }

    @RequiresApi(26)
    private object Api26 {
        @DoNotInline
        fun copyColorMode(base: Configuration, changed: Configuration, out: Configuration) {
            for (mask in intArrayOf(Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_MASK, Configuration.COLOR_MODE_HDR_MASK)) {
                if ((base.colorMode and mask) != (changed.colorMode and mask)) {
                    out.colorMode = out.colorMode or (changed.colorMode and mask)
                }
            }
        }
    }
}
