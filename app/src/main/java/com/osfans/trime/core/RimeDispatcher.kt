// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * RimeDispatcher is a wrapper of a single-threaded executor that runs RimeController.
 * It provides a coroutine-based interface for dispatching jobs to the executor.
 * It also provides a stop() method to gracefully stop the executor and return the remaining jobs.
 *
 * Adapted from [fcitx5-android/FcitxDispatcher.kt](https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/core/FcitxDispatcher.kt).
 */
class RimeDispatcher(
    private val controller: RimeController,
) : CoroutineDispatcher() {
    interface RimeController {
        fun nativeStartup()

        fun nativeFinalize()

        fun nativeFailure(error: Throwable) {
            Timber.e(error, "Rime dispatcher failed")
        }
    }

    class WrappedRunnable(
        private val runnable: Runnable,
        private val name: String? = null,
        private val context: CoroutineContext = EmptyCoroutineContext,
    ) : Runnable by runnable {
        private val time = System.nanoTime()
        var started = false
            private set

        private val delta
            get() = (System.nanoTime() - time) / 1_000_000

        override fun run() {
            if (delta > JOB_WAITING_LIMIT) {
                Timber.w("${toString()} has waited $delta ms to get run since created!")
            }
            started = true
            runnable.run()
        }

        override fun toString(): String = "WrappedRunnable[${name ?: hashCode()}]"

        fun reject(error: Throwable) {
            if (context[Job] == null) return
            context.cancel(RimeUnavailableException(error))
            Dispatchers.IO.dispatch(context, runnable)
        }

        companion object {
            fun wakeup() = WrappedRunnable({}, "Wakeup")
        }
    }

    companion object {
        private const val JOB_WAITING_LIMIT = 2000L // ms
    }

    @Volatile private var dispatcherThread: Thread? = null

    private val internalDispatcher =
        Executors
            .newSingleThreadExecutor {
                Thread(it, "rime-main").also { thread -> dispatcherThread = thread }
            }.asCoroutineDispatcher()

    private val internalScope = CoroutineScope(internalDispatcher)

    private val mutex = Mutex()

    private val queue = LinkedBlockingQueue<WrappedRunnable>()
    private val submissionLock = Any()

    private val isRunning = AtomicBoolean(false)
    private var stopped = CompletableDeferred(Unit)

    @Volatile private var failure: Throwable? = null

    /**
     * Start the dispatcher
     * This function returns immediately
     */
    fun start() {
        Timber.d("RimeDispatcher start()")
        val completion = synchronized(submissionLock) {
            if (!stopped.isCompleted || !isRunning.compareAndSet(false, true)) return
            failure = null
            CompletableDeferred<Unit>().also { stopped = it }
        }
        internalScope.launch {
            mutex.withLock {
                try {
                    Timber.d("nativeStartup()")
                    controller.nativeStartup()
                    while (isActive) {
                        failure?.let { throw it }
                        val block = queue.take()
                        block.run()
                        if (!isRunning.get() && queue.isEmpty()) break
                    }
                } catch (e: Throwable) {
                    fail(e)
                    if (e !is Exception && e !is LinkageError) throw e
                } finally {
                    try {
                        Timber.i("nativeFinalize()")
                        controller.nativeFinalize()
                    } catch (e: Throwable) {
                        fail(e)
                        if (e !is Exception && e !is LinkageError) throw e
                    } finally {
                        try {
                            val error = synchronized(submissionLock) {
                                isRunning.set(false)
                                failure?.also { error ->
                                    while (true) (queue.poll() ?: break).reject(error)
                                }
                            }
                            // Failure publication can wait for slow message subscribers. Never
                            // hold the submission lock while waiting for their main-thread work:
                            // a concurrent UI dispatch must be able to reject without blocking.
                            error?.let(controller::nativeFailure)
                        } finally {
                            // Keep restart/stop behind the entire failure notification, even
                            // though other callers can now acquire the submission lock.
                            completion.complete(Unit)
                        }
                    }
                }
            }
        }
    }

    fun fail(error: Throwable) {
        synchronized(submissionLock) {
            if (failure == null) failure = error
            if (isRunning.compareAndSet(true, false)) queue.offer(WrappedRunnable.wakeup())
        }
    }

    /**
     * Stop the dispatcher
     * This function blocks until fully stopped
     */
    fun stop(): List<Runnable> {
        Timber.i("RimeDispatcher stop()")
        val completion = synchronized(submissionLock) {
            if (isRunning.compareAndSet(true, false)) queue.offer(WrappedRunnable.wakeup())
            stopped
        }
        runBlocking { completion.await() }
        return emptyList()
    }

    fun close() {
        stop()
        internalDispatcher.close()
    }

    suspend fun <T> runConfined(block: suspend () -> T): T = withContext(this) {
        // A rejected NonCancellable continuation can resume on the fallback
        // executor. It must never call the already-finalized native engine.
        check(Thread.currentThread() === dispatcherThread) { "Rime dispatcher is stopped" }
        block()
    }

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        synchronized(submissionLock) {
            if (isRunning.get()) {
                queue.offer(WrappedRunnable(block, context = context))
                return
            }
        }
        // Resume rejected continuations as cancellations, rather than strand a
        // caller waiting for a queue that has already finished its stop barrier.
        context.cancel(CancellationException("Rime dispatcher is stopped"))
        Dispatchers.IO.dispatch(context, block)
    }
}
