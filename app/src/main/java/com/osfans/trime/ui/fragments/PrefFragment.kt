// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.ime.dialog.AvailableSchemaPickerDialog
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import kotlinx.coroutines.launch

class PrefFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.trime_app_name))
        viewModel.enableTopOptionsMenu()
    }

    override fun onPause() {
        viewModel.disableTopOptionsMenu()
        super.onPause()
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
        with(preferenceScreen) {
            get<Preference>("pref_schemata")?.setOnPreferenceClickListener {
                viewModel.rime.launchOnReady { api ->
                    lifecycleScope.launch {
                        EnabledSchemaPickerDialog
                            .build(api, lifecycleScope, context) {
                                setPositiveButton(R.string.enable_schemata) { _, _ ->
                                    lifecycleScope.launch {
                                        AvailableSchemaPickerDialog.build(api, lifecycleScope, context).show()
                                    }
                                }
                            }.show()
                    }
                }
                true
            }
            get<Preference>("pref_user_data")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_profileFragment)
                true
            }
            get<Preference>("pref_keyboard")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_keyboardFragment)
                true
            }
            get<Preference>("pref_theme_and_color")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_themeColorFragment)
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
