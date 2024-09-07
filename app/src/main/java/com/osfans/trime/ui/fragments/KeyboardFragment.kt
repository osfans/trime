// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.settings.KeySoundEffectPickerDialog
import kotlinx.coroutines.launch

class KeyboardFragment :
    PaddingPreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.keyboard_preference)
        findPreference<Preference>("keyboard__key_sound_package")
            ?.setOnPreferenceClickListener {
                lifecycleScope.launch { KeySoundEffectPickerDialog.build(lifecycleScope, requireContext()).show() }
                true
            }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        when (key) {
            "keyboard__key_long_press_timeout",
            "keyboard__key_repeat_interval",
            "keyboard__show_key_popup",
            AppPrefs.Keyboard.LANDSCAPE_MODE, AppPrefs.Keyboard.SPLIT_SPACE_PERCENT,
            "keyboard__show_window",
            "keyboard__inline_preedit", "keyboard__soft_cursor",
            -> {
                TrimeInputMethodService.getServiceOrNull()?.recreateInputView()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_keyboard))
        viewModel.disableTopOptionsMenu()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }
}
