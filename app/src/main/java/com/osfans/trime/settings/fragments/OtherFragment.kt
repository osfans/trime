package com.osfans.trime.settings.fragments

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.forEach
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.osfans.trime.R
import com.osfans.trime.ime.core.Trime

class OtherFragment: PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.other_preference)
        findPreference<ListPreference>("pref__settings_theme")?.setOnPreferenceChangeListener { _, newValue ->
            val uiMode = when (newValue) {
                "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
            }
            AppCompatDelegate.setDefaultNightMode(uiMode)
            true
        }

        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.forEach { item -> item.isVisible = false}
        super.onPrepareOptionsMenu(menu)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val trime = Trime.getService()
        when (key) {
            "pref_notification_icon" -> {
                if (sharedPreferences?.getBoolean(key, false) == true) {
                    trime?.showStatusIcon(R.drawable.ic_status)
                } else { trime.hideStatusIcon() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        updateLauncherIconStatus()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateLauncherIconStatus() {
        // Set LauncherAlias enabled/disabled state just before destroying/pausing this activity
        if (prefs.getBoolean("pref__others__show_app_icon", true)) {
            showAppIcon(requireContext())
        } else {
            hideAppIcon(requireContext())
        }
    }

    companion object {
        private const val SETTINGS_ACTIVITY_NAME = "com.osfans.trime.PrefLauncherAlias"

        fun hideAppIcon(context: Context) {
            val pkg: PackageManager = context.packageManager
            pkg.setComponentEnabledSetting(
                ComponentName(context, SETTINGS_ACTIVITY_NAME),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        fun showAppIcon(context: Context) {
            val pkg: PackageManager = context.packageManager
            pkg.setComponentEnabledSetting(
                ComponentName(context, SETTINGS_ACTIVITY_NAME),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}