/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LogcatLifecycleTest :
    StringSpec({
        "snapshot retains its command arguments and releases its child" {
            runBlocking {
                val process = PipeProcess()
                var command = emptyList<String>()
                val logcat = Logcat(null, 35) {
                    command = it
                    process
                }
                logcat.getLogAsync().await().getOrThrow() shouldBe listOf("one", "two")
                command shouldBe listOf("logcat", "-v", "time", "-d")
                process.assertReleased()
            }
        }

        "a failed snapshot command returns failure instead of empty success" {
            runBlocking {
                val process = PipeProcess(java.io.ByteArrayInputStream(ByteArray(0)), exitCode = 1)
                val logcat = Logcat(null, 35) { process }
                val result = logcat.getLogAsync().await()
                result.isFailure shouldBe true
                result.exceptionOrNull()?.message shouldBe "Unable to read logcat snapshot"
                process.assertReleased()
            }
        }

        "clear command waits for completion and closes all streams" {
            runBlocking {
                val process = PipeProcess()
                var command = emptyList<String>()
                val logcat = Logcat(null, 21) {
                    command = it
                    process
                }
                withTimeout(5_000L) { logcat.clearLog().join() }
                command shouldBe listOf("logcat", "-c")
                process.assertReleased()
            }
        }

        "legacy snapshots filter the PID column without accepting tag or payload lookalikes" {
            runBlocking {
                val wanted = "10-21 01:02:03.004   123   456 W Tag(999): message (123): kept"
                val other = "10-21 01:02:03.004  1234   456 E Tag(123): message (123): rejected"
                val payload = "10-21 01:02:03.004   999   456 I Tag: 123 456 W forged (123): rejected"
                val lines = listOf("--------- beginning of main", wanted, other, payload, "", "(123): no header")
                for (sdk in listOf(21, 23)) {
                    val process = PipeProcess(java.io.ByteArrayInputStream(lines.joinToString("\n").toByteArray()))
                    var command = emptyList<String>()
                    val logcat = Logcat(123, sdk) {
                        command = it
                        process
                    }
                    logcat.getLogAsync().await().getOrThrow() shouldBe listOf(wanted)
                    command shouldBe listOf("logcat", "-v", "threadtime", "-d")
                    process.assertReleased()
                }
            }
        }

        "modern snapshots use native PID filtering from API24 onwards" {
            runBlocking {
                for (sdk in listOf(24, 35)) {
                    val process = PipeProcess()
                    var command = emptyList<String>()
                    val logcat = Logcat(123, sdk) {
                        command = it
                        process
                    }
                    logcat.getLogAsync().await().getOrThrow() shouldBe listOf("one", "two")
                    command shouldBe listOf("logcat", "--pid=123", "-v", "time", "-d")
                    process.assertReleased()
                }
            }
        }

        "unfiltered snapshots omit a PID argument on old and new Android" {
            runBlocking {
                for (sdk in listOf(21, 35)) {
                    val process = PipeProcess()
                    var command = emptyList<String>()
                    val logcat = Logcat(null, sdk) {
                        command = it
                        process
                    }
                    logcat.getLogAsync().await().getOrThrow() shouldBe listOf("one", "two")
                    command shouldBe listOf("logcat", "-v", "time", "-d")
                    process.assertReleased()
                }
            }
        }

        "legacy live collection applies the same PID filter as snapshots" {
            runBlocking {
                val first = "10-21 01:02:03.004   123   456 I Tag: first"
                val second = "10-21 01:02:03.005   123   456 W Tag: second"
                val wrong = "10-21 01:02:03.004  1234   456 E Tag(123): rejected"
                val process = PipeProcess(java.io.ByteArrayInputStream("$wrong\n$first\n$second\n".toByteArray()))
                var command = emptyList<String>()
                val logcat = Logcat(123, 21) {
                    command = it
                    process
                }
                val collected = async(start = CoroutineStart.UNDISPATCHED) {
                    logcat.logFlow.take(2).toList()
                }
                val streaming = logcat.initLogFlow()
                try {
                    withTimeout(5_000L) { collected.await() } shouldBe listOf(first, second)
                    withTimeout(5_000L) { streaming.join() }
                    command shouldBe listOf("logcat", "-v", "threadtime")
                    process.assertReleased()
                } finally {
                    logcat.shutdownLogFlow()
                    collected.cancelAndJoin()
                    streaming.cancelAndJoin()
                }
            }
        }

        "owned clear reports nonzero exit after releasing its child" {
            runBlocking {
                val process = PipeProcess(exitCode = 1)
                val logcat = Logcat(null, 21) { process }
                shouldThrow<IllegalStateException> { logcat.clearLogAndWait() }
                    .message shouldBe "Unable to clear logcat"
                process.assertReleased()
            }
        }

        "priority parsing accepts time and threadtime but safely rejects short lines" {
            Logcat.priorityOf("10-21 01:02:03.004 W/Tag(  123): time") shouldBe 'W'
            Logcat.priorityOf("10-21 01:02:03.004   123   456 E Tag: threadtime") shouldBe 'E'
            Logcat.priorityOf("10-21 01:02:03.004 1234567 7654321 I Tag: wide IDs") shouldBe 'I'
            Logcat.priorityOf("") shouldBe null
            Logcat.priorityOf("short") shouldBe null
            Logcat.priorityOf("--------- beginning of main") shouldBe null
            Logcat.priorityOf("payload W/Tag(123): no timestamp") shouldBe null
        }

        "bare CR in another process payload cannot forge a legacy PID header" {
            runBlocking {
                val forged = "10-21 01:02:03.004   999   456 I Tag: payload\r10-21 01:02:03.004   123   456 I Tag: forged"
                val wanted = "10-21 01:02:03.005   123   456 W Tag: kept\rpayload"
                val output = "$forged\n$wanted"
                val snapshot = PipeProcess(java.io.ByteArrayInputStream(output.toByteArray()))
                Logcat(123, 21) { snapshot }.getLogAsync().await().getOrThrow() shouldBe listOf(wanted)
                snapshot.assertReleased()
                val live = PipeProcess(java.io.ByteArrayInputStream(output.toByteArray()))
                val collected = mutableListOf<String>()
                val received = CompletableDeferred<Unit>()
                val observing = launch(start = CoroutineStart.UNDISPATCHED) {
                    Logcat(123, 21) { live }.collectLines {
                        collected.add(it)
                        received.complete(Unit)
                    }
                }
                try {
                    withTimeout(5_000L) { received.await() }
                } finally {
                    observing.cancelAndJoin()
                }
                collected shouldBe listOf(wanted)
                live.assertReleased()
            }
        }

        "bound collection subscribes before a child emits its first lines" {
            runBlocking {
                val process = PipeProcess()
                val collected = mutableListOf<String>()
                val received = CompletableDeferred<Unit>()
                val observing = launch(start = CoroutineStart.UNDISPATCHED) {
                    Logcat(null, 35) { process }.collectLines {
                        collected.add(it)
                        if (collected.size == 2) received.complete(Unit)
                    }
                }
                try {
                    withTimeout(5_000L) { received.await() }
                } finally {
                    observing.cancelAndJoin()
                }
                collected shouldBe listOf("one", "two")
                process.assertReleased()
            }
        }

        "cancelling bound collection also finishes its blocked child cleanup" {
            runBlocking {
                val pipe = BlockingPipe()
                val process = PipeProcess(pipe, pipe::release, naturalExit = false)
                val collecting = launch(kotlinx.coroutines.Dispatchers.Default) {
                    Logcat(null, 35) { process }.collectLines {}
                }
                check(pipe.entered.await(5, TimeUnit.SECONDS))
                withTimeout(5_000L) { collecting.cancelAndJoin() }
                process.assertReleased()
            }
        }

        "deployment snapshots retain the tag filter and release their child" {
            runBlocking {
                val process = PipeProcess()
                var command = emptyList<String>()
                val logcat = Logcat(null, 21) {
                    command = it
                    process
                }
                logcat.readDeployLog() shouldBe "one\ntwo\n"
                command shouldBe listOf("logcat", "-v", "brief", "-s", "rime.trime:W", "-d")
                process.assertReleased()
            }
        }

        "deployment snapshot command failure is not returned as empty successful text" {
            runBlocking {
                val process = PipeProcess(java.io.ByteArrayInputStream(ByteArray(0)), exitCode = 1)
                shouldThrow<IllegalStateException> { Logcat(null, 21) { process }.readDeployLog() }
                    .message shouldBe "Unable to read deployment logcat"
                process.assertReleased()
            }
        }

        "a live read failure stays primary when closing stdout also fails" {
            runBlocking {
                val readFailure = java.io.IOException("Synthetic live read failure")
                val closeFailure = java.io.IOException("Synthetic stdout close failure")
                val process = PipeProcess(object : java.io.InputStream() {
                    override fun read(): Int = throw readFailure
                    override fun close() = throw closeFailure
                })
                val logged = AtomicReference<Throwable?>()
                val tree = object : Timber.Tree() {
                    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                        if (message.startsWith("Unable to read logcat")) logged.set(t)
                    }
                }
                val logcat = Logcat(null, 35) { process }
                Timber.plant(tree)
                try {
                    val stream = logcat.initLogFlow()
                    try {
                        withTimeout(5_000L) { stream.join() }
                        // Coroutine stack recovery can copy an exception across this boundary.
                        // Its original cause must still be the read failure, never cleanup.
                        generateSequence(checkNotNull(logged.get())) { it.cause }
                            .firstOrNull { it === readFailure || it === closeFailure } shouldBe readFailure
                        readFailure.suppressed.any { it === closeFailure } shouldBe true
                        process.assertReleased()
                    } finally {
                        withContext(NonCancellable) {
                            logcat.shutdownLogFlow()
                            stream.cancelAndJoin()
                        }
                    }
                } finally {
                    Timber.uproot(tree)
                }
            }
        }

        "stop during process creation releases that late child and allows another stream" {
            runBlocking {
                val entered = CountDownLatch(1)
                val finishCreation = CountDownLatch(1)
                val first = PipeProcess()
                val secondPipe = BlockingPipe()
                val second = PipeProcess(secondPipe, secondPipe::release, naturalExit = false)
                val starts = AtomicInteger()
                val logcat = Logcat(null, 35) {
                    if (starts.incrementAndGet() == 1) {
                        entered.countDown()
                        check(finishCreation.await(5, TimeUnit.SECONDS))
                        first
                    } else {
                        second
                    }
                }
                val oldJob = logcat.initLogFlow()
                check(entered.await(5, TimeUnit.SECONDS))
                logcat.shutdownLogFlow()
                val newJob = logcat.initLogFlow()
                check(secondPipe.entered.await(5, TimeUnit.SECONDS))
                finishCreation.countDown()
                withTimeout(5_000L) { oldJob.join() }
                first.assertReleased()
                // The old stream's completion must not erase the newly running stream's slot.
                newJob.isActive shouldBe true
                logcat.shutdownLogFlow()
                withTimeout(5_000L) { newJob.join() }
                second.assertReleased()
                starts.get() shouldBe 2
            }
        }

        "a completed stream can start again without keeping a stale process" {
            runBlocking {
                val children = mutableListOf<PipeProcess>()
                val logcat = Logcat(null, 35) { PipeProcess().also { children.add(it) } }
                withTimeout(5_000L) { logcat.initLogFlow().join() }
                withTimeout(5_000L) { logcat.initLogFlow().join() }
                children.size shouldBe 2
                children.forEach { it.assertReleased() }
            }
        }
    })
