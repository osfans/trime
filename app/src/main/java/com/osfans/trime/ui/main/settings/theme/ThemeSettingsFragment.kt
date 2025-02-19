/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import kotlinx.coroutines.launch

class ThemeSettingsFragment : PreferenceDelegateFragment(ThemeManager.prefs) {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        findPreference<Preference>("selected_theme")?.setOnPreferenceClickListener {
            lifecycleScope.launch { ThemePickerDialog.build(lifecycleScope, requireContext()).show() }
            true
        }
        findPreference<Preference>("normal_mode_color")?.setOnPreferenceClickListener {
            lifecycleScope.launch { ColorPickerDialog.build(lifecycleScope, requireContext()).show() }
            true
        }
    }
}
