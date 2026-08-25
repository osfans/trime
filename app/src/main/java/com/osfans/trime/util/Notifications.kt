/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.osfans.trime.R
import splitties.systemservices.notificationManager

fun createNotificationChannel(
    id: String,
    name: String,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
            NotificationChannel(
                id,
                name,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = id }
        notificationManager.createNotificationChannel(channel)
    }
}

object DeployNotification {
    const val CHANNEL_ID = "rime-daemon"
    const val MESSAGE_ID = 2331

    fun ensureChannel() {
        createNotificationChannel(CHANNEL_ID, appContext.getString(R.string.rime_daemon))
    }

    fun showProgress() {
        ensureChannel()
        NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.rime_daemon))
            .setSmallIcon(R.drawable.ic_baseline_refresh_reversed_24)
            .setContentText(appContext.getString(R.string.deploy_progress))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
            .let { notificationManager.notify(MESSAGE_ID, it) }
    }

    fun showSuccess() {
        ensureChannel()
        NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.rime_daemon))
            .setSmallIcon(R.drawable.ic_baseline_refresh_reversed_24)
            .setColor(Color.GREEN)
            .setContentText(appContext.getString(R.string.deploy_finish))
            .setOngoing(false)
            .setTimeoutAfter(3000L)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
            .let { notificationManager.notify(MESSAGE_ID, it) }
    }

    fun showFailure(contentIntent: PendingIntent) {
        ensureChannel()
        NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.rime_daemon))
            .setSmallIcon(R.drawable.ic_baseline_warning_24)
            .setColor(Color.YELLOW)
            .setContentText(appContext.getString(R.string.view_deploy_failure_log))
            .setContentIntent(contentIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
            .let { notificationManager.notify(MESSAGE_ID, it) }
    }

    fun cancel() {
        notificationManager.cancel(MESSAGE_ID)
    }
}
