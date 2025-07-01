// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel

class PrefFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onStart() {
        super.onStart()
        viewModel.enableTopOptionsMenu()
    }

    override fun onStop() {
        viewModel.disableTopOptionsMenu()
        super.onStop()
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
        with(preferenceScreen) {
            get<Preference>("pref_schemata")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_schemaListFragment)
                true
            }
            get<Preference>("pref_user_data")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_profileFragment)
                true
            }
            get<Preference>("pref_general")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_generalSettingsFragment)
                true
            }
            get<Preference>("pref_keyboard")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_keyboardFragment)
                true
            }
            get<Preference>("pref_candidates")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_candidatesSettingsFragment)
                true
            }
            get<Preference>("pref_theme_and_color")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_themeSettingsFragment)
                true
            }
            get<Preference>("pref_clipboard")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_clipboardFragment)
                true
            }
            get<Preference>("pref_toolkit")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_toolkitFragment)
                true
            }
            get<Preference>("pref_others")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_otherFragment)
                true
            }
        }
    }
}
