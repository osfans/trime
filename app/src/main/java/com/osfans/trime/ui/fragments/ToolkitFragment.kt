package com.osfans.trime.ui.fragments

import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.ShortcutUtils

@Suppress("unused")
class ToolkitFragment : PreferenceFragmentCompat() {

    private val viewModel : MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
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
            }
        )
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        viewModel.disableTopOptionsMenu()
    }
}
