/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.util.isLandscape

object KeyboardPrefs {
    private const val WIDE_SCREEN_WIDTH_DP = 600

    fun Context.isLandscapeMode(): Boolean = when (ThemeManager.prefs.landscapeMode.getValue()) {
        ThemePrefs.LandscapeMode.WIDE -> resources.configuration.isLandscape() || isWideScreen()
        ThemePrefs.LandscapeMode.LANDSCAPE -> resources.configuration.isLandscape()
        ThemePrefs.LandscapeMode.ALWAYS -> true
        else -> false
    }

    private fun Context.isWideScreen(): Boolean {
        val metrics = resources.displayMetrics
        return metrics.widthPixels / metrics.density > WIDE_SCREEN_WIDTH_DP
    }
}
