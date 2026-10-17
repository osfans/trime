/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

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

fun subprocess(vararg commands: String): Process = Runtime.getRuntime().exec(commands)

/** Consumes this process's output and releases its process and all three streams. */
internal suspend fun <T> Process.consumeOutput(block: suspend (BufferedReader) -> T): T {
    var failure: Throwable? = null
    try {
        return withContext(Dispatchers.IO) {
            // Interrupting a coroutine does not reliably interrupt a pipe read on Android.
            // Terminate the owned child when cancellation starts so the read can finish.
            val stopOnCancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    destroy()
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
        try {
            releaseProcess()
        } catch (cleanup: Exception) {
            if (failure == null) throw cleanup
            if (failure !== cleanup) failure.addSuppressed(cleanup)
        }
    }
}

internal suspend fun Process.releaseProcess() {
    withContext(NonCancellable + Dispatchers.IO) {
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
            while (true) {
                try {
                    exitValue()
                    return@withTimeoutOrNull true
                } catch (_: IllegalThreadStateException) {
                    delay(10L)
                }
            }
        } == true
        if (!exited) attempt { throw IOException("Child process did not exit after destruction") }
        failure?.let { throw it }
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
