package com.osfans.trime.ui.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.ui.components.ResetAssetsDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.formatDateTime
import com.osfans.trime.util.withLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileFragment : PreferenceFragmentCompat() {

    private val viewModel: MainViewModel by activityViewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.profile_preference)
        with(preferenceScreen) {
            get<Preference>("profile_sync_user_data")?.setOnPreferenceClickListener {
                lifecycleScope.withLoadingDialog(context, 200L, R.string.sync_progress) {
                    withContext(Dispatchers.IO) {
                        Rime.sync_user_data()
                        Rime.destroy()
                        Rime.get(context, true)
                    }
                }
                true
            }
            get<SwitchPreferenceCompat>("profile_sync_in_background")?.apply {
                val lastBackgroundSync = prefs.profile.lastBackgroundSync
                summaryOn =
                    if (lastBackgroundSync.isBlank()) {
                        context.getString(R.string.profile_never_sync_in_background)
                    } else {
                        context.getString(
                            R.string.profile_last_sync_in_background,
                            formatDateTime(lastBackgroundSync.toLong()),
                            context.getString(
                                if (prefs.profile.lastSyncStatus) {
                                    R.string.success
                                } else {
                                    R.string.failure
                                }
                            )
                        )
                    }
                summaryOff = context.getString(R.string.profile_enable_syncing_in_background)
            }
            get<Preference>("profile_reset")?.setOnPreferenceClickListener {
                ResetAssetsDialog(context).show()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_profile))
        viewModel.disableTopOptionsMenu()
    }
}
