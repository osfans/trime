/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

fun subprocess(vararg commands: String): Process = Runtime.getRuntime().exec(commands)

// This elastic view can stop a pipe reader even when the base IO view is saturated.
private val processCleanupDispatcher = Dispatchers.IO.limitedParallelism(1)

/** Consumes this process's output and releases its process and all three streams. */
internal suspend fun <T> Process.consumeOutput(block: suspend (BufferedReader) -> T): T {
    var failure: Throwable? = null
    val stopFailure = AtomicReference<Exception?>()
    try {
        return withContext(Dispatchers.IO) {
            // Interrupting a coroutine does not reliably interrupt a pipe read on Android.
            // Closing its streams must still be attempted when termination fails.
            val stopOnCancellation = launch(processCleanupDispatcher, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    try {
                        releaseProcess()
                    } catch (error: Exception) {
                        stopFailure.set(error)
                    }
                }
            }
            try {
                inputStream.bufferedReader().use { block(it) }
            } finally {
                stopOnCancellation.cancel()
            }
        }
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        // Also covers cancellation before the IO block starts and failed reader construction.
        // withContext waits for the cancellation watcher before reaching this cleanup.
        var cleanupFailure: Exception? = null
        fun recordCleanup(error: Exception) {
            val primary = failure ?: cleanupFailure
            if (primary == null) {
                cleanupFailure = error
            } else {
                // Stack recovery can copy a cancellation exception across withContext. Jobs
                // retain its original cause, so attach cleanup errors to that stable node.
                var target = primary
                val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
                while (target is CancellationException && seen.add(target)) {
                    val cause = target.cause
                    if (cause !is CancellationException || cause in seen) break
                    target = cause
                }
                if (target !== error) target.addSuppressed(error)
            }
        }
        stopFailure.get()?.let(::recordCleanup)
        try {
            releaseProcess()
        } catch (cleanup: Exception) {
            recordCleanup(cleanup)
        }
        cleanupFailure?.let { throw it }
    }
}

internal suspend fun Process.releaseProcess() {
    withContext(NonCancellable + processCleanupDispatcher) {
        var failure: Exception? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: Exception) {
                if (failure == null) {
                    failure = error
                } else if (failure !== error) {
                    failure.addSuppressed(error)
                }
            }
        }
        attempt { destroy() }
        // One failed getter/close must not leave the other two descriptors open.
        attempt { inputStream.close() }
        attempt { errorStream.close() }
        attempt { outputStream.close() }
        val exited = withTimeoutOrNull(1_000L) {
            awaitExit()
            true
        } == true
        if (!exited) attempt { throw IOException("Child process did not exit after destruction") }
        failure?.let { throw it }
    }
}

/** Waits without blocking a worker; callers can cancel while the child is running. */
internal suspend fun Process.awaitExit(): Int {
    while (true) {
        currentCoroutineContext().ensureActive()
        try {
            return exitValue()
        } catch (_: IllegalThreadStateException) {
            delay(10L)
        }
    }
}

/** A single consumer owns the process once collection begins. */
fun Process.asFlow(): Flow<String> = channelFlow {
    consumeOutput { reader ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = reader.readLine() ?: break
            send(line)
        }
    }
}.flowOn(Dispatchers.IO)

suspend fun Process.readText(): String = consumeOutput { it.readText() }

suspend fun Process.readLines(): List<String> = consumeOutput { it.readLines() }
