// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** 接收 Intent 廣播事件  */
class IntentReceiver(
    private val service: TrimeInputMethodService,
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val command = intent.action ?: return
        Timber.d("Received command: $command")
        when (command) {
            COMMAND_DEPLOY ->
                service.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        RimeDaemon.restartRime(true)
                    }
                    context.toast(R.string.deploy_finish, Toast.LENGTH_LONG)
                }
            COMMAND_SYNC ->
                service.lifecycleScope.launch(Dispatchers.IO) {
                    RimeDaemon.syncUserData()
                }
            else -> return
        }
    }

    fun registerReceiver(context: Context) {
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(COMMAND_DEPLOY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(COMMAND_SYNC),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        context.registerReceiver(this, IntentFilter(Intent.ACTION_SHUTDOWN))
    }

    fun unregisterReceiver(context: Context) {
        context.unregisterReceiver(this)
    }

    companion object {
        private const val COMMAND_DEPLOY = "com.osfans.trime.DEPLOY"
        private const val COMMAND_SYNC = "com.osfans.trime.SYNC"
    }
}
