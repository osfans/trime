// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * RimeDispatcher is a wrapper of a single-threaded executor that runs RimeLooper.
 * It provides a coroutine-based interface for dispatching jobs to the executor.
 * It also provides a stop() method to gracefully stop the executor and return the remaining jobs.
 *
 * Adapted from [fcitx5-android/FcitxDispatcher.kt](https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/core/FcitxDispatcher.kt).
 */
class RimeDispatcher(
    private val looper: RimeLooper,
) : CoroutineDispatcher() {
    interface RimeLooper {
        fun nativeStartup(fullCheck: Boolean)

        fun nativeFinalize()
    }

    class WrappedRunnable(
        private val runnable: Runnable,
        private val name: String? = null,
    ) : Runnable by runnable {
        private val time = System.currentTimeMillis()
        var started = false
            private set

        private val delta
            get() = System.currentTimeMillis() - time

        override fun run() {
            if (delta > JOB_WAITING_LIMIT) {
                Timber.w("${toString()} has waited $delta ms to get run since created!")
            }
            started = true
            runnable.run()
        }

        override fun toString(): String = "WrappedRunnable[${name ?: hashCode()}]"
    }

    companion object {
        private const val JOB_WAITING_LIMIT = 2000L // ms
    }

    private val internalDispatcher =
        Executors
            .newSingleThreadExecutor {
                Thread(it, "rime-main")
            }.asCoroutineDispatcher()

    private val internalScope = CoroutineScope(internalDispatcher)

    private val mutex = Mutex()

    private val queue = ConcurrentLinkedQueue<WrappedRunnable>()

    private val isRunning = AtomicBoolean(false)

    private val channel = Channel<Unit>(Channel.UNLIMITED)

    /**
     * Start the dispatcher
     * This function returns immediately
     */
    fun start(fullCheck: Boolean) {
        Timber.d("RimeDispatcher start()")
        internalScope.launch {
            mutex.withLock {
                if (isRunning.compareAndSet(false, true)) {
                    Timber.d("nativeStartup()")
                    looper.nativeStartup(fullCheck)
                    while (isActive && isRunning.get()) {
                        // TODO: because we have nothing to block currently,
                        //  here we use a channel to wait for a signal.
                        runBlocking { channel.receive() }
                        while (true) {
                            val block = queue.poll() ?: break
                            block.run()
                        }
                    }
                    Timber.i("nativeFinalize()")
                    looper.nativeFinalize()
                }
            }
        }
    }

    /**
     * Stop the dispatcher
     * This function blocks until fully stopped
     */
    fun stop(): List<Runnable> {
        Timber.i("RimeDispatcher stop()")
        return if (isRunning.compareAndSet(true, false)) {
            runBlocking {
                channel.trySend(Unit)
                mutex.withLock {
                    val rest = queue.toList()
                    queue.clear()
                    rest
                }
            }
        } else {
            emptyList()
        }
    }

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        if (!isRunning.get()) {
            throw IllegalStateException("Dispatcher is not in running state!")
        }
        queue.offer(WrappedRunnable(block))
        channel.trySend(Unit)
    }
}
