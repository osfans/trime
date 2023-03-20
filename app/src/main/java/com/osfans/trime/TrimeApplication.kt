package com.osfans.trime

import android.app.Application
import android.os.Process
import android.util.Log
import androidx.preference.PreferenceManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.ui.main.LogActivity
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
        private var lastPid: Int? = null

        fun getInstance() =
            instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = lastPid
    }

    override fun onCreate() {
        super.onCreate()
        CaocConfig.Builder
            .create()
            .errorActivity(LogActivity::class.java)
            .enabled(!BuildConfig.DEBUG)
            .apply()
        instance = this
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            AppPrefs.initDefault(sharedPreferences).apply {
                initDefaultPreferences()
            }
            if (BuildConfig.DEBUG) {
                Timber.plant(object : Timber.DebugTree() {
                    override fun createStackElementTag(element: StackTraceElement): String {
                        return "${super.createStackElementTag(element)}|${element.fileName}:${element.lineNumber}"
                    }

                    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                        super.log(
                            priority,
                            "[${Thread.currentThread().name}] ${tag?.substringBefore('|')}",
                            "${tag?.substringAfter('|')}] $message",
                            t,
                        )
                    }
                })
            } else {
                Timber.plant(object : Timber.Tree() {
                    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                        if (priority < Log.INFO) return
                        Log.println(priority, "[${Thread.currentThread().name}]", message)
                    }
                })
            }
            // record last pid for crash logs
            val appPrefs = AppPrefs.defaultInstance()
            val currentPid = Process.myPid()
            appPrefs.internal.pid.apply {
                lastPid = this
                Timber.d("Last pid is $lastPid. Set it to current pid: $currentPid")
            }
            appPrefs.internal.pid = currentPid
            ClipboardHelper.init(applicationContext)
            CollectionHelper.init(applicationContext)
            DraftHelper.init(applicationContext)
        } catch (e: Exception) {
            e.fillInStackTrace()
            return
        }
    }
}
