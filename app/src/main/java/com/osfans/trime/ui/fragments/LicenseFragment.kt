package com.osfans.trime.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.osfans.trime.R
import com.osfans.trime.data.LibraryLicenseDao
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import kotlinx.coroutines.launch

class LicenseFragment : PaddingPreferenceFragment() {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        lifecycleScope.launch {
            LibraryLicenseDao.getAll().onSuccess { licenses ->
                val context = preferenceManager.context
                val screen = preferenceManager.createPreferenceScreen(context)
                licenses.forEach { license ->
                    screen.addPreference(
                        Preference(context).apply {
                            isIconSpaceReserved = false
                            title = license.libraryName
                            summary = license.artifactId.group
                            setOnPreferenceClickListener {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(license.licenseUrl)))
                                true
                            }
                        }
                    )
                }
                preferenceScreen = screen
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.about__license))
        viewModel.disableTopOptionsMenu()
    }
}
