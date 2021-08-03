package com.osfans.trime.settings.fragments

import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.settings.components.ColorPickerDialog
import com.osfans.trime.settings.components.ThemePickerDialog

class LooksFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.looks_preference)

        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.forEach { item -> item.isVisible = false}
        super.onPrepareOptionsMenu(menu)
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