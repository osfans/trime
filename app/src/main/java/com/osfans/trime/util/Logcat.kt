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
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Adapted from [fcitx5-android/Logcat.kt](https://github.com/fcitx5-android/fcitx5-android/blob/e44c1c7/app/src/main/java/org/fcitx/fcitx5/android/utils/Logcat.kt)
 */
class Logcat internal constructor(
    val pid: Int?,
    private val sdkInt: Int,
    private val startProcess: (List<String>) -> java.lang.Process,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    constructor(pid: Int? = Process.myPid()) : this(pid, Build.VERSION.SDK_INT, { subprocess(*it.toTypedArray()) })

    private val streamLock = Any()
    private var emittingJob: Job? = null

    private val flow: MutableSharedFlow<String> = MutableSharedFlow()

    private fun command(vararg options: String): List<String> = buildList {
        add("logcat")
        if (sdkInt >= Build.VERSION_CODES.N) pid?.let { add("--pid=$it") }
        addAll(options)
    }

    private fun includesLine(line: String): Boolean {
        if (pid == null || sdkInt >= Build.VERSION_CODES.N) return true
        return TIME_LOG_LINE.find(line)?.groupValues?.get(1)?.toIntOrNull() == pid
    }

    /**
     * Subscribe to this flow to receive log in app
     * Nothing would be emitted until [initLogFlow] was called
     */
    val logFlow: SharedFlow<String> by lazy { flow.asSharedFlow() }

    /** Get a snapshot of logcat, closing the completed child and its streams. */
    fun getLogAsync(): Deferred<Result<List<String>>> = async {
        try {
            val process = startProcess(command("-v", "time", "-d"))
            Result.success(
                process.consumeOutput { reader ->
                    val lines = reader.readLines()
                    check(process.waitFor() == 0) { "Unable to read logcat snapshot" }
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
            val process = startProcess(listOf("logcat", "-c"))
            process.consumeOutput { reader ->
                reader.readText()
                check(process.waitFor() == 0) { "Unable to clear logcat" }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Timber.e(error, "Unable to clear logcat")
        }
    }

    /** Create one owned process reading logcat and sending lines to [logFlow]. */
    fun initLogFlow(): Job = synchronized(streamLock) {
        if (emittingJob != null) errorState(R.string.exception_logcat_created)
        launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext().job
            try {
                currentCoroutineContext().ensureActive()
                val process = startProcess(command("-v", "time"))
                // Ownership begins immediately after creation, including cancellation before
                // the IO reader starts. One cleanup preserves the original read failure.
                process.consumeOutput { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        if (includesLine(line)) flow.emit(line)
                    }
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
        // Older logcat versions have no --pid or long --clear option. Match only complete
        // time-format records so similarly prefixed PIDs or a payload cannot match.
        private val TIME_LOG_LINE = Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+\s+[VDIWEFAS]/.*?\(\s*(\d+)\):""")

        val default by lazy { Logcat() }
    }
}
