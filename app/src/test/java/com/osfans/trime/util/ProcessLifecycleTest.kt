/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
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

internal class BlockingPipe : InputStream() {
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
                val process = ProcessBuilder("sh", "-c", "printf 'ready\\n'; exec sleep 30").start()
                val ready = CountDownLatch(1)
                val reading = launch(Dispatchers.Default) {
                    process.asFlow().collect { ready.countDown() }
                }
                try {
                    check(ready.await(5, TimeUnit.SECONDS))
                    withTimeout(5_000L) { reading.cancelAndJoin() }
                    process.exitValue()
                } finally {
                    process.destroy()
                }
            }
        }
    })
