package com.osfans.trime.settings.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.Rime
import com.osfans.trime.ime.core.Preferences
import com.osfans.trime.ime.core.Trime

class KeyboardFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.keyboard_preference)

        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.forEach { item -> item.isVisible = false }
        super.onPrepareOptionsMenu(menu)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val trime = Trime.getService()
        val prefs = Preferences.defaultInstance()
        prefs.sync()
        when (key) {
            "keyboard__key_long_press_timeout",
            "keyboard__key_repeat_interval",
            "keyboard__show_key_popup" -> {
                trime.resetKeyboard()
            }
            "keyboard__show_window" -> {
                trime.resetCandidate()
            }
            "keyboard__inline_preedit", "keyboard__soft_cursor" -> {
                trime.loadConfig()
            }
            "keyboard__show_switches" -> {
                Rime.setShowSwitches(prefs.keyboard.switchesEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
}
