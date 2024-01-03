package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import com.osfans.trime.R
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel

class ClipboardFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.clipboard_preference)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.clipboard))
        viewModel.disableTopOptionsMenu()
    }
}
