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
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.util.DeployNotification
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

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
        if (!RimeDataSync.isStorageAvailable(applicationContext)) {
            if (RimeDataSync.usesExternalSync(applicationContext)) {
                // Unlike a manual deploy this path never reaches
                // Rime.deploy(), so notify explicitly: a periodic sync that
                // silently fails because the folder is missing is not
                // actionable otherwise.
                DeployNotification.showExternalSyncUnavailable()
            }
            Timber.w("Background sync skipped: storage not available")
            return Result.failure()
        }
        val rime = RimeDaemon.createSession(javaClass.name)
        try {
            // syncUserData() imports the external tree, queues an export
            // request and returns its deferred outcome; it returns null when
            // the sync was aborted (no external access or failed import).
            val exportRequest =
                rime.runOnReady {
                    syncUserData()
                }
            if (exportRequest == null) {
                Timber.w("Background sync aborted before scheduling")
                lastSyncStatus = false
                return Result.retry()
            }
            // Await this sync's own maintenance and post-maintenance export:
            // requests are consumed in maintenance order, so the deferred is
            // completed by our export (or with failure on maintenance
            // failure), never by an unrelated maintenance.
            val exported = withTimeoutOrNull(MAINTENANCE_TIMEOUT) { exportRequest.await() }
            if (exported == null) {
                // The maintenance did not finish in time: withdraw the request
                // so that a later maintenance does not consume the stale head
                // and complete it for a waiter that already gave up.
                RimeDataSync.cancelExportRequest(exportRequest)
            }
            lastSyncTime = System.currentTimeMillis()
            lastSyncStatus = exported == true
            return if (exported == true) {
                Result.success()
            } else {
                // The maintenance or the export failed, or the timeout
                // elapsed; retry on the next backoff.
                Result.retry()
            }
        } finally {
            RimeDaemon.destroySession(javaClass.name)
        }
    }

    companion object {
        private val MAINTENANCE_TIMEOUT = 5.minutes
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
