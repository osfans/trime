/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import androidx.core.content.edit
import com.osfans.trime.data.prefs.PreferenceDelegate

class PreferenceThemeDelegate(
    sharedPreferences: SharedPreferences,
    key: String,
    defaultValue: Theme,
) : PreferenceDelegate<Theme>(sharedPreferences, key, defaultValue) {
    override fun setValue(value: Theme) {
        sharedPreferences.edit { putString(key, value.id) }
    }

    override fun getValue(): Theme =
        sharedPreferences.getString(key, null)?.let { configId ->
            ThemeManager.getAllThemes().find { it.id == configId }
        } ?: defaultValue
}
