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
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.PrivacyModeState
import com.osfans.trime.receiver.RimeIntentReceiver
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.util.DirectBootProcessHandoff
import com.osfans.trime.util.isNightMode
import com.osfans.trime.worker.BackgroundSyncWork
import com.osfans.trime.worker.canInitializeWorkManager
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeoutOrNull
import splitties.systemservices.userManager
import timber.log.Timber
import kotlin.system.exitProcess
import androidx.work.Configuration as WorkManagerConfiguration

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication :
    Application(),
    WorkManagerConfiguration.Provider {
    val coroutineScope = MainScope() + CoroutineName("TrimeApplication")

    private val workManagerConfigurationDelegate = lazy { WorkManagerConfiguration.Builder().build() }

    override val workManagerConfiguration: WorkManagerConfiguration
        get() {
            val userUnlocked =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.N || userManager.isUserUnlocked
            val deviceProtectedContext =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isDeviceProtectedStorage
            check(
                canInitializeWorkManager(
                    sdkInt = Build.VERSION.SDK_INT,
                    userUnlocked = userUnlocked,
                    deviceProtectedContext = deviceProtectedContext,
                    directBootMode = isDirectBootMode,
                    processReplacementPending = isDirectBootProcessReplacementPending,
                ),
            ) { "WorkManager is unavailable in this process state" }
            return workManagerConfigurationDelegate.value
        }

    private val rimeIntentReceiver = RimeIntentReceiver()

    @Keep
    private val manualPrivacyModeListener =
        PreferenceDelegate.OnChangeListener<Boolean> { _, enabled ->
            PrivacyModeState.setManual(enabled, ClipboardHelper::quarantineCurrentPrimaryClip)
        }

    private val unlockHandoff = DirectBootProcessHandoff(PrivacyModeState::beginProcessReplacement)

    internal val isDirectBootProcessReplacementPending: Boolean
        get() = unlockHandoff.pending

    private val unlockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_UNLOCKED) return
                val pendingResult = goAsync()
                requestDirectBootProcessReplacementIfUnlocked(pendingResult::finish)
            }
        }

    internal fun requestDirectBootProcessReplacementIfUnlocked(finishBroadcast: (() -> Unit)? = null): Boolean = unlockHandoff.request(
        apiLevel = Build.VERSION.SDK_INT,
        directBootMode = isDirectBootMode,
        isUserUnlocked = {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && userManager.isUserUnlocked
        },
        finishBroadcast = finishBroadcast,
        replaceProcess = ::replaceDirectBootProcessAfterUnlock,
    )

    private fun replaceDirectBootProcessAfterUnlock(finishBroadcastOnce: () -> Unit) {
        Timber.i("Device unlocked; replacing the direct-boot process")
        val handler = Handler(mainLooper)
        val forceExit =
            Runnable {
                Timber.w("Forcing replacement of an unresponsive direct-boot process")
                finishBroadcastOnce()
                exitProcess(0)
            }
        handler.postDelayed(forceExit, UNLOCK_PROCESS_EXIT_TIMEOUT_MS)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val restoreResult =
                    runCatching { AppPrefs.restoreDirectBootChangesToCredentialStorage() }
                        .onFailure { Timber.w(it, "Unable to restore Direct Boot preference changes") }
                        .getOrNull()
                if (restoreResult?.journalRetained == true) {
                    Timber.w("Direct Boot preference changes remain journaled for the next normal start")
                }
                val stopped = withTimeoutOrNull(RIME_SHUTDOWN_TIMEOUT_MS) {
                    RimeDaemon.shutdownForProcessExit()
                    true
                } ?: false
                if (!stopped) {
                    Timber.w("Timed out stopping Rime before process replacement")
                }
            } catch (error: Throwable) {
                Timber.w(error, "Unable to stop Rime cleanly before process replacement")
            } finally {
                handler.removeCallbacks(forceExit)
                finishBroadcastOnce()
                exitProcess(0)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun registerDirectBootUnlockReceiver() {
        val receiverThread = HandlerThread("TrimeDirectBootUnlock")
        try {
            receiverThread.start()
            ContextCompat.registerReceiver(
                this,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                null,
                Handler(receiverThread.looper),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (error: Throwable) {
            receiverThread.quitSafely()
            throw error
        }
        // The registered receiver owns its scheduler until this DE process exits. Keeping it
        // alive also lets a broadcast arriving after a lifecycle request finish normally.
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !userManager.isUserUnlocked) {
            isDirectBootMode = true
        }
        val ctx = directBootAwareContext
        if (!BuildConfig.DEBUG) {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, e ->
                try {
                    val crashTime = System.currentTimeMillis()
                    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val lastCrashTimePrefKey = "last_crash_time"
                    val lastCrashTime = sharedPrefs.getLong(lastCrashTimePrefKey, -1L)
                    sharedPrefs.edit(commit = true) {
                        putLong(lastCrashTimePrefKey, crashTime)
                    }
                    if (crashTime - lastCrashTime <= 10_000L) {
                        // continuous crashes within 10 seconds, maybe in a crash loop. just bail
                        return@setDefaultUncaughtExceptionHandler
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
                } finally {
                    previousHandler?.uncaughtException(thread, e)
                    exitProcess(10)
                }
            }
        }
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
            val appPrefs =
                AppPrefs.initDefault(
                    sharedPreferences,
                    usingDeviceProtectedStorage = isDirectBootMode,
                )
            PrivacyModeState.initialize(appPrefs.general.manualPrivacyMode.getValue())
            appPrefs.general.manualPrivacyMode.registerOnChangeListener(manualPrivacyModeListener)
            // record last pid for crash logs
            appPrefs.internal.pid.apply {
                val currentPid = Process.myPid()
                lastPid = getValue()
                Timber.d("Last pid is $lastPid. Set it to current pid: $currentPid")
                setValue(currentPid)
            }
            ClipboardHelper.init(ctx, persistHistory = !isDirectBootMode)
            CollectionHelper.init(ctx, persistHistory = !isDirectBootMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isDirectBootMode) {
                appPrefs.syncToDeviceEncryptedStorage()
                ThemeManager.syncToDeviceEncryptedStorage()
            }
            registerBroadcastReceiver()
            startWorkManager()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isDirectBootMode) {
                // A background receiver can run immediately. Register only after the minimum DE
                // initialization so it cannot restore preferences before AppPrefs is ready or have
                // its private boundary cleared by PrivacyModeState.initialize.
                registerDirectBootUnlockReceiver()
                // USER_UNLOCKED is not sticky; close an unlock-during-initialization/register race.
                if (userManager.isUserUnlocked) requestDirectBootProcessReplacementIfUnlocked()
            }
        } catch (e: Exception) {
            Timber.e(e, "Application initialization failed")
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
        if (isDirectBootMode) {
            Timber.d("Skip WorkManager initialization in direct-boot mode")
            return
        }
        coroutineScope.launch(Dispatchers.IO) {
            BackgroundSyncWork.start(applicationContext)
        }
    }

    companion object {
        private var instance: TrimeApplication? = null
        private var lastPid: Int? = null

        fun getInstance() = instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = lastPid

        private const val MAX_STACKTRACE_SIZE = 128000
        private const val RIME_SHUTDOWN_TIMEOUT_MS = 5_000L
        private const val UNLOCK_PROCESS_EXIT_TIMEOUT_MS = 7_000L

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
