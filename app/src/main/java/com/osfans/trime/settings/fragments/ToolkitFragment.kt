package com.osfans.trime.settings.fragments

import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.util.ShortcutUtils

@Suppress("unused")
class ToolkitFragment : PreferenceFragmentCompat() {
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

        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.forEach { item -> item.isVisible = false }
        super.onPrepareOptionsMenu(menu)
    }
}
