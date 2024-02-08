package com.osfans.trime.util

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowCompat

fun Activity.applyTranslucentSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // windowLightNavigationBar is available for 27+
    window.navigationBarColor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Color.TRANSPARENT
        } else {
            // com.android.internal.R.color.system_bar_background_semi_transparent
            0x66000000
        }
    WindowCompat.getInsetsController(window, window.decorView)
        .isAppearanceLightNavigationBars = !resources.configuration.isNightMode()
}
