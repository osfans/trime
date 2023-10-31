package com.osfans.trime.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.soundPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyboardFragment :
    PaddingPreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.keyboard_preference)
        findPreference<Preference>("keyboard__key_sound_package")
            ?.setOnPreferenceClickListener {
                lifecycleScope.launch { requireContext().soundPicker().show() }
                true
            }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        val trime = Trime.getServiceOrNull()
        when (key) {
            "keyboard__key_long_press_timeout",
            "keyboard__key_repeat_interval",
            "keyboard__show_key_popup",
            -> {
                trime?.resetKeyboard()
            }
            "keyboard__show_window" -> {
                trime?.resetCandidate()
            }
            "keyboard__inline_preedit", "keyboard__soft_cursor" -> {
                trime?.loadConfig()
            }
            "keyboard__candidate_page_size" -> {
                val pageSize = AppPrefs.defaultInstance().keyboard.candidatePageSize.toInt()
                if (pageSize <= 0) return
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        Rime.setRimeCustomConfigInt(
                            "default",
                            arrayOf("menu/page_size" to pageSize),
                        )
                        Rime.deployRimeConfigFile("${DataManager.userDataDir}/default.yaml", "")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_keyboard))
        viewModel.disableTopOptionsMenu()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }
}
