/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.util.ColorUtils

class NavigationBarManager {
    private val navbarBackground by ThemeManager.prefs.navbarBackground

    private var shouldUpdateNavbarForeground = false
    private var shouldUpdateNavbarBackground = false

    private fun Window.useSystemNavbarBackground(enabled: Boolean) {
        // 35+ enforces edge to edge and we must draw behind navbar
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowCompat.setDecorFitsSystemWindows(this, enabled)
        }
    }

    private fun Window.setNavbarBackgroundColor(
        @ColorInt color: Int,
    ) {
        /**
         * Why on earth does it deprecated? It says
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r3/core/java/android/view/Window.java#2720
         * "If the app targets VANILLA_ICE_CREAM or above, the color will be transparent and cannot be changed"
         * but it only takes effect on API 35+ devices. Older devices still needs this.
         */
        @Suppress("DEPRECATION")
        navigationBarColor = color
    }

    private fun Window.enforceNavbarContrast(enforced: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isNavigationBarContrastEnforced = enforced
        }
    }

    fun evaluate(window: Window) {
        when (navbarBackground) {
            ThemePrefs.NavbarBackground.NONE -> {
                shouldUpdateNavbarForeground = false
                shouldUpdateNavbarBackground = false
                window.useSystemNavbarBackground(true)
                window.enforceNavbarContrast(true)
            }
            ThemePrefs.NavbarBackground.COLOR_ONLY -> {
                shouldUpdateNavbarForeground = true
                shouldUpdateNavbarBackground = true
                window.useSystemNavbarBackground(true)
                window.enforceNavbarContrast(false)
            }
            ThemePrefs.NavbarBackground.FULL -> {
                shouldUpdateNavbarForeground = true
                shouldUpdateNavbarBackground = false
                window.useSystemNavbarBackground(false)
                window.setNavbarBackgroundColor(Color.TRANSPARENT)
                window.enforceNavbarContrast(false)
            }
        }
    }

    fun evaluate(
        window: Window,
        useVirtualKeyboard: Boolean,
    ) {
        if (useVirtualKeyboard) {
            evaluate(window)
        } else {
            shouldUpdateNavbarForeground = true
            shouldUpdateNavbarBackground = true
            window.useSystemNavbarBackground(true)
            window.enforceNavbarContrast(false)
        }
        update(window)
    }

    fun update(window: Window) {
        val backColor =
            runCatching {
                ColorManager.getColor("back_color")
            }.getOrDefault(Color.BLACK)
        if (shouldUpdateNavbarForeground) {
            WindowCompat
                .getInsetsController(window, window.decorView)
                .isAppearanceLightNavigationBars = !ColorUtils.isContrastedDark(backColor)
        }
        if (shouldUpdateNavbarBackground) {
            window.setNavbarBackgroundColor(backColor)
        }
    }

    fun setupInputView(v: BaseInputView) {
        // on API 35+, we must call requestApplyInsets() manually after replacing views,
        // otherwise View#onApplyWindowInsets won't be called. ¯\_(ツ)_/¯
        v.requestApplyInsets()
    }
}
