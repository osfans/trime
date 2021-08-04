package com.osfans.trime.settings.fragments

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.forEach
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.osfans.trime.Config
import com.osfans.trime.R
import com.osfans.trime.ime.core.Preferences
import com.osfans.trime.ime.core.Trime

class OtherFragment: PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs get() = Preferences.defaultInstance()
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.other_preference)
        findPreference<ListPreference>("other__ui_mode")?.setOnPreferenceChangeListener { _, newValue ->
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
    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            "other__clipboard_manager" -> {
                PreferenceManager.getDefaultSharedPreferences(context).getString("pref_clipboard_manager", "")
                    ?.let {
                        ClipBoardManagerDialog(requireContext(),
                            it
                        ).show()
                    }
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val trime = Trime.getService()
        when (key) {
            "other__show_status_bar_icon" -> {
                if (sharedPreferences?.getBoolean(key, false) == true) {
                    trime?.showStatusIcon(R.drawable.ic_status)
                } else { trime.hideStatusIcon() }
            }

            "other__clipboard_compare" -> {
                Config.get(context).setClipBoardCompare(
                    sharedPreferences?.getString(key, "")

                )
            }

            "other__clipboard_output" -> {
                Config.get(context).setClipBoardOutput(
                    sharedPreferences?.getString(key, "")
                )
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
        if (prefs.other.showAppIcon) {
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


    /** 顯示輸入法可以分享剪贴板给哪些App */
    class ClipBoardManagerDialog(private val context: Context, val value: String) {
        private val config = Config.get(context)

        /** 回廠對話框 */
        val resetDialog: AlertDialog

        init {
            var values: ArrayList<String> = ArrayList<String>();

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, "Trime test")
            val packageManager = context.packageManager
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)

            val items = arrayOfNulls<String>(resolveInfos.size)

            for (i in resolveInfos.indices) {
                values.add(resolveInfos[i].activityInfo.packageName + "," + resolveInfos[i].activityInfo.name);
                items[i] = resolveInfos[i].loadLabel(packageManager) as String?;
            }
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.other__clipboard_manager_title)

            var value0: Int = values.indexOf(value);
            if (value0 < 0)
                value0 = 0;

            builder.setSingleChoiceItems(
                items, value0
            ) { dialog, which ->
                config.setClipBoardManager(
                    values[which]
                )
                dialog.dismiss()
            }
            resetDialog = builder.create()

        }

        /** 彈出對話框 */
        fun show() = resetDialog.show()
    }
}