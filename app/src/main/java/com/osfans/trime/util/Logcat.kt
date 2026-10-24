// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.os.Build
import android.os.Process
import com.osfans.trime.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader

/**
 * Adapted from [fcitx5-android/Logcat.kt](https://github.com/fcitx5-android/fcitx5-android/blob/e44c1c7/app/src/main/java/org/fcitx/fcitx5/android/utils/Logcat.kt)
 */
class Logcat internal constructor(
    val pid: Int?,
    private val sdkInt: Int,
    private val startProcess: (List<String>) -> java.lang.Process,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    @JvmOverloads
    constructor(pid: Int? = Process.myPid()) : this(pid, Build.VERSION.SDK_INT, { subprocess(*it.toTypedArray()) })

    private val streamLock = Any()
    private var emittingJob: Job? = null

    private val flow: MutableSharedFlow<String> = MutableSharedFlow()

    // Older logcat has no --pid. Threadtime places the PID before the arbitrary tag/message,
    // so filtering cannot confuse a payload or a tag containing parentheses with a PID.
    private val filterInApp = pid != null && sdkInt < Build.VERSION_CODES.N
    private val logFormat = if (filterInApp) "threadtime" else "time"

    private fun command(vararg options: String): List<String> = buildList {
        add("logcat")
        if (sdkInt >= Build.VERSION_CODES.N) pid?.let { add("--pid=$it") }
        add("-v")
        add(logFormat)
        addAll(options)
    }

    private fun includesLine(line: String): Boolean = !filterInApp || THREAD_TIME_PREFIX.find(line)?.groupValues?.get(1)?.toIntOrNull() == pid

    /**
     * Subscribe to this flow to receive log in app
     * Nothing would be emitted until [initLogFlow] was called
     */
    val logFlow: SharedFlow<String> by lazy { flow.asSharedFlow() }

    /** Get a snapshot of logcat, closing the completed child and its streams. */
    fun getLogAsync(): Deferred<Result<List<String>>> = async {
        try {
            val process = startProcess(command("-d"))
            Result.success(
                process.consumeOutput { reader ->
                    val lines = buildList {
                        while (true) add(reader.readLogLine() ?: break)
                    }
                    check(process.awaitExit() == 0) { "Unable to read logcat snapshot" }
                    lines.filter(::includesLine)
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** Clear logcat and wait for the owned command to finish. */
    fun clearLog(): Job = launch {
        try {
            clearLogAndWait()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Timber.e(error, "Unable to clear logcat")
        }
    }

    /** The deployment path also owns and waits for its clear command. */
    internal suspend fun clearLogAndWait(): Unit = withContext(Dispatchers.IO) {
        val process = startProcess(listOf("logcat", "-c"))
        process.consumeOutput { reader ->
            reader.readText()
            check(process.awaitExit() == 0) { "Unable to clear logcat" }
        }
    }

    internal suspend fun readDeployLog(): String = withContext(Dispatchers.IO) {
        val process = startProcess(listOf("logcat", "-v", "brief", "-s", "rime.trime:W", "-d"))
        process.consumeOutput { reader ->
            val text = reader.readText()
            check(process.awaitExit() == 0) { "Unable to read deployment logcat" }
            text
        }
    }

    /** Observe until the caller stops, subscribing before the child can emit its first line. */
    internal suspend fun collectLines(onLine: (String) -> Unit): Unit = coroutineScope {
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
            logFlow.collect { onLine(it) }
        }
        val streaming = try {
            initLogFlow()
        } catch (error: Throwable) {
            collecting.cancel()
            throw error
        }
        try {
            // Keep the subscriber alive after EOF so the last rendezvous emission can finish
            // delivery. The view's lifecycle, rather than the reader, owns this subscription.
            collecting.join()
        } finally {
            // Cancel only this invocation's stream, so late completion cannot stop a restart.
            streaming.cancel()
            collecting.cancel()
            withContext(NonCancellable) { streaming.cancelAndJoin() }
        }
    }

    /** Create one owned process reading logcat and sending lines to [logFlow]. */
    fun initLogFlow(): Job = synchronized(streamLock) {
        if (emittingJob != null) errorState(R.string.exception_logcat_created)
        launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext().job
            try {
                currentCoroutineContext().ensureActive()
                val process = startProcess(command())
                // Ownership begins immediately after creation, including cancellation before
                // the IO reader starts. One cleanup preserves the original read failure.
                process.consumeOutput { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLogLine() ?: break
                        if (includesLine(line)) flow.emit(line)
                    }
                    check(process.awaitExit() == 0) { "Unable to read logcat stream" }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                Timber.e(error, "Unable to read logcat")
            } finally {
                synchronized(streamLock) {
                    if (emittingJob === ownJob) emittingJob = null
                }
            }
        }.also {
            emittingJob = it
            it.start()
        }
    }

    /** Cancel this stream; a later initialization starts a fresh child. */
    fun shutdownLogFlow() {
        val previous = synchronized(streamLock) {
            emittingJob.also { emittingJob = null }
        }
        previous?.cancel()
    }

    companion object {
        // Logcat frames records on LF. BufferedReader.readLine also splits a payload's bare CR,
        // which could make that payload look like a separate record from another PID.
        private fun BufferedReader.readLogLine(): String? {
            val line = StringBuilder()
            while (true) {
                val next = read()
                if (next == -1) return if (line.isEmpty()) null else line.toString()
                if (next == '\n'.code) return line.toString()
                line.append(next.toChar())
            }
        }

        private val THREAD_TIME_PREFIX = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+\s+(\d+)\s+\d+\s+([VDIWEFAS])(?:\s|$)""")
        private val TIME_PREFIX = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+\s+([VDIWEFAS])/""")

        internal fun priorityOf(line: String): Char? = THREAD_TIME_PREFIX.find(line)?.groupValues?.get(2)?.singleOrNull()
            ?: TIME_PREFIX.find(line)?.groupValues?.get(1)?.singleOrNull()

        val default by lazy { Logcat() }
    }
}
