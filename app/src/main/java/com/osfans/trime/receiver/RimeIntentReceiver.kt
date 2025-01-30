/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.osfans.trime.BuildConfig
import com.osfans.trime.daemon.RimeDaemon
import timber.log.Timber

class RimeIntentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val rime = RimeDaemon.getFirstSessionOrNull() ?: return Timber.w("No active rime session, skipping")
        Timber.i("Received broadcast ${intent.action}")
        when (intent.action) {
            ACTION_DEPLOY -> {
                Timber.i("try to start maintenance ...")
                RimeDaemon.restartRime(true)
            }
            ACTION_SYNC_USER_DATA -> {
                Timber.i("try to sync rime user data ...")
                rime.run { syncUserData() }
            }
            else -> {}
        }
    }

    companion object {
        const val ACTION_DEPLOY = "${BuildConfig.APPLICATION_ID}.action.DEPLOY"
        const val ACTION_SYNC_USER_DATA = "${BuildConfig.APPLICATION_ID}.action.SYNC_USER_DATA"
    }
}
