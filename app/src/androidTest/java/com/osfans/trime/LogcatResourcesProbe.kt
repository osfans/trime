/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.osfans.trime.util.Logcat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Uses the actual device logcat CLI without clearing the audit log. */
internal object LogcatResourcesProbe {
    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        var passed = false
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            val token = "TRIME_LOG_RESOURCES_${UUID.randomUUID()}"
            val logcat = Logcat(Process.myPid())
            runBlocking {
                suspend fun snapshot(reader: Logcat): List<String> {
                    val pending = reader.getLogAsync()
                    return try {
                        pending.await().getOrThrow()
                    } finally {
                        withContext(NonCancellable) { pending.cancelAndJoin() }
                    }
                }
                withTimeout(60_000) {
                    repeat(3) { cycle ->
                        val marker = "${token}_$cycle"
                        val observed = async(start = CoroutineStart.UNDISPATCHED) {
                            withTimeout(10_000) { logcat.logFlow.first { it.contains(marker) } }
                        }
                        val stream = logcat.initLogFlow()
                        try {
                            withTimeout(10_000) {
                                while (!observed.isCompleted) {
                                    Log.i("TrimeLogProbe", marker)
                                    delay(100)
                                }
                            }
                            check(observed.await().contains(marker))
                        } finally {
                            withContext(NonCancellable) {
                                try {
                                    observed.cancelAndJoin()
                                } finally {
                                    logcat.shutdownLogFlow()
                                    withTimeout(5_000) { stream.join() }
                                }
                            }
                        }
                        check(stream.isCancelled) { "Live logcat stream was not cancelled" }
                    }
                    check(snapshot(logcat).any { it.contains(token) })
                    check(snapshot(Logcat(null)).any { it.contains(token) })
                    check(snapshot(Logcat(Int.MAX_VALUE)).isEmpty())
                }
            }
            result.putInt("stream_restart_cycles", 3)
            result.putInt("snapshot_checks", 3)
            result.putString("marker", token)
            result.putInt("api", Build.VERSION.SDK_INT)
            result.putInt("pid", Process.myPid())
            passed = true
        } catch (error: Throwable) {
            result.putString("error", error.stackTraceToString())
        }
        result.putBoolean("log_resources_passed", passed)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
