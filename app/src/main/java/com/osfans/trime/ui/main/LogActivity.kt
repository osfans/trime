/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.ClipData
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.databinding.ActivityLogBinding
import com.osfans.trime.ui.main.log.LogView
import com.osfans.trime.util.DeviceInfo
import com.osfans.trime.util.Logcat
import com.osfans.trime.util.iso8601UTCDateTime
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import splitties.systemservices.clipboardManager

/**
 * The activity to show [LogView].
 *
 * This file is adapted from fcitx5-android project.
 * Source: [fcitx5-android/LogActivity](https://github.com/fcitx5-android/fcitx5-android/blob/24457e13b7c3f9f59a6f220db7caad3d02f27651/app/src/main/java/org/fcitx/fcitx5/android/ui/main/LogActivity.kt)
 */
class LogActivity : AppCompatActivity() {
    private lateinit var launcher: ActivityResultLauncher<String>
    private lateinit var logView: LogView

    companion object {
        const val FROM_CRASH = "from_crash"
        const val FROM_DEPLOY = "from_deploy"
        const val CRASH_STACK_TRACE = "crash_stack_trace"
        const val DEPLOY_FAILURE_TRACE = "deploy_failure_trace"
    }

    private fun registerLauncher() {
        launcher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                    uri
                        ?.runCatching {
                            contentResolver.openOutputStream(this)?.use { os ->
                                os.bufferedWriter().use {
                                    it.write(DeviceInfo.get(this@LogActivity))
                                    it.write(logView.currentLog)
                                }
                            }
                        }?.let { toast(it) }
                }
            }
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
            setSupportActionBar(logToolbar.toolbar)
            this@LogActivity.logView = logView
            if (intent.hasExtra(FROM_CRASH)) {
                supportActionBar!!.setTitle(R.string.crash_logs)
                clearButton.visibility = View.GONE
                copyButton.visibility = View.GONE
                AlertDialog
                    .Builder(this@LogActivity)
                    .setTitle(R.string.app_crash)
                    .setMessage(R.string.app_crash_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                logView.append("--------- Crash stacktrace")
                logView.append(intent.getStringExtra(CRASH_STACK_TRACE) ?: "<empty>")
                logView.setLogcat(Logcat(TrimeApplication.getLastPid()))
            } else if (intent.hasExtra(FROM_DEPLOY)) {
                supportActionBar!!.setTitle(R.string.deploy_failure)
                clearButton.visibility = View.GONE
                logView.append(intent.getStringExtra(DEPLOY_FAILURE_TRACE) ?: "<empty>")
            } else {
                supportActionBar!!.apply {
                    setDisplayHomeAsUpEnabled(true)
                    setTitle(R.string.real_time_logs)
                }
                copyButton.visibility = View.GONE
                logView.setLogcat(Logcat())
            }
            clearButton.setOnClickListener {
                logView.clear()
            }
            exportButton.setOnClickListener {
                launcher.launch("$packageName-${iso8601UTCDateTime()}.txt")
            }
            copyButton.setOnClickListener {
                val data = ClipData.newPlainText("log", logView.currentLog)
                clipboardManager.setPrimaryClip(data)
                if (clipboardManager.hasPrimaryClip()) {
                    toast(R.string.copy_done)
                }
            }
            jumpToBottomButton.setOnClickListener {
                logView.scrollToBottom()
            }
        }
        registerLauncher()
    }
}
