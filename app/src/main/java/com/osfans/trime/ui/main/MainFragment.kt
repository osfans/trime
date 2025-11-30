/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import splitties.resources.styledColor

class MainFragment : PaddingPreferenceFragment() {
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
            get<Preference>("schemata")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.SchemaList)
                    true
                }
            }
            get<Preference>("user_dict")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.UserDict)
                    true
                }
            }
            get<Preference>("user_data")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.Profile)
                    true
                }
            }
            get<Preference>("general")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.General)
                    true
                }
            }
            get<Preference>("keyboard")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.VirtualKeyboard)
                    true
                }
            }
            get<Preference>("candidates")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.CandidatesWindow)
                    true
                }
            }
            get<Preference>("theme")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.Theme)
                    true
                }
            }
            get<Preference>("clipboard")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.Clipboard)
                    true
                }
            }
            get<Preference>("advanced")?.apply {
                icon?.setTint(styledColor(android.R.attr.colorControlNormal))
                setOnPreferenceClickListener {
                    findNavController().navigate(NavigationRoute.Advanced)
                    true
                }
            }
        }
    }
}
