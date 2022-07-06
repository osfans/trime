@file:Suppress("DEPRECATION")

package com.osfans.trime.ui.fragments

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.ui.components.ResetAssetsDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.RimeUtils
import com.osfans.trime.util.formatDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber

class ConfFragment : PreferenceFragmentCompat(), CoroutineScope by MainScope() {

    private val viewModel: MainViewModel by activityViewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.conf_preference)
        with(preferenceScreen) {
            get<Preference>("conf_synchronize")?.setOnPreferenceClickListener {
                val progress = ProgressDialog(context)
                progress.setMessage(getString(R.string.sync_progress))
                progress.setCancelable(false)
                launch {
                    try {
                        RimeUtils.sync(requireContext())
                    } catch (ex: Exception) {
                        Timber.e(ex, "Sync Exception")
                    } finally {
                        progress.dismiss()
                    }
                }
                true
            }
            get<Preference>("conf__synchronize_background")?.setOnPreferenceClickListener {
                setBackgroundSyncSummary(context)
                true
            }
            get<Preference>("conf__reset")?.setOnPreferenceClickListener {
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
                val lastResult = prefs.conf.lastSyncStatus
                val lastTime = prefs.conf.lastBackgroundSync
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
