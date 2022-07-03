package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.ui.components.SchemaPickerDialog
import com.osfans.trime.ui.main.MainViewModel

class PrefFragment : PreferenceFragmentCompat() {

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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
        with(preferenceScreen) {
            get<Preference>("pref_schemas")?.setOnPreferenceClickListener {
                SchemaPickerDialog(context).show()
                true
            }
            get<Preference>("pref_user_data")?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_prefFragment_to_userDataFragment)
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
