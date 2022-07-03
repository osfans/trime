package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.ui.components.ColorPickerDialog
import com.osfans.trime.ui.components.ThemePickerDialog
import com.osfans.trime.ui.main.MainViewModel

class ThemeColorFragment : PreferenceFragmentCompat() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.theme_color_preference)
        with(preferenceScreen) {
            get<Preference>("theme_selected_theme")?.setOnPreferenceClickListener {
                ThemePickerDialog(context).show()
                true
            }
            get<Preference>("theme_selected_color")?.setOnPreferenceClickListener {
                ColorPickerDialog(context).show()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_theme_and_color))
        viewModel.disableTopOptionsMenu()
    }
}
