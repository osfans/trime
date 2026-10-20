/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class PipeProcess(
    output: InputStream = ByteArrayInputStream("one\ntwo\n".toByteArray()),
    private val onDestroy: () -> Unit = {},
    private val naturalExit: Boolean = true,
    private val exitCode: Int = 0,
) : Process() {
    val outputClosed = AtomicBoolean()
    val errorClosed = AtomicBoolean()
    val inputClosed = AtomicBoolean()
    val destructions = AtomicInteger()
    val exitObserved = AtomicBoolean()
    val exited = CountDownLatch(if (naturalExit) 0 else 1)
    val stdout = object : FilterInputStream(output) {
        override fun close() {
            outputClosed.set(true)
            super.close()
        }
    }
    val stderr = object : ByteArrayInputStream(ByteArray(0)) {
        override fun close() {
            errorClosed.set(true)
            super.close()
        }
    }
    val stdin = object : ByteArrayOutputStream() {
        override fun close() {
            inputClosed.set(true)
            super.close()
        }
    }

    override fun getInputStream(): InputStream = stdout
    override fun getErrorStream(): InputStream = stderr
    override fun getOutputStream(): OutputStream = stdin
    override fun waitFor(): Int {
        check(exited.await(5, TimeUnit.SECONDS)) { "Test child was not stopped" }
        return exitCode
    }
    override fun exitValue(): Int {
        if (exited.count != 0L) throw IllegalThreadStateException("Child still running")
        exitObserved.set(true)
        return exitCode
    }
    override fun destroy() {
        destructions.incrementAndGet()
        onDestroy()
        exited.countDown()
    }

    fun assertReleased() {
        outputClosed.get() shouldBe true
        errorClosed.get() shouldBe true
        inputClosed.get() shouldBe true
        (destructions.get() > 0) shouldBe true
        exitObserved.get() shouldBe true
    }
}

internal open class BlockingPipe : InputStream() {
    val entered = CountDownLatch(1)
    private val unblocked = CountDownLatch(1)
    override fun read(): Int {
        entered.countDown()
        check(unblocked.await(5, TimeUnit.SECONDS)) { "Test pipe remained blocked" }
        return -1
    }
    fun release() = unblocked.countDown()
}

class ProcessLifecycleTest :
    StringSpec({
        "text and line readers preserve all output and release all child streams" {
            runBlocking {
                val text = PipeProcess()
                text.readText() shouldBe "one\ntwo\n"
                text.assertReleased()
                val lines = PipeProcess()
                lines.readLines() shouldBe listOf("one", "two")
                lines.assertReleased()
            }
        }

        "flow preserves order and closes the child at EOF" {
            runBlocking {
                val process = PipeProcess()
                process.asFlow().toList() shouldBe listOf("one", "two")
                process.assertReleased()
            }
        }

        "stopping a flow before EOF still releases the process" {
            runBlocking {
                val process = PipeProcess()
                process.asFlow().take(1).toList() shouldBe listOf("one")
                process.assertReleased()
            }
        }

        "reader failure retains its original exception and closes the other streams" {
            runBlocking {
                val expected = IOException("Synthetic failed pipe")
                val process = PipeProcess(object : InputStream() {
                    override fun read(): Int = throw expected
                })
                shouldThrow<IOException> { process.readText() } shouldBe expected
                process.assertReleased()
            }
        }

        "reader failure stays primary when both destruction attempts fail" {
            runBlocking {
                val readFailure = IOException("Synthetic read failure")
                val attempts = AtomicInteger()
                val process = PipeProcess(
                    output = object : InputStream() {
                        override fun read(): Int = throw readFailure
                    },
                    onDestroy = { throw IOException("Destroy attempt ${attempts.incrementAndGet()}") },
                )
                val failure = shouldThrow<IOException> { process.readText() }
                failure.message shouldBe readFailure.message
                // Coroutine stack recovery can copy exceptions, retaining originals as causes.
                val suppressed = generateSequence<Throwable>(failure) { it.cause }
                    .flatMap { it.suppressed.asSequence() }
                    .map { it.message }
                    .toSet()
                suppressed shouldBe setOf("Destroy attempt 1", "Destroy attempt 2")
                attempts.get() shouldBe 2
                process.assertReleased()
            }
        }

        "destruction failure at EOF is reported after closing every stream" {
            runBlocking {
                val attempts = AtomicInteger()
                val process = PipeProcess(
                    onDestroy = { throw IOException("Destroy attempt ${attempts.incrementAndGet()}") },
                )
                val failure = shouldThrow<IOException> { process.readText() }
                failure.message shouldBe "Destroy attempt 1"
                generateSequence<Throwable>(failure) { it.cause }
                    .flatMap { it.suppressed.asSequence() }
                    .map { it.message }
                    .toSet() shouldBe setOf("Destroy attempt 2")
                attempts.get() shouldBe 2
                process.assertReleased()
            }
        }

        "cancelling a blocked snapshot stops the child and unblocks its pipe read" {
            runBlocking {
                val pipe = BlockingPipe()
                val process = PipeProcess(pipe, pipe::release, naturalExit = false)
                val reading = async(Dispatchers.Default) { process.readLines() }
                check(pipe.entered.await(5, TimeUnit.SECONDS))
                withTimeout(5_000L) { reading.cancelAndJoin() }
                reading.isCancelled shouldBe true
                process.assertReleased()
            }
        }

        "cancelling a blocked log stream stops the child without another output line" {
            runBlocking {
                val pipe = BlockingPipe()
                val process = PipeProcess(pipe, pipe::release, naturalExit = false)
                val reading = launch(Dispatchers.Default) { process.asFlow().toList() }
                check(pipe.entered.await(5, TimeUnit.SECONDS))
                withTimeout(5_000L) { reading.cancelAndJoin() }
                reading.isCancelled shouldBe true
                process.assertReleased()
            }
        }

        "cancellation closes a blocked pipe even when destroying the child fails" {
            runBlocking {
                val pipe = object : BlockingPipe() {
                    override fun close() = release()
                }
                val destroyFailure = IOException("Synthetic destruction failure")
                val process = PipeProcess(pipe, { throw destroyFailure })
                val reading = async(Dispatchers.Default) { process.readLines() }
                check(pipe.entered.await(5, TimeUnit.SECONDS))
                withTimeout(3_000L) { reading.cancelAndJoin() }
                val failure = shouldThrow<CancellationException> { reading.await() }
                val pending = ArrayDeque<Throwable>()
                val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
                pending.add(failure)
                while (pending.isNotEmpty()) {
                    val next = pending.removeFirst()
                    if (!seen.add(next)) continue
                    next.cause?.let(pending::add)
                    pending.addAll(next.suppressed)
                }
                (destroyFailure in seen) shouldBe true
                process.assertReleased()
            }
        }

        "exit waiting yields to its caller and remains cancellable" {
            runBlocking {
                val process = PipeProcess(naturalExit = false, exitCode = 9)
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { process.awaitExit() }
                waiting.isCompleted shouldBe false
                process.exited.countDown()
                withTimeout(3_000L) { waiting.await() } shouldBe 9

                val running = PipeProcess(naturalExit = false)
                val cancelled = async(start = CoroutineStart.UNDISPATCHED) { running.awaitExit() }
                withTimeout(3_000L) { cancelled.cancelAndJoin() }
                cancelled.isCancelled shouldBe true
            }
        }

        "a child still running after stdout EOF can be cancelled even when destruction fails" {
            runBlocking {
                val atExitWait = CountDownLatch(1)
                val process = PipeProcess(
                    output = ByteArrayInputStream(ByteArray(0)),
                    onDestroy = { throw IOException("Synthetic destruction failure") },
                    naturalExit = false,
                )
                val reading = async(Dispatchers.Default) {
                    process.consumeOutput { reader ->
                        reader.readText() shouldBe ""
                        atExitWait.countDown()
                        process.awaitExit()
                    }
                }
                check(atExitWait.await(5, TimeUnit.SECONDS))
                withTimeout(4_000L) { reading.cancelAndJoin() }
                reading.isCancelled shouldBe true
                process.outputClosed.get() shouldBe true
                process.errorClosed.get() shouldBe true
                process.inputClosed.get() shouldBe true
                // The wait was cancelled, rather than rescued by a successful child exit.
                process.exited.count shouldBe 1L
            }
        }

        "cancellation cleanup runs when the base IO dispatcher has only one worker" {
            val javaName = if (checkNotNull(System.getProperty("os.name")).startsWith("Windows")) "java.exe" else "java"
            val java = File(File(checkNotNull(System.getProperty("java.home")), "bin"), javaName)
            val classpath = listOf(
                ProcessCancellationTestChild::class.java,
                Class.forName("com.osfans.trime.util.ProcessKt"),
                Unit::class.java,
                Dispatchers::class.java,
            ).map {
                val source = checkNotNull(checkNotNull(it.protectionDomain).codeSource)
                File(checkNotNull(source.location).toURI()).path
            }.distinct().joinToString(File.pathSeparator)
            val child = ProcessBuilder(
                java.path,
                "-Dkotlinx.coroutines.io.parallelism=1",
                "-cp",
                classpath,
                ProcessCancellationTestChild::class.java.name,
            ).redirectErrorStream(true).start()
            try {
                child.waitFor(15, TimeUnit.SECONDS) shouldBe true
                val output = child.inputStream.bufferedReader().use { it.readText() }
                check(child.exitValue() == 0) { output }
                output shouldContain "IO_CANCELLATION_COMPLETED_WITHOUT_RESCUE"
            } finally {
                child.destroy()
                child.inputStream.close()
                child.errorStream.close()
                child.outputStream.close()
                if (!child.waitFor(5, TimeUnit.SECONDS)) {
                    child.destroyForcibly()
                    check(child.waitFor(5, TimeUnit.SECONDS)) { "Cancellation test child did not exit" }
                }
            }
        }

        "a failed stdout close cannot prevent closing stderr and stdin" {
            runBlocking {
                val expected = IOException("Synthetic close failure")
                val process = PipeProcess(object : ByteArrayInputStream(ByteArray(0)) {
                    override fun close() = throw expected
                })
                shouldThrow<IOException> { process.readText() } shouldBe expected
                process.assertReleased()
            }
        }

        "a failed stdout getter cannot prevent closing the other descriptors" {
            runBlocking {
                val errorClosed = AtomicBoolean()
                val inputClosed = AtomicBoolean()
                val process = object : Process() {
                    override fun getInputStream(): InputStream = throw IOException("No stdout")
                    override fun getErrorStream(): InputStream = object : ByteArrayInputStream(ByteArray(0)) {
                        override fun close() {
                            errorClosed.set(true)
                        }
                    }
                    override fun getOutputStream(): OutputStream = object : ByteArrayOutputStream() {
                        override fun close() {
                            inputClosed.set(true)
                        }
                    }
                    override fun waitFor(): Int = 0
                    override fun exitValue(): Int = 0
                    override fun destroy() = Unit
                }
                shouldThrow<IOException> { process.readText() }
                errorClosed.get() shouldBe true
                inputClosed.get() shouldBe true
            }
        }

        "real child pipe cancellation terminates and observes the child exit" {
            runBlocking {
                // A real JVM child pipe covers behavior a close-tracking fake cannot model.
                val javaName = if (checkNotNull(System.getProperty("os.name")).startsWith("Windows")) "java.exe" else "java"
                val java = File(File(checkNotNull(System.getProperty("java.home")), "bin"), javaName)
                val classpath = listOf(ProcessTestChild::class.java, Unit::class.java)
                    .map {
                        val source = checkNotNull(checkNotNull(it.protectionDomain).codeSource)
                        File(checkNotNull(source.location).toURI()).path
                    }
                    .distinct()
                    .joinToString(File.pathSeparator)
                val process = ProcessBuilder(java.path, "-cp", classpath, ProcessTestChild::class.java.name).start()
                val ready = CountDownLatch(1)
                val reading = launch(Dispatchers.Default) {
                    process.asFlow().collect { ready.countDown() }
                }
                try {
                    check(ready.await(10, TimeUnit.SECONDS))
                    withTimeout(5_000L) { reading.cancelAndJoin() }
                    process.exitValue()
                } finally {
                    process.destroy()
                }
            }
        }
    })

/** A platform-independent real child; no shell or executable from PATH is required. */
internal object ProcessTestChild {
    @JvmStatic
    fun main(args: Array<String>) {
        check(args.isEmpty())
        System.out.println("ready")
        Thread.sleep(30_000L)
    }
}

/** Isolated so the IO parallelism property is set before Dispatchers initializes. */
internal object ProcessCancellationTestChild {
    @JvmStatic
    fun main(args: Array<String>) {
        check(args.isEmpty())
        check(System.getProperty("kotlinx.coroutines.io.parallelism") == "1")
        val entered = CountDownLatch(1)
        val unblocked = CountDownLatch(1)
        val pipe = object : InputStream() {
            override fun read(): Int {
                entered.countDown()
                check(unblocked.await(12, TimeUnit.SECONDS)) { "Test pipe remained blocked after rescue" }
                return -1
            }
            override fun close() = unblocked.countDown()
        }
        val alive = AtomicBoolean(true)
        val rescued = AtomicBoolean()
        val destructions = AtomicInteger()
        val stdoutClosed = AtomicBoolean()
        val stderrClosed = AtomicBoolean()
        val stdinClosed = AtomicBoolean()
        val process = object : Process() {
            private val stdout = object : FilterInputStream(pipe) {
                override fun close() {
                    stdoutClosed.set(true)
                    super.close()
                }
            }
            private val stderr = object : ByteArrayInputStream(ByteArray(0)) {
                override fun close() {
                    stderrClosed.set(true)
                }
            }
            private val stdin = object : ByteArrayOutputStream() {
                override fun close() {
                    stdinClosed.set(true)
                }
            }
            override fun getInputStream(): InputStream = stdout
            override fun getErrorStream(): InputStream = stderr
            override fun getOutputStream(): OutputStream = stdin
            override fun waitFor(): Int = error("The cancellation helper must not block in waitFor")
            override fun exitValue(): Int {
                if (alive.get()) throw IllegalThreadStateException("Child still running")
                return 0
            }
            override fun destroy() {
                destructions.incrementAndGet()
                alive.set(false)
                unblocked.countDown()
            }
        }
        val scheduler = Executors.newSingleThreadScheduledExecutor {
            Thread(it, "process-cancellation-test-rescue").apply { isDaemon = true }
        }
        val rescue = scheduler.schedule({
            rescued.set(true)
            unblocked.countDown()
        }, 8, TimeUnit.SECONDS)
        try {
            runBlocking {
                val reading = async(Dispatchers.Default) { process.readLines() }
                check(entered.await(5, TimeUnit.SECONDS)) { "Reader did not enter the blocked IO pipe" }
                withTimeout(5_000L) { reading.cancelAndJoin() }
                check(reading.isCancelled)
            }
            check(!rescued.get()) { "Cancellation required manual pipe rescue" }
            check(destructions.get() > 0)
            check(stdoutClosed.get() && stderrClosed.get() && stdinClosed.get())
            println("IO_CANCELLATION_COMPLETED_WITHOUT_RESCUE")
        } finally {
            rescue.cancel(false)
            scheduler.shutdownNow()
            unblocked.countDown()
            process.destroy()
        }
    }
}
