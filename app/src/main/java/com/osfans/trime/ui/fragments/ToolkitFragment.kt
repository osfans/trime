package com.osfans.trime.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import com.osfans.trime.R
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.Logcat
import com.osfans.trime.util.ShortcutUtils

class ToolkitFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.real_time_logs)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    ShortcutUtils.launchLogActivity(context)
                    true
                }
            },
        )
        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.real_time_logs_clear)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    AlertDialog.Builder(context)
                        .setMessage(R.string.real_time_logs_confirm)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            Logcat.default.clearLog()
                        }.setNegativeButton(R.string.cancel, null)
                        .show()
                    true
                }
            },
        )
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_toolkit))
        viewModel.disableTopOptionsMenu()
    }
}
