// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.content.Context
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.util.isLandscape

object KeyboardPrefs {
    private val prefs = AppPrefs.defaultInstance()

    private const val WIDE_SCREEN_WIDTH_DP = 600

    fun Context.isLandscapeMode(): Boolean {
        return when (prefs.keyboard.splitOption) {
            AppPrefs.Keyboard.SplitOption.AUTO -> isWideScreen()
            AppPrefs.Keyboard.SplitOption.LANDSCAPE -> resources.configuration.isLandscape()
            AppPrefs.Keyboard.SplitOption.ALWAYS -> true
            else -> false
        }
    }

    private fun Context.isWideScreen(): Boolean {
        val metrics = resources.displayMetrics
        return metrics.widthPixels / metrics.density > WIDE_SCREEN_WIDTH_DP
    }
}
