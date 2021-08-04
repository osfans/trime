package com.osfans.trime

import android.app.Application
import com.osfans.trime.ime.core.Preferences

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = Preferences.initDefault(this)
        prefs.initDefaultPreferences()
    }
}