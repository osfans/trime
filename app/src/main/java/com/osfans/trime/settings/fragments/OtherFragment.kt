package com.osfans.trime.settings.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R

class OtherFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.other_preference)
    }
}