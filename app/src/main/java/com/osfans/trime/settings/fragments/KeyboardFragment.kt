package com.osfans.trime.settings.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.Rime
import com.osfans.trime.Trime
import com.osfans.trime.settings.components.ColorPickerDialog
import com.osfans.trime.settings.components.ThemePickerDialog

class KeyboardFragment: PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.keyboard_preference)

        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.forEach { item -> item.isVisible = false}
        super.onPrepareOptionsMenu(menu)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val trime = Trime.getService()
        when (key) {
            "key_sound" -> {
                trime?.resetEffect()
            }
            "key_vibrate" -> {
                trime?.resetEffect()
            }
            "key_sound_volume" -> {
                trime?.let {
                    it.resetEffect()
                    it.soundEffect()
                }
            }
            "key_vibrate_duration", "key_vibrate_amplitude" -> {
                trime?.let {
                    it.resetEffect()
                    it.vibrateEffect()
                }
            }
            "speak_key", "speak_commit" -> {
                trime?.resetEffect()
            }
            "longpress_timeout", "repeat_interval", "show_preview" -> {
                trime?.resetKeyboard()
            }
            "show_window" -> {
                trime?.resetCandidate()
            }
            "inline_preedit", "soft_cursor" -> {
                trime?.loadConfig()
            }
            "show_switches" -> {
                sharedPreferences?.getBoolean(key, false)?.let { Rime.setShowSwitches(it) }
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