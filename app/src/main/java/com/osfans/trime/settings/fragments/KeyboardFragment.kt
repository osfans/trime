package com.osfans.trime.settings.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import androidx.core.view.forEach
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.Rime
import com.osfans.trime.ime.core.Trime

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
            "keyboard__key_sound" -> {
                trime?.resetEffect()
            }
            "keyboard__key_vibration" -> {
                trime?.resetEffect()
            }
            "keyboard__key_sound_volume" -> {
                trime?.let {
                    it.resetEffect()
                    it.soundEffect()
                }
            }
            "keyboard__key_vibration_duration", "keyboard__key_vibration_amplitude" -> {
                trime?.let {
                    it.resetEffect()
                    it.vibrateEffect()
                }
            }
            "keyboard__speak_key_press", "keyboard__speak_commit" -> {
                trime?.resetEffect()
            }
            "keyboard__key_long_press_timeout",
            "keyboard__key_repeat_interval",
            "keyboard__show_key_popup" -> {
                trime?.resetKeyboard()
            }
            "keyboard__show_window" -> {
                trime?.resetCandidate()
            }
            "keyboard__inline_preedit", "keyboard__soft_cursor" -> {
                trime?.loadConfig()
            }
            "keyboard__show_switches" -> {
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