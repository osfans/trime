/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.osfans.trime.R
import com.osfans.trime.data.sync.SyncStats
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.NavigationRoute
import splitties.systemservices.notificationManager
import timber.log.Timber

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
    private const val PARTIAL_COPY_MESSAGE_ID = 2332
    private const val EXTERNAL_SYNC_FALLBACK_MESSAGE_ID = 2333

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

    fun showExportFailure() {
        ensureChannel()
        NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.rime_daemon))
            .setSmallIcon(R.drawable.ic_baseline_warning_24)
            .setColor(Color.YELLOW)
            .setContentText(appContext.getString(R.string.sync_export_failure))
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
            .let { notificationManager.notify(MESSAGE_ID, it) }
    }

    fun cancel() {
        notificationManager.cancel(MESSAGE_ID)
    }

    fun notifyPartialCopyIfNeeded(
        stats: SyncStats,
        operation: String,
    ): SyncStats {
        if (stats.failed <= 0) {
            Timber.i(
                "%s: copied=%d skipped=%d deleted=%d bytesCopied=%d",
                operation,
                stats.copied,
                stats.skipped,
                stats.deleted,
                stats.bytesCopied,
            )
            return stats
        }
        Timber.w(
            "%s completed with failures: failed=%d copied=%d skipped=%d deleted=%d bytesCopied=%d",
            operation,
            stats.failed,
            stats.copied,
            stats.skipped,
            stats.deleted,
            stats.bytesCopied,
        )
        ensureChannel()
        NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.rime_daemon))
            .setSmallIcon(R.drawable.ic_baseline_warning_24)
            .setContentText(appContext.getString(R.string.sync_partial_copy_failure))
            .setStyle(
                NotificationCompat
                    .BigTextStyle()
                    .bigText(
                        appContext.getString(
                            R.string.sync_partial_copy_failure_detail,
                            stats.failed,
                            operation,
                        ),
                    ),
            ).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
            .let { notificationManager.notify(PARTIAL_COPY_MESSAGE_ID, it) }
        return stats
    }

    fun showExternalSyncFallback() {
        val message = appContext.getString(R.string.external_sync_missing_tree_fallback)
        val contentIntent =
            PendingIntent.getActivity(
                appContext,
                EXTERNAL_SYNC_FALLBACK_MESSAGE_ID,
                Intent(appContext, MainActivity::class.java).apply {
                    action = Intent.ACTION_RUN
                    putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, NavigationRoute.Profile)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        ensureChannel()
        NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.rime_daemon))
            .setSmallIcon(R.drawable.ic_baseline_warning_24)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
            .let { notificationManager.notify(EXTERNAL_SYNC_FALLBACK_MESSAGE_ID, it) }
    }
}
