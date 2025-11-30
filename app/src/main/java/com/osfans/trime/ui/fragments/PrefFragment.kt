/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import splitties.resources.styledColor

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
            get<Preference>("pref_schemata")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_schemaListFragment)
                    true
                }
            }
            get<Preference>("pref_user_dict")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_userDictionaryFragment)
                    true
                }
            }
            get<Preference>("pref_user_data")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_profileFragment)
                    true
                }
            }
            get<Preference>("pref_general")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_generalSettingsFragment)
                    true
                }
            }
            get<Preference>("pref_keyboard")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_keyboardFragment)
                    true
                }
            }
            get<Preference>("pref_candidates")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_candidatesSettingsFragment)
                    true
                }
            }
            get<Preference>("pref_theme_and_color")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_themeSettingsFragment)
                    true
                }
            }
            get<Preference>("pref_clipboard")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_clipboardSettingsFragment)
                    true
                }
            }
            get<Preference>("pref_toolkit")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_toolkitFragment)
                    true
                }
            }
            get<Preference>("pref_others")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_prefFragment_to_otherFragment)
                    true
                }
            }
        }
    }
}
