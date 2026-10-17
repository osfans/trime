// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.daemon.RimeDaemon
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

/** Exercises automatic recovery when app-scoped storage becomes writable after startup. */
internal object LateStorageProbe {
    private const val BLOCKED_OBSERVATION_MS = 1_600L
    private const val MINIMUM_MANUAL_WAITER_MS = 1_500L
    private const val MINIMUM_RETRY_EXHAUSTION_MS = 28_000L
    private const val RETRY_EXHAUSTION_TIMEOUT_MS = 45_000L
    private const val RECOVERY_TIMEOUT_MS = 60_000L
    private const val SHUTDOWN_TIMEOUT_MS = 30_000L
    private const val PROBE_TIMEOUT_MS = 240_000L

    private class StorageBlock(
        root: File,
        private val suffix: String,
    ) {
        private data class Entry(
            val live: File,
            val backup: File,
            var moved: Boolean = false,
            var blockerCreated: Boolean = false,
        )

        private val entries = listOf("shared", "rime").map { name ->
            Entry(
                live = File(root, name),
                backup = File(root, "$name-late-storage-$suffix"),
            )
        }

        fun block() {
            check(entries.none { it.moved }) { "Storage block is already active" }
            try {
                entries.forEach { entry ->
                    check(entry.live.isDirectory) { "Missing runtime directory: ${entry.live}" }
                    check(!entry.backup.exists()) { "Backup path already exists: ${entry.backup}" }
                    check(entry.live.renameTo(entry.backup)) {
                        "Cannot move ${entry.live} to ${entry.backup}"
                    }
                    entry.moved = true
                    check(entry.live.createNewFile()) { "Cannot create blocker file: ${entry.live}" }
                    entry.blockerCreated = true
                    entry.live.writeText("late-storage-probe:$suffix:${entry.live.name}\n")
                }
            } catch (failure: Throwable) {
                runCatching { restore() }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        }

        fun restore() {
            var firstFailure: Throwable? = null
            entries.asReversed().forEach { entry ->
                if (!entry.moved) return@forEach
                try {
                    if (entry.blockerCreated) {
                        check(entry.live.isFile) { "Blocker path changed unexpectedly: ${entry.live}" }
                        check(entry.live.delete()) { "Cannot remove blocker file: ${entry.live}" }
                        entry.blockerCreated = false
                    } else {
                        check(!entry.live.exists()) { "Live path was recreated unexpectedly: ${entry.live}" }
                    }
                    check(entry.backup.isDirectory) { "Backup directory is missing: ${entry.backup}" }
                    check(entry.backup.renameTo(entry.live)) {
                        "Cannot restore ${entry.backup} to ${entry.live}"
                    }
                    entry.moved = false
                } catch (failure: Throwable) {
                    if (firstFailure == null) {
                        firstFailure = failure
                    } else {
                        firstFailure.addSuppressed(failure)
                    }
                }
            }
            firstFailure?.let { throw it }
        }
    }

    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        var success = false
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) {
                "This probe only runs on an emulator"
            }
            val context = instrumentation.targetContext
            val root = checkNotNull(context.getExternalFilesDir(null)) {
                "App-scoped external files dir is unavailable before the probe"
            }.canonicalFile
            check(File(root, "runtime-audit-dedicated").isFile) {
                "Requires an explicitly marked disposable audit installation"
            }

            fun phase(message: String) {
                instrumentation.sendStatus(
                    0,
                    Bundle().apply { putString("stream", "$message\n") },
                )
            }

            runBlocking {
                withTimeout(PROBE_TIMEOUT_MS) {
                    check(RimeDaemon.getFirstSessionOrNull() == null) {
                        "Late-storage probe requires no other Rime session"
                    }
                    check(RimeDaemon.engineState.value == RimeLifecycle.State.STOPPED) {
                        "Late-storage probe requires a stopped engine"
                    }

                    val phaseOneRecoveryMs = runAutomaticRecoveryPhase(root, ::phase)
                    result.putLong("late_storage_automatic_recovery_ms", phaseOneRecoveryMs)
                    awaitStopped()

                    val phaseTwoWaitMs = runManualWaiterPhase(root, ::phase)
                    result.putLong("late_storage_manual_waiter_held_ms", phaseTwoWaitMs)
                    awaitStopped()

                    val phaseThreeExhaustionMs = runRetryExhaustionPhase(root, ::phase)
                    result.putLong("late_storage_retry_exhaustion_ms", phaseThreeExhaustionMs)
                    awaitStopped()
                }
            }
            success = true
            result.putString(
                "stream",
                "PASS: late storage stayed stopped while blocked, recovered automatically, " +
                    "manual retry waited for READY, and bounded polling failed then recovered\n",
            )
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            result.putBoolean("passed", success)
            instrumentation.finish(
                if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                result,
            )
        }
    }

    private suspend fun runAutomaticRecoveryPhase(
        root: File,
        phase: (String) -> Unit,
    ): Long {
        val sessionName = "late-storage-automatic"
        val storage = StorageBlock(root, UUID.randomUUID().toString())
        var sessionCreated = false
        try {
            storage.block()
            val session = RimeDaemon.createSession(sessionName)
            sessionCreated = true
            val unexpectedState = withTimeoutOrNull(BLOCKED_OBSERVATION_MS) {
                RimeDaemon.engineState.first { it != RimeLifecycle.State.STOPPED }
            }
            check(unexpectedState == null) {
                "Engine left STOPPED while storage was blocked: $unexpectedState"
            }
            phase("Late-storage phase 1 remained STOPPED for $BLOCKED_OBSERVATION_MS ms")

            val recoveryStarted = SystemClock.elapsedRealtime()
            storage.restore()
            withTimeout(RECOVERY_TIMEOUT_MS) {
                RimeDaemon.engineState.first { it == RimeLifecycle.State.READY }
                session.runOnReady {
                    check(isReady)
                    check(selectedSchemaId().isNotBlank())
                }
            }
            return (SystemClock.elapsedRealtime() - recoveryStarted).also {
                phase("Late-storage phase 1 recovered automatically in $it ms")
            }
        } finally {
            try {
                if (sessionCreated) RimeDaemon.destroySession(sessionName)
            } finally {
                storage.restore()
            }
        }
    }

    private suspend fun runManualWaiterPhase(
        root: File,
        phase: (String) -> Unit,
    ): Long = coroutineScope {
        val sessionName = "late-storage-manual-waiter"
        val storage = StorageBlock(root, UUID.randomUUID().toString())
        var sessionCreated = false
        var retry: Deferred<Unit>? = null
        try {
            storage.block()
            val session = RimeDaemon.createSession(sessionName)
            sessionCreated = true
            val waitStarted = SystemClock.elapsedRealtime()
            val retryJob = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                RimeDaemon.retryFailedStartup()
            }
            retry = retryJob
            val unexpectedState = withTimeoutOrNull(BLOCKED_OBSERVATION_MS) {
                RimeDaemon.engineState.first { it != RimeLifecycle.State.STOPPED }
            }
            val heldMs = SystemClock.elapsedRealtime() - waitStarted
            check(unexpectedState == null) {
                "Engine left STOPPED while storage was blocked: $unexpectedState"
            }
            check(heldMs >= MINIMUM_MANUAL_WAITER_MS) {
                "Manual waiter observation was too short: $heldMs ms"
            }
            check(!retryJob.isCompleted) {
                "retryFailedStartup completed before storage became available"
            }
            phase("Late-storage phase 2 manual retry remained pending for $heldMs ms")

            storage.restore()
            withTimeout(RECOVERY_TIMEOUT_MS) {
                retryJob.await()
                check(RimeDaemon.engineState.value == RimeLifecycle.State.READY)
                session.runOnReady {
                    check(isReady)
                    check(selectedSchemaId().isNotBlank())
                }
            }
            phase("Late-storage phase 2 retry completed only after READY")
            return@coroutineScope heldMs
        } finally {
            withContext(NonCancellable) {
                retry?.cancelAndJoin()
                try {
                    if (sessionCreated) RimeDaemon.destroySession(sessionName)
                } finally {
                    storage.restore()
                }
            }
        }
    }

    private suspend fun runRetryExhaustionPhase(
        root: File,
        phase: (String) -> Unit,
    ): Long {
        val sessionName = "late-storage-retry-exhaustion"
        val storage = StorageBlock(root, UUID.randomUUID().toString())
        var sessionCreated = false
        try {
            storage.block()
            val session = RimeDaemon.createSession(sessionName)
            sessionCreated = true
            val exhaustionStarted = SystemClock.elapsedRealtime()
            withTimeout(RETRY_EXHAUSTION_TIMEOUT_MS) {
                RimeDaemon.engineState.first { it == RimeLifecycle.State.FAILED }
            }
            val exhaustionMs = SystemClock.elapsedRealtime() - exhaustionStarted
            check(exhaustionMs >= MINIMUM_RETRY_EXHAUSTION_MS) {
                "Startup retry exhausted too early: $exhaustionMs ms"
            }
            phase("Late-storage phase 3 reached FAILED after $exhaustionMs ms")

            storage.restore()
            withTimeout(RECOVERY_TIMEOUT_MS) {
                RimeDaemon.retryFailedStartup()
                check(RimeDaemon.engineState.value == RimeLifecycle.State.READY)
                session.runOnReady {
                    check(isReady)
                    check(selectedSchemaId().isNotBlank())
                }
            }
            phase("Late-storage phase 3 recovered from exhausted polling after explicit retry")
            return exhaustionMs
        } finally {
            try {
                if (sessionCreated) RimeDaemon.destroySession(sessionName)
            } finally {
                storage.restore()
            }
        }
    }

    private suspend fun awaitStopped() {
        withTimeout(SHUTDOWN_TIMEOUT_MS) {
            RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
        }
    }
}
