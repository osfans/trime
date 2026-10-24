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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.IOException

fun subprocess(vararg commands: String): Process = Runtime.getRuntime().exec(commands)

// This elastic view can stop a pipe reader even when the base IO view is saturated.
private val processCleanupDispatcher = Dispatchers.IO.limitedParallelism(1)

/** Consumes this process's output and releases its process and all three streams. */
internal suspend fun <T> Process.consumeOutput(block: suspend (BufferedReader) -> T): T {
    var failure: Throwable? = null
    var released = false
    var cleanupFailure: Exception? = null

    // Both cancellation and the outer finally can reach cleanup. Mark ownership
    // before suspending; withContext waits for its watcher child before finally.
    suspend fun releaseOnce() = withContext(NonCancellable) {
        // Returning from the dispatcher switch must also happen while active.
        withContext(processCleanupDispatcher) {
            if (!released) {
                released = true
                try {
                    releaseProcess()
                } catch (error: Exception) {
                    cleanupFailure = error
                }
            }
        }
    }

    try {
        return withContext(Dispatchers.IO) {
            // A blocked pipe read cannot check coroutine cancellation itself.
            val stopOnCancellation = launch(processCleanupDispatcher, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    releaseOnce()
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
        // Also owns a process created just before its caller was cancelled,
        // when withContext never entered the IO reader or installed the watcher.
        releaseOnce()
        cleanupFailure?.let { cleanup ->
            var primary = failure
            // Stack recovery copies cancellation exceptions. Attach diagnostics
            // to the original cancellation retained by the caller's Job.
            while (primary is CancellationException && primary.cause is CancellationException) {
                primary = primary.cause
            }
            if (primary == null) throw cleanup
            if (primary !== cleanup) primary.addSuppressed(cleanup)
        }
    }
}

private suspend fun Process.releaseProcess() {
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

fun Process.asFlow(): Flow<String> = inputStream
    .bufferedReader()
    .lineSequence()
    .asFlow()
    .flowOn(Dispatchers.IO)
    .cancellable()

suspend fun Process.readText() = withContext(Dispatchers.IO) {
    inputStream.bufferedReader().readText()
}

suspend fun Process.readLines() = withContext(Dispatchers.IO) {
    inputStream.bufferedReader().readLines()
}
