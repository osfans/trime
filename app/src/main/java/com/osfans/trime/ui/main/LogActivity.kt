/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.ClipData
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.databinding.ActivityLogBinding
import com.osfans.trime.ui.common.PublicWindowPilotActivity
import com.osfans.trime.ui.common.dialog.PublicAlertDialogs
import com.osfans.trime.ui.main.log.LogView
import com.osfans.trime.util.DeviceInfo
import com.osfans.trime.util.Logcat
import com.osfans.trime.util.iso8601UTCDateTime
import com.osfans.trime.util.toast
import io.github.whiredplanck.storageaccess.StorageAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.clipboardManager

/**
 * The activity to show [LogView].
 *
 * This file is adapted from fcitx5-android project.
 * Source: [fcitx5-android/LogActivity](https://github.com/fcitx5-android/fcitx5-android/blob/24457e13b7c3f9f59a6f220db7caad3d02f27651/app/src/main/java/org/fcitx/fcitx5/android/ui/main/LogActivity.kt)
 */
class LogActivity : PublicWindowPilotActivity() {
    private lateinit var logView: LogView
    private val storageAccess = StorageAccess(this)

    private fun createRetryMenu(menu: Menu) {
        if (intent.hasExtra(FROM_DEPLOY)) {
            menu.add(Menu.NONE, R.id.action_retry_deploy, Menu.NONE, R.string.ime_retry).apply {
                setIcon(R.drawable.ic_baseline_refresh_reversed_24)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                setOnMenuItemClickListener {
                    isEnabled = false
                    lifecycleScope.launch {
                        val name = "LogActivity.retry"
                        val session = RimeDaemon.createSession(name)
                        try {
                            if (RimeDaemon.engineState.value == RimeLifecycle.State.FAILED) {
                                RimeDaemon.retryFailedStartup()
                            } else {
                                session.runOnReady { deploy() }
                            }
                        } catch (failure: Exception) {
                            if (failure is CancellationException && failure !is com.osfans.trime.core.RimeUnavailableException) throw failure
                            toast(R.string.deploy_failure)
                        } finally {
                            RimeDaemon.destroySession(name)
                            isEnabled = true
                        }
                    }
                    true
                }
            }
        }
    }

    private fun configureUpNavigation(toolbar: Toolbar) {
        toolbar.apply {
            val indicator = TypedValue()
            val themedIcon =
                if (context.theme.resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, indicator, true) &&
                    indicator.resourceId != 0
                ) {
                    AppCompatResources.getDrawable(context, indicator.resourceId)
                } else {
                    null
                }
            navigationIcon = themedIcon ?: DrawerArrowDrawable(context).apply {
                progress = 1f
                color = ContextCompat.getColor(context, R.color.toolbarForegroundColor)
            }
            setNavigationContentDescription(R.string.public_window_navigate_up)
            setNavigationOnClickListener { navigateToManifestParent() }
        }
    }

    companion object {
        const val FROM_CRASH = "from_crash"
        const val FROM_DEPLOY = "from_deploy"
        const val CRASH_STACK_TRACE = "crash_stack_trace"
        const val DEPLOY_FAILURE_TRACE = "deploy_failure_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityLogBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBars.left
                rightMargin = systemBars.right
                bottomMargin = systemBars.bottom
            }
            binding.logToolbar.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
            }
            windowInsets
        }
        WindowCompat
            .getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setContentView(binding.root)
        with(binding) {
            this@LogActivity.logView = logView
            if (intent.hasExtra(FROM_CRASH)) {
                logToolbar.toolbar.setTitle(R.string.crash_logs)
                clearButton.visibility = View.GONE
                copyButton.visibility = View.GONE
                PublicAlertDialogs
                    .builder(this@LogActivity)
                    .setTitle(R.string.app_crash)
                    .setMessage(R.string.app_crash_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                logView.append("--------- Crash stacktrace")
                logView.append(intent.getStringExtra(CRASH_STACK_TRACE) ?: "<empty>")
                logView.setLogcat(Logcat(TrimeApplication.getLastPid()))
            } else if (intent.hasExtra(FROM_DEPLOY)) {
                logToolbar.toolbar.setTitle(R.string.deploy_failure)
                clearButton.visibility = View.GONE
                logView.append(intent.getStringExtra(DEPLOY_FAILURE_TRACE) ?: "<empty>")
            } else {
                logToolbar.toolbar.setTitle(R.string.real_time_logs)
                configureUpNavigation(logToolbar.toolbar)
                copyButton.visibility = View.GONE
                logView.setLogcat(Logcat())
            }
            clearButton.setOnClickListener {
                logView.clear()
            }
            exportButton.setOnClickListener {
                lifecycleScope.launch {
                    runCatching {
                        val log = storageAccess.createFile(
                            filename = "$packageName-${iso8601UTCDateTime()}.txt",
                            mimeType = "text/plain",
                        ) ?: return@launch
                        // Snapshot UI-backed state before the provider write leaves Main.
                        val deviceInfo = DeviceInfo.get(this@LogActivity)
                        val logSnapshot = logView.currentLog
                        withContext(Dispatchers.IO) {
                            StorageAccess.writeFile(log.uri) { outs ->
                                outs.bufferedWriter().use {
                                    it.write(deviceInfo)
                                    it.write(logSnapshot)
                                }
                            }
                        }
                    }.onFailure {
                        if (it is CancellationException) throw it
                    }.let { toast(it) }
                }
            }
            copyButton.setOnClickListener {
                ClipboardHelper.setSensitivePrimaryClip(
                    clipboardManager,
                    ClipData.newPlainText("log", logView.currentLog),
                )
                if (clipboardManager.hasPrimaryClip()) {
                    toast(R.string.copy_done)
                }
            }
            jumpToBottomButton.setOnClickListener {
                logView.scrollToBottom()
            }
            createRetryMenu(logToolbar.toolbar.menu)
        }
    }
}
