package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.colorPicker
import com.osfans.trime.ui.main.themePicker
import kotlinx.coroutines.launch

class ThemeColorFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.theme_color_preference)
        with(preferenceScreen) {
            get<Preference>("theme_selected_theme")?.setOnPreferenceClickListener {
                lifecycleScope.launch { context.themePicker().show() }
                true
            }
            get<Preference>("theme_selected_color")?.setOnPreferenceClickListener {
                lifecycleScope.launch { context.colorPicker().show() }
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
