package com.osfans.trime.settings.fragments

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.settings.components.SchemaPickerDialog

class PrefFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "pref_schemas" -> {
                    SchemaPickerDialog(requireContext()).show()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }
}
