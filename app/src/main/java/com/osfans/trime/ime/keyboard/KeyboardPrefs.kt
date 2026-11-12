/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.isLandscape

object KeyboardPrefs {
    private val prefs = AppPrefs.defaultInstance()

    private const val WIDE_SCREEN_WIDTH_DP = 600

    fun Context.isLandscapeMode(): Boolean = isLandscapeMode(resources.configuration.isLandscape())

    /**
     * Whether the theme's landscape sizes would be used in portrait
     * orientation.
     *
     * The floating card always uses the portrait layout, but a wide screen can
     * still select the landscape sizes through the WIDE preference.
     */
    fun Context.isLandscapeModeInPortrait(): Boolean = isLandscapeMode(landscapeOrientation = false)

    private fun Context.isLandscapeMode(landscapeOrientation: Boolean): Boolean = when (prefs.keyboard.landscapeMode.getValue()) {
        AppPrefs.Keyboard.LandscapeMode.WIDE -> landscapeOrientation || isWideScreen()
        AppPrefs.Keyboard.LandscapeMode.LANDSCAPE -> landscapeOrientation
        AppPrefs.Keyboard.LandscapeMode.ALWAYS -> true
        else -> false
    }

    private fun Context.isWideScreen(): Boolean {
        val metrics = resources.displayMetrics
        // Use the portrait width so a phone in landscape is not treated as a
        // tablet; the floating card is only as wide as the portrait screen.
        val portraitWidth = minOf(metrics.widthPixels, metrics.heightPixels)
        return portraitWidth / metrics.density > WIDE_SCREEN_WIDTH_DP
    }
}
