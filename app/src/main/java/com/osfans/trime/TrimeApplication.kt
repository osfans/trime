package com.osfans.trime

import android.app.Application
import com.osfans.trime.ime.core.Preferences
import timber.log.Timber

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication : Application() {
    companion object {
        private var instance: TrimeApplication? = null
        fun getInstance() =
            instance ?: throw IllegalStateException("Trime application is not created!")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            }
            val prefs = Preferences.initDefault(this)
            prefs.initDefaultPreferences()
        } catch (e: Exception) {
            e.fillInStackTrace()
            return
        }
    }
}
