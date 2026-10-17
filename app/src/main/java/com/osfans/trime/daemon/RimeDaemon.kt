// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.daemon

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeMaintenanceMutex
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.RimeUnavailableException
import com.osfans.trime.core.lifecycleScope
import com.osfans.trime.core.whenReady
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.util.DeployNotification
import com.osfans.trime.util.appContext
import com.osfans.trime.util.consumeOutput
import com.osfans.trime.util.subprocess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import splitties.systemservices.notificationManager
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manage the singleton instance of [Rime]
 *
 * To use rime, client should call [createSession] to obtain a [RimeSession],
 * and call [destroySession] on client destroyed. Client should not leak the instance of [RimeApi],
 * and must use [RimeSession] to access rime functionalities. Cached UI presentation
 * may use [readCached] without borrowing another client's session.
 *
 * The instance of [Rime] always exists,but whether the dispatcher runs and callback works depend on clients, i.e.
 * if no clients are connected, the engine will be shut down on the lifecycle worker.
 *
 * Functions are thread-safe in this class.
 *
 * Adapted from [fcitx5-android/FcitxDaemon.kt](https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/daemon/FcitxDaemon.kt)
 */
object RimeDaemon {
    private const val STARTUP_RETRY_DELAY_MS = 1_000L
    private const val STARTUP_RETRY_MAX_ATTEMPTS = 30

    private val realRime by lazy { Rime() }

    private val rimeImpl by lazy { object : RimeApi by realRime {} }

    private val sessions = ConcurrentHashMap<String, Session>()

    private val lock = ReentrantLock()
    private val lifecycleChanges = Channel<Unit>(Channel.CONFLATED)
    private var restartRequested = false
    private var fullCheckRequested = false
    private val restartNotifications = mutableSetOf<Int>()
    private var manualRetryRequested = false
    private val manualRetryWaiters = mutableListOf<CompletableDeferred<Unit>>()
    private var lifecycleGeneration = 0L
    private var startupRetryEpoch = 0L
    private var startupRetryBlocked = false
    private var retryExhaustionPending: Long? = null
    private var processExitRequested = false

    @Volatile
    private var startupRetryJob: Job? = null

    private data class PendingLifecycleChange(
        val restart: Boolean,
        val fullCheck: Boolean,
        val notifications: List<Int>,
        val manualRetry: Boolean,
        val manualWaiters: List<CompletableDeferred<Unit>>,
        val generation: Long,
        val retryExhaustionEpoch: Long?,
    )

    private data class AbandonedRequests(
        val notifications: List<Int>,
        val waiters: List<CompletableDeferred<Unit>>,
    )

    internal val engineState: StateFlow<RimeLifecycle.State>
        get() = realRime.lifecycle.stateFlow

    private class Session(private val name: String) : RimeSession {
        private var clientScope: CoroutineScope? = null
        private fun isEstablished() = sessions[name] === this

        private inline fun <T> ensureEstablished(block: () -> T) = if (isEstablished()) {
            block()
        } else {
            throw RimeSessionClosedException(name)
        }

        override fun <T> run(block: suspend RimeApi.() -> T): T = ensureEstablished {
            runBlocking { block(rimeImpl) }
        }

        override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T = ensureEstablished {
            realRime.lifecycle.whenReady { ensureEstablished { block(rimeImpl) } }
        }

        override fun runIfReady(block: suspend RimeApi.() -> Unit) {
            ensureEstablished {
                if (realRime.isReady) {
                    lifecycleScope.launch {
                        if (isEstablished() && realRime.isReady) block(rimeImpl)
                    }
                }
            }
        }

        override val lifecycleScope: CoroutineScope
            get() = lock.withLock {
                ensureEstablished {
                    clientScope?.takeIf { it.isActive } ?: CoroutineScope(
                        realRime.lifecycleScope.coroutineContext + SupervisorJob(realRime.lifecycleScope.coroutineContext[Job]),
                    ).also { clientScope = it }
                }
            }

        fun close() {
            clientScope?.cancel()
        }
    }

    /** A detached session returned only while the direct-boot process is being replaced. */
    private object ProcessExitSession : RimeSession {
        private val cancelledScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).apply { cancel() }

        override fun <T> run(block: suspend RimeApi.() -> T): T = runBlocking { block(rimeImpl) }

        override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T = throw RimeUnavailableException(
            IllegalStateException("Rime process replacement is in progress"),
        )

        override fun runIfReady(block: suspend RimeApi.() -> Unit) = Unit

        override val lifecycleScope: CoroutineScope
            get() = cancelledScope
    }

    private fun cancelStartupRetryLocked() {
        startupRetryJob?.cancel()
        startupRetryJob = null
    }

    private fun resetStartupRetryLocked() {
        cancelStartupRetryLocked()
        startupRetryEpoch += 1
        startupRetryBlocked = false
        retryExhaustionPending = null
    }

    private fun sessionEndedFailure() = RimeUnavailableException(
        IllegalStateException("All Rime sessions closed before startup completed"),
    )

    /**
     * Poll app-scoped storage after reboot, then wake the lifecycle worker once.
     * This job never starts or stops native Rime itself. When the bounded poll is
     * exhausted, the worker publishes a retryable failure instead of leaving the
     * engine indefinitely stopped with disabled recovery UI.
     */
    private fun scheduleStartupRetry() {
        lock.withLock {
            if (sessions.isEmpty()) return
            if (startupRetryJob?.isActive == true) return
            if (startupRetryBlocked) return
            val retryEpoch = ++startupRetryEpoch
            Timber.i("Scheduling Rime startup retry until storage is available")
            val retryJob =
                TrimeApplication.getInstance().coroutineScope.launch(Dispatchers.IO) {
                    val owner = currentCoroutineContext()[Job]
                    repeat(STARTUP_RETRY_MAX_ATTEMPTS) { attempt ->
                        delay(STARTUP_RETRY_DELAY_MS)
                        val canPoll = lock.withLock {
                            startupRetryJob === owner &&
                                sessions.isNotEmpty() &&
                                realRime.lifecycle.currentState in
                                setOf(RimeLifecycle.State.STOPPED, RimeLifecycle.State.FAILED)
                        }
                        if (!canPoll) return@launch
                        if (!realRime.isStorageReadyForStartup()) {
                            Timber.d("Rime startup retry ${attempt + 1}: storage still unavailable")
                            return@repeat
                        }
                        val shouldDispatch = lock.withLock {
                            if (startupRetryJob !== owner ||
                                sessions.isEmpty() ||
                                startupRetryEpoch != retryEpoch ||
                                realRime.lifecycle.currentState !in
                                setOf(RimeLifecycle.State.STOPPED, RimeLifecycle.State.FAILED)
                            ) {
                                false
                            } else {
                                // Release ownership before signalling. If storage becomes
                                // unavailable again, the worker can schedule a fresh batch.
                                startupRetryJob = null
                                true
                            }
                        }
                        if (shouldDispatch) lifecycleChanges.trySend(Unit)
                        return@launch
                    }
                    val exhausted =
                        lock.withLock {
                            if (startupRetryJob !== owner ||
                                sessions.isEmpty() ||
                                startupRetryEpoch != retryEpoch ||
                                realRime.lifecycle.currentState !in
                                setOf(RimeLifecycle.State.STOPPED, RimeLifecycle.State.FAILED)
                            ) {
                                false
                            } else {
                                startupRetryJob = null
                                startupRetryBlocked = true
                                retryExhaustionPending = retryEpoch
                                true
                            }
                        }
                    if (exhausted) {
                        Timber.w("Rime startup retry exhausted while sessions remain connected")
                        lifecycleChanges.trySend(Unit)
                    }
                }
            startupRetryJob = retryJob
            retryJob.invokeOnCompletion {
                lock.withLock {
                    if (startupRetryJob === retryJob) startupRetryJob = null
                }
            }
        }
    }

    fun createSession(name: String): RimeSession = lock.withLock {
        if (processExitRequested) return@withLock ProcessExitSession
        if (sessions.containsKey(name)) {
            return@withLock sessions.getValue(name)
        }
        val session = Session(name)
        sessions[name] = session
        lifecycleChanges.trySend(Unit)
        return@withLock session
    }

    fun destroySession(name: String) {
        val abandoned = lock.withLock {
            val session = sessions.remove(name) ?: return
            session.close()
            val requests =
                if (sessions.isEmpty()) {
                    lifecycleGeneration += 1
                    resetStartupRetryLocked()
                    AbandonedRequests(
                        restartNotifications.toList(),
                        manualRetryWaiters.toList(),
                    ).also {
                        restartRequested = false
                        fullCheckRequested = false
                        restartNotifications.clear()
                        manualRetryRequested = false
                        manualRetryWaiters.clear()
                    }
                } else {
                    null
                }
            lifecycleChanges.trySend(Unit)
            requests
        }
        abandoned?.notifications?.forEach(notificationManager::cancel)
        val failure = sessionEndedFailure()
        abandoned?.waiters?.forEach { it.completeExceptionally(failure) }
    }

    /**
     * Reuse a session for remote service
     */
    fun getFirstSessionOrNull(): RimeSession? = lock.withLock {
        sessions.firstNotNullOfOrNull { it.value }
            ?: ProcessExitSession.takeIf { processExitRequested }
    }

    /**
     * Read presentation caches without borrowing a session that another client can close.
     * Keep [block] short: cached getters only, no waiting, native work, callbacks,
     * or escaping RimeApi/flows. Return immutable values for calculation outside the lock.
     * This synchronizes with client teardown, not with updates on the Rime dispatcher;
     * it does not make different caches a single response snapshot.
     */
    internal fun <T> readCached(block: RimeApi.() -> T): T? = lock.withLock {
        if (sessions.isEmpty() || processExitRequested) null else block(rimeImpl)
    }

    /**
     * Best-effort native shutdown before replacing the direct-boot process after unlock.
     * New lifecycle work is invalidated first, then an already-running engine is stopped
     * under the same maintenance mutex used by deployment and normal session teardown.
     */
    suspend fun shutdownForProcessExit() {
        val abandoned = lock.withLock {
            processExitRequested = true
            lifecycleGeneration += 1
            resetStartupRetryLocked()
            sessions.values.forEach(Session::close)
            sessions.clear()
            AbandonedRequests(
                restartNotifications.toList(),
                manualRetryWaiters.toList(),
            ).also {
                restartRequested = false
                fullCheckRequested = false
                restartNotifications.clear()
                manualRetryRequested = false
                manualRetryWaiters.clear()
            }
        }
        abandoned.notifications.forEach(notificationManager::cancel)
        val failure = sessionEndedFailure()
        abandoned.waiters.forEach { it.completeExceptionally(failure) }

        RimeMaintenanceMutex.withLock {
            when (realRime.lifecycle.currentState) {
                RimeLifecycle.State.READY -> {
                    realRime.beginShutdown()
                    realRime.finishShutdown()
                }

                RimeLifecycle.State.STOPPING -> realRime.finishShutdown()

                else -> Unit
            }
        }
    }

    private var restartId = 0

    init {
        DeployNotification.ensureChannel()
        // Session, restart, and automatic retry events change the native lifecycle here.
        // Never hold the session lock while waiting for deployment, shutdown, or restart.
        TrimeApplication.getInstance().coroutineScope.launch(Dispatchers.IO) {
            for (change in lifecycleChanges) {
                var activeNotifications = emptyList<Int>()
                var activeManualWaiters = emptyList<CompletableDeferred<Unit>>()
                var activeGeneration: Long? = null
                var retainPending = false
                var completionFailure: Throwable? = null
                try {
                    RimeMaintenanceMutex.withLock {
                        val pending = lock.withLock {
                            PendingLifecycleChange(
                                restartRequested,
                                fullCheckRequested,
                                restartNotifications.toList(),
                                manualRetryRequested,
                                manualRetryWaiters.toList(),
                                lifecycleGeneration,
                                retryExhaustionPending,
                            ).also {
                                restartRequested = false
                                fullCheckRequested = false
                                restartNotifications.clear()
                                manualRetryRequested = false
                                manualRetryWaiters.clear()
                                retryExhaustionPending = null
                            }
                        }
                        activeNotifications = pending.notifications
                        activeManualWaiters = pending.manualWaiters
                        activeGeneration = pending.generation
                        if (pending.manualRetry) {
                            lock.withLock { cancelStartupRetryLocked() }
                        }
                        val generationCurrent = lock.withLock {
                            pending.generation == lifecycleGeneration
                        }
                        if (!generationCurrent) {
                            completionFailure = sessionEndedFailure()
                        } else {
                            val exhaustionCause =
                                pending.retryExhaustionEpoch?.let {
                                    IllegalStateException(
                                        "Rime storage remained unavailable after " +
                                            "$STARTUP_RETRY_MAX_ATTEMPTS startup checks",
                                    )
                                }
                            val storageUnavailable =
                                exhaustionCause != null && !realRime.isStorageReadyForStartup()
                            val exhaustionApplied =
                                if (storageUnavailable) {
                                    val cause = checkNotNull(exhaustionCause)
                                    lock.withLock {
                                        pending.generation == lifecycleGeneration &&
                                            pending.retryExhaustionEpoch == startupRetryEpoch &&
                                            startupRetryBlocked &&
                                            sessions.isNotEmpty() &&
                                            realRime.failStartup(cause)
                                    }
                                } else {
                                    false
                                }
                            if (exhaustionApplied) {
                                completionFailure = RimeUnavailableException(exhaustionCause)
                                // Preserve deployment intent for the explicit retry, but
                                // retire ongoing notifications and this batch's waiters.
                                lock.withLock {
                                    if (pending.generation == lifecycleGeneration) {
                                        restartRequested = restartRequested || pending.restart
                                        fullCheckRequested = fullCheckRequested || pending.fullCheck
                                    }
                                }
                            } else {
                                pending.retryExhaustionEpoch?.let { epoch ->
                                    lock.withLock {
                                        if (startupRetryEpoch == epoch) startupRetryBlocked = false
                                    }
                                }
                                if (realRime.lifecycle.currentState == RimeLifecycle.State.STARTING) {
                                    realRime.lifecycle.whenReady {}
                                }
                                val stopping = lock.withLock {
                                    (
                                        pending.generation == lifecycleGeneration &&
                                            realRime.isReady &&
                                            (sessions.isEmpty() || pending.restart)
                                        ).also {
                                        if (it) realRime.beginShutdown()
                                    }
                                }
                                if (stopping) realRime.finishShutdown()
                                val sessionState = lock.withLock {
                                    (pending.generation == lifecycleGeneration) to sessions.isNotEmpty()
                                }
                                if (!sessionState.first) {
                                    completionFailure = sessionEndedFailure()
                                } else if (sessionState.second &&
                                    realRime.lifecycle.currentState in
                                    setOf(RimeLifecycle.State.STOPPED, RimeLifecycle.State.FAILED)
                                ) {
                                    if (realRime.startup(pending.fullCheck)) {
                                        lock.withLock {
                                            if (pending.generation == lifecycleGeneration) {
                                                resetStartupRetryLocked()
                                            }
                                        }
                                        realRime.lifecycle.whenReady {}
                                    } else {
                                        retainPending = lock.withLock {
                                            if (pending.generation != lifecycleGeneration || sessions.isEmpty()) {
                                                false
                                            } else {
                                                restartRequested = restartRequested || pending.restart
                                                fullCheckRequested = fullCheckRequested || pending.fullCheck
                                                restartNotifications.addAll(pending.notifications)
                                                manualRetryWaiters.addAll(pending.manualWaiters)
                                                true
                                            }
                                        }
                                        if (retainPending) scheduleStartupRetry()
                                    }
                                } else if (!sessionState.second) {
                                    lock.withLock {
                                        if (pending.generation == lifecycleGeneration) resetStartupRetryLocked()
                                    }
                                    completionFailure = sessionEndedFailure()
                                }
                            }
                        }
                    }
                } catch (error: RimeUnavailableException) {
                    completionFailure = error
                    // The engine published the cause and failure notification. Keep
                    // this worker alive for an explicit retry or a new session.
                } catch (error: Throwable) {
                    completionFailure = error
                    throw error
                } finally {
                    if (!retainPending) {
                        if (completionFailure == null && activeGeneration != null) {
                            val generationStillCurrent = lock.withLock {
                                activeGeneration == lifecycleGeneration
                            }
                            if (!generationStillCurrent) completionFailure = sessionEndedFailure()
                        }
                        activeNotifications.forEach(notificationManager::cancel)
                        activeManualWaiters.forEach { waiter ->
                            completionFailure?.let(waiter::completeExceptionally) ?: waiter.complete(Unit)
                        }
                    }
                }
            }
        }
        TrimeApplication.getInstance().coroutineScope.launch {
            realRime.messageFlow.collect {
                handleRimeMessage(it)
            }
        }
    }

    suspend fun retryFailedStartup() = withContext(Dispatchers.IO) {
        val completion = CompletableDeferred<Unit>()
        val enqueued = lock.withLock {
            if (sessions.isEmpty()) return@withLock false
            resetStartupRetryLocked()
            manualRetryRequested = true
            manualRetryWaiters += completion
            lifecycleChanges.trySend(Unit)
            true
        }
        if (enqueued) completion.await()
    }

    private inline fun sendNotification(
        id: Int,
        buildAction: NotificationCompat.Builder.() -> Unit,
    ) {
        val builder =
            NotificationCompat
                .Builder(appContext, DeployNotification.CHANNEL_ID)
                .setContentTitle(appContext.getString(R.string.rime_daemon))
        builder.buildAction()
        builder.build().let { notificationManager.notify(id, it) }
    }

    /**
     * Restart Rime instance to deploy while keep the session
     */
    fun restartRime(fullCheck: Boolean = false) = lock.withLock {
        val id = restartId++
        if (!fullCheck) {
            sendNotification(id) {
                setSmallIcon(R.drawable.ic_baseline_sync_24)
                setContentTitle(appContext.getString(R.string.rime_daemon))
                setContentText(appContext.getString(R.string.restarting_rime))
                setOngoing(true)
                setProgress(100, 0, true)
                setPriority(NotificationCompat.PRIORITY_HIGH)
            }
        }
        restartRequested = true
        fullCheckRequested = fullCheckRequested || fullCheck
        restartNotifications.add(id)
        lifecycleChanges.trySend(Unit)
    }

    private suspend fun handleRimeMessage(it: RimeMessage<*>) {
        if (it is RimeMessage.DeployMessage) {
            when (it.data) {
                RimeMessage.DeployMessage.State.Start -> {
                    DeployNotification.showProgress()
                }

                RimeMessage.DeployMessage.State.Success -> {
                    DeployNotification.showSuccess()
                }

                RimeMessage.DeployMessage.State.Failure -> {
                    val log = withContext(Dispatchers.IO) {
                        try {
                            val process = subprocess("logcat", "-v", "brief", "-d", "-t", "2000", "*:W")
                            process.consumeOutput { reader ->
                                val text = reader.readText()
                                if (process.waitFor() != 0) throw IOException("Deployment log command failed")
                                text
                            }
                        } catch (e: IOException) {
                            "Unable to collect deployment log: ${e.stackTraceToString()}"
                        }
                    }.takeLast(128_000)
                    val intent =
                        Intent(appContext, LogActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra(LogActivity.FROM_DEPLOY, true)
                            putExtra(LogActivity.DEPLOY_FAILURE_TRACE, log)
                        }
                    val pendingIntent =
                        PendingIntent.getActivity(
                            appContext,
                            0,
                            intent,
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    DeployNotification.showFailure(pendingIntent)
                }
            }
        }
    }
}
