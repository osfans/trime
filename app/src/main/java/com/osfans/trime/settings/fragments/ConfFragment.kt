package com.osfans.trime.settings.fragments

import android.content.Context
import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.settings.components.ResetAssetsDialog
import com.osfans.trime.util.RimeUtils
import com.osfans.trime.util.createLoadingDialog
import com.osfans.trime.util.formatDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber

class ConfFragment : PreferenceFragmentCompat(), CoroutineScope by MainScope() {

    private val prefs get() = AppPrefs.defaultInstance()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.conf_preference)

        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.forEach { item -> item.isVisible = false }
        super.onPrepareOptionsMenu(menu)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            "conf__synchronize" -> {
                val progressDialog = createLoadingDialog(requireContext(), R.string.sync_progress)
                launch {
                    try {
                        RimeUtils.sync(requireContext())
                    } catch (ex: Exception) {
                        Timber.e(ex, "Sync Exception")
                    } finally {
                        progressDialog.dismiss()
                    }
                }
                true
            }
            "conf__synchronize_background" -> {
                setBackgroundSyncSummary(context)
                true
            }
            "conf__reset" -> {
                ResetAssetsDialog(requireContext()).show()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    override fun onResume() {
        super.onResume()
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
