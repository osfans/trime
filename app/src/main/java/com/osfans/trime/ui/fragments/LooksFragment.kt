package com.osfans.trime.ui.fragments

import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.ui.components.ColorPickerDialog
import com.osfans.trime.ui.components.ThemePickerDialog
import com.osfans.trime.ui.main.MainViewModel

class LooksFragment : PreferenceFragmentCompat() {
    private val viewModel : MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.looks_preference)
    }

    override fun onResume() {
        super.onResume()
        viewModel.disableTopOptionsMenu()
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            "looks__selected_theme" -> {
                ThemePickerDialog(requireContext()).show()
                true
            }
            "looks__selected_color" -> {
                ColorPickerDialog(requireContext()).show()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }
}
