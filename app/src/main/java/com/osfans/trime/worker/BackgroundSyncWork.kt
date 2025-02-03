/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import timber.log.Timber
import java.util.concurrent.TimeUnit

class BackgroundSyncWork(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        try {
            Timber.i("Starting background sync ...")
            return doBackgroundSync()
        } catch (e: Exception) {
            Timber.e(e, "Background sync job failed.")
            return Result.retry()
        }
    }

    private suspend fun doBackgroundSync(): Result {
        if (!enable) {
            return Result.failure()
        }
        val rime = RimeDaemon.getFirstSessionOrNull() ?: return Result.retry()
        val success = rime.runOnReady { syncUserData() }
        lastSyncTime = System.currentTimeMillis()
        lastSyncStatus = success

        return if (success) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_BACKGROUND_SYNC_KEY = "periodic_background_sync"

        private val prefs = AppPrefs.defaultInstance().profile
        private val enable by prefs.periodicBackgroundSync
        private val interval by prefs.periodicBackgroundSyncInterval
        private var lastSyncStatus by prefs.lastBackgroundSyncStatus
        private var lastSyncTime by prefs.lastBackgroundSyncTime

        fun start(context: Context) {
            Timber.i("BackgroundSyncWork scheduled!")
            internalStart(context, ExistingPeriodicWorkPolicy.UPDATE)
        }

        fun forceStart(context: Context) {
            internalStart(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        }

        private fun internalStart(
            context: Context,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val instance = WorkManager.getInstance(context.applicationContext)
            if (!enable) {
                instance.cancelUniqueWork(PERIODIC_BACKGROUND_SYNC_KEY)
                Timber.i("BackgroundSyncWork canceled!")
                return
            }
            val constraints =
                Constraints
                    .Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<BackgroundSyncWork>(
                    interval.toLong(),
                    TimeUnit.MINUTES,
                    5,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .build()
            instance.enqueueUniquePeriodicWork(
                PERIODIC_BACKGROUND_SYNC_KEY,
                policy,
                workRequest,
            )
        }
    }
}
