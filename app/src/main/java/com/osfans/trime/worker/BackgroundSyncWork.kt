/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class BackgroundSyncWork(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    private val prefs = AppPrefs.defaultInstance()

    override suspend fun doWork(): Result {
        try {
            return doBackgroundSync()
        } catch (e: Exception) {
            Timber.e(e, "Background sync job failed.")
            return Result.success()
        }
    }

    private suspend fun doBackgroundSync(): Result {
        Timber.i("Executing background sync job")
        if (!prefs.profile.timingBackgroundSyncEnabled) {
            return Result.failure()
        }

        prefs.profile.lastBackgroundSyncTime = System.currentTimeMillis()
        val result =
            withContext(Dispatchers.IO) {
                RimeDaemon.syncUserData()
            }.also { prefs.profile.lastSyncStatus = it }

        Timber.i("Done background sync work request")
        return if (result) Result.success() else Result.failure()
    }

    companion object {
        private const val TIMING_BACKGROUND_SYNC_KEY = "timing_background_sync"

        fun start(context: Context) {
            Timber.i("Start BackgroundSyncWork")
            internalStart(context.applicationContext)
        }

        private fun internalStart(context: Context) {
            val instance = WorkManager.getInstance(context)
            if (!AppPrefs.defaultInstance().profile.timingBackgroundSyncEnabled) {
                instance.cancelUniqueWork(TIMING_BACKGROUND_SYNC_KEY)
                return
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()

            val dueMillis = AppPrefs.defaultInstance().profile.timingBackgroundSyncSetTime
            val timeDiff = dueMillis - System.currentTimeMillis()
            val workRequest =
                OneTimeWorkRequestBuilder<BackgroundSyncWork>()
                    .setConstraints(constraints)
                    .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                    .build()
            instance.enqueueUniqueWork(TIMING_BACKGROUND_SYNC_KEY, ExistingWorkPolicy.KEEP, workRequest)
        }
    }
}
