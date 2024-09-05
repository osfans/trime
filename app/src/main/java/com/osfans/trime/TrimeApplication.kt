// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.preference.PreferenceManager
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.main.LogActivity
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import timber.log.Timber
import kotlin.system.exitProcess

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

        fun getInstance() = instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = lastPid

        private const val MAX_STACKTRACE_SIZE = 128000
    }

    val coroutineScope = MainScope() + CoroutineName("TrimeApplication")

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                startActivity(
                    Intent(applicationContext, LogActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(LogActivity.FROM_CRASH, true)
                        // avoid transaction overflow
                        val truncated =
                            e.stackTraceToString().let {
                                if (it.length > MAX_STACKTRACE_SIZE) {
                                    it.take(MAX_STACKTRACE_SIZE) + "<truncated>"
                                } else {
                                    it
                                }
                            }
                        putExtra(LogActivity.CRASH_STACK_TRACE, truncated)
                    },
                )
                exitProcess(10)
            }
        }
        instance = this
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            AppPrefs.initDefault(sharedPreferences).apply {
                initDefaultPreferences()
            }
            if (BuildConfig.DEBUG) {
                Timber.plant(
                    object : Timber.DebugTree() {
                        override fun createStackElementTag(element: StackTraceElement): String =
                            "${super.createStackElementTag(element)}|${element.fileName}:${element.lineNumber}"

                        override fun log(
                            priority: Int,
                            tag: String?,
                            message: String,
                            t: Throwable?,
                        ) {
                            super.log(
                                priority,
                                "[${Thread.currentThread().name}] ${tag?.substringBefore('|')}",
                                "${tag?.substringAfter('|')}] $message",
                                t,
                            )
                        }
                    },
                )
            } else {
                Timber.plant(
                    object : Timber.Tree() {
                        override fun log(
                            priority: Int,
                            tag: String?,
                            message: String,
                            t: Throwable?,
                        ) {
                            if (priority < Log.INFO) return
                            Log.println(priority, "[${Thread.currentThread().name}]", message)
                        }
                    },
                )
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
