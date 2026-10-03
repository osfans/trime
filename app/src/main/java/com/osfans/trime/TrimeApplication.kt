/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.receiver.RimeIntentReceiver
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.util.isNightMode
import com.osfans.trime.worker.BackgroundSyncWork
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import splitties.systemservices.userManager
import timber.log.Timber
import java.io.File
import kotlin.system.exitProcess

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication : Application() {
    val coroutineScope = MainScope() + CoroutineName("TrimeApplication")

    private val rimeIntentReceiver = RimeIntentReceiver()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_UNLOCKED) return
            if (!isDirectBootMode) return
            Timber.d("Device unlocked, app will exit now and restart to normal mode")
            RimeDaemon.getFirstSessionOrNull()?.also {
                // try to shutdown rime gracefully
                RimeDaemon.stopRime()
            }
            exitProcess(0)
        }
    }

    private fun registerBroadcastReceiver() {
        val intentFilter =
            IntentFilter().apply {
                addAction(RimeIntentReceiver.ACTION_DEPLOY)
                addAction(RimeIntentReceiver.ACTION_SYNC_USER_DATA)
            }
        ContextCompat.registerReceiver(
            this,
            rimeIntentReceiver,
            intentFilter,
            PERMISSION_TEST_INPUT_METHOD,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    var isDirectBootMode = false
        private set

    val directBootAwareContext: Context
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isDirectBootMode) {
            createDeviceProtectedStorageContext()
        } else {
            applicationContext
        }

    val externalFilesDir: File
        get() = with(directBootAwareContext) {
            getExternalFilesDir(null) ?: filesDir
        }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !userManager.isUserUnlocked) {
            isDirectBootMode = true
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        }
        val ctx = directBootAwareContext

        if (!BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                val crashTime = System.currentTimeMillis()
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                val lastCrashTimePrefKey = "last_crash_time"
                val lastCrashTime = sharedPrefs.getLong(lastCrashTimePrefKey, -1L)
                sharedPrefs.edit(commit = true) {
                    putLong(lastCrashTimePrefKey, crashTime)
                }
                if (crashTime - lastCrashTime <= 10_000L) {
                    // continuous crashes within 10 seconds, maybe in a crash loop. just bail
                    exitProcess(10)
                }
                startActivity(
                    Intent(ctx, LogActivity::class.java).apply {
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
            if (BuildConfig.DEBUG) {
                Timber.plant(
                    object : Timber.DebugTree() {
                        override fun createStackElementTag(element: StackTraceElement): String = "${super.createStackElementTag(element)}|${element.fileName}:${element.lineNumber}"

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
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)

            Timber.d("isDirectBootMode=$isDirectBootMode")

            val appPrefs = AppPrefs.initDefault(sharedPreferences)
            // record last pid for crash logs
            appPrefs.internal.pid.apply {
                val currentPid = Process.myPid()
                lastPid = getValue()
                Timber.d("Last pid is $lastPid. Set it to current pid: $currentPid")
                setValue(currentPid)
            }
            ClipboardHelper.init(ctx)
            CollectionHelper.init(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isDirectBootMode) {
                AppPrefs.defaultInstance().syncToDeviceEncryptedStorage()
                ThemeManager.syncToDeviceEncryptedStorage()
            }
            registerBroadcastReceiver()
            startWorkManager()
        } catch (e: Exception) {
            e.fillInStackTrace()
            return
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            ColorManager.onSystemNightModeChange(newConfig.isNightMode())
        } catch (e: Exception) {
            Timber.w(e, "Something wrong on configuration changed")
        }
    }

    private fun startWorkManager() {
        coroutineScope.launch {
            BackgroundSyncWork.start(directBootAwareContext)
        }
    }

    companion object {
        private var instance: TrimeApplication? = null
        private var lastPid: Int? = null

        fun getInstance() = instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = lastPid

        private const val MAX_STACKTRACE_SIZE = 128000

        /**
         * This permission is requested by com.android.shell, makes it possible to start
         * deploy from `adb shell am` command:
         * ```sh
         * adb shell am broadcast -a com.osfans.trime.action.DEPLOY
         * ```
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-7.0.0_r1/packages/Shell/AndroidManifest.xml#67
         *
         * other candidate: android.permission.TEST_INPUT_METHOD requires Android 14
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/packages/Shell/AndroidManifest.xml#628
         */
        const val PERMISSION_TEST_INPUT_METHOD = "android.permission.READ_INPUT_STATE"
    }
}
