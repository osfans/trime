package com.osfans.trime.settings.fragments

import android.content.SharedPreferences
import android.os.Bundle
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
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            "pref_themes" -> {
                ThemePickerDialog(requireContext()).show()
                true
            }
            "pref_colors" -> {
                ColorPickerDialog(requireContext()).show()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
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
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
}