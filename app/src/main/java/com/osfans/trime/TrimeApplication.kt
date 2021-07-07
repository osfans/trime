package com.osfans.trime

import android.app.Application
import com.osfans.trime.ime.core.Preferences

class TrimeApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = Preferences.initDefault(this)
        prefs.initDefaultPreferences()
    }
}