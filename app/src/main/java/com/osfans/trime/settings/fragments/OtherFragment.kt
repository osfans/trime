package com.osfans.trime.settings.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.osfans.trime.R
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.settings.PrefMainActivity

class OtherFragment: PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.other_preference)

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
                    trime?.showStatusIcon(R.drawable.status)
                } else { trime.hideStatusIcon() }
            }
            "pref__settings_theme" -> {
                (activity as PrefMainActivity).finish()
                val intent = Intent(context, PrefMainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                requireContext().startActivity(intent)
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