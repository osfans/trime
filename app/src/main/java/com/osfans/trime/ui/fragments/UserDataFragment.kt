package com.osfans.trime.ui.fragments

import android.content.Context
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class UserDataFragment : PreferenceFragmentCompat() {

    private val viewModel: MainViewModel by activityViewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.user_data_preference)
        with(preferenceScreen) {
            get<Preference>("data_synchronize")?.setOnPreferenceClickListener {
                lifecycleScope.withLoadingDialog(context, 200L, R.string.sync_progress) {
                    withContext(Dispatchers.IO) {
                        Rime.sync_user_data()
                        Rime.destroy()
                        Rime.get(context, true)
                    }
                }
                true
            }
            get<Preference>("data_synchronize_background")?.setOnPreferenceClickListener {
                setBackgroundSyncSummary(context)
                true
            }
            get<Preference>("data_reset")?.setOnPreferenceClickListener {
                ResetAssetsDialog(context).show()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_user_data))
        viewModel.disableTopOptionsMenu()
        setBackgroundSyncSummary(context)
    }

    private fun setBackgroundSyncSummary(context: Context?) {
        val syncBgPref = findPreference<SwitchPreferenceCompat>("pref_sync_bg")
        if (context == null) {
            if (syncBgPref?.isChecked == true) {
                syncBgPref.setSummaryOn(R.string.pref_sync_bg_never)
            } else {
                syncBgPref?.setSummaryOff(R.string.conf__synchronize_background_summary)
            }
        } else {
            var summary: String
            if (syncBgPref?.isChecked == true) {
                val lastResult = prefs.userData.lastSyncStatus
                val lastTime = prefs.userData.lastBackgroundSync
                summary = if (lastResult) {
                    context.getString(R.string.pref_sync_bg_success)
                } else {
                    context.getString(R.string.pref_sync_bg_failure)
                }
                summary = if (lastTime == 0L) {
                    context.getString(R.string.conf__synchronize_background_summary)
                } else {
                    summary.format(formatDateTime(lastTime))
                }
                syncBgPref.summaryOn = summary
            } else {
                syncBgPref?.setSummaryOff(R.string.conf__synchronize_background_summary)
            }
        }
    }
}
