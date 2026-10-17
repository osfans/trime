// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

class RimeDispatcherTest :
    StringSpec({
        "startup failure cancels queued callers and permits a clean retry" {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val attempts = AtomicInteger()
            val reported = AtomicReference<Throwable>()
            val cause = IOException("injected asset copy failure")
            val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                override fun nativeStartup() {
                    if (attempts.incrementAndGet() == 1) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        throw cause
                    }
                }
                override fun nativeFinalize() = Unit
                override fun nativeFailure(error: Throwable) {
                    reported.set(error)
                }
            })
            try {
                coroutineScope {
                    dispatcher.start()
                    entered.await(5, TimeUnit.SECONDS) shouldBe true
                    val ran = AtomicInteger()
                    val pending = async(dispatcher) { ran.incrementAndGet() }
                    release.countDown()
                    withTimeout(5000) { pending.join() }
                    pending.isCancelled shouldBe true
                    dispatcher.stop()
                    reported.get() shouldBe cause
                    ran.get() shouldBe 0
                    dispatcher.start()
                    dispatcher.runConfined { 9 } shouldBe 9
                    dispatcher.stop()
                    attempts.get() shouldBe 2
                }
            } finally {
                release.countDown()
                dispatcher.close()
            }
        }

        "finalization failure publishes the cause before the stop barrier and can restart" {
            val finalized = AtomicInteger()
            val reported = AtomicReference<Throwable>()
            val cause = IOException("injected finalization failure")
            val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                override fun nativeStartup() = Unit
                override fun nativeFinalize() {
                    if (finalized.incrementAndGet() == 1) throw cause
                }
                override fun nativeFailure(error: Throwable) {
                    reported.set(error)
                }
            })
            try {
                dispatcher.start()
                dispatcher.stop()
                reported.get() shouldBe cause
                dispatcher.start()
                dispatcher.runConfined { 9 } shouldBe 9
                dispatcher.stop()
                finalized.get() shouldBe 2
            } finally {
                dispatcher.close()
            }
        }

        "a failed in-session redeploy finalizes and does not strand queued calls" {
            val reported = AtomicReference<Throwable>()
            val cause = IOException("injected redeploy failure")
            val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                override fun nativeStartup() = Unit
                override fun nativeFinalize() = Unit
                override fun nativeFailure(error: Throwable) {
                    reported.set(error)
                }
            })
            try {
                dispatcher.start()
                dispatcher.runConfined { dispatcher.fail(cause) }
                dispatcher.stop()
                reported.get() shouldBe cause
                dispatcher.start()
                dispatcher.runConfined { 9 } shouldBe 9
            } finally {
                dispatcher.close()
            }
        }

        "stop waits for a startup that has not acquired the worker mutex yet" {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finalized = AtomicInteger()
            val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                override fun nativeStartup() {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                override fun nativeFinalize() {
                    finalized.incrementAndGet()
                }
            })
            try {
                coroutineScope {
                    dispatcher.start()
                    val stop = async(Dispatchers.IO) { dispatcher.stop() }
                    started.await(5, TimeUnit.SECONDS) shouldBe true
                    finalized.get() shouldBe 0
                    release.countDown()
                    withTimeout(5000) { stop.await() }.isEmpty() shouldBe true
                    finalized.get() shouldBe 1
                }
            } finally {
                release.countDown()
                dispatcher.close()
            }
        }

        "stop drains accepted work before finalization and restart has no old work" {
            val release = CountDownLatch(1)
            val ran = AtomicInteger()
            val finalized = AtomicInteger()
            val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                override fun nativeStartup() {
                    check(release.await(5, TimeUnit.SECONDS))
                }
                override fun nativeFinalize() {
                    ran.get() shouldBe 8
                    finalized.incrementAndGet()
                }
            })
            try {
                dispatcher.start()
                repeat(8) { dispatcher.dispatch(EmptyCoroutineContext, Runnable { ran.incrementAndGet() }) }
                release.countDown()
                dispatcher.stop().isEmpty() shouldBe true
                dispatcher.start()
                dispatcher.stop().isEmpty() shouldBe true
                finalized.get() shouldBe 2
            } finally {
                release.countDown()
                dispatcher.close()
            }
        }

        "a rejected coroutine completes as cancelled instead of waiting forever" {
            val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                override fun nativeStartup() = Unit
                override fun nativeFinalize() = Unit
            })
            try {
                coroutineScope {
                    val ran = AtomicInteger()
                    val task = async(dispatcher) { ran.incrementAndGet() }
                    withTimeout(5000) { task.join() }
                    task.isCancelled shouldBe true
                    ran.get() shouldBe 0
                }
            } finally {
                dispatcher.close()
            }
        }

        "shutdown signals have independent enqueue times and execution state" {
            val first = RimeDispatcher.WrappedRunnable.wakeup()
            first.run()
            val second = RimeDispatcher.WrappedRunnable.wakeup()
            first.started shouldBe true
            second.started shouldBe false
            (first === second) shouldBe false
        }

        "noncancellable rejected calls cannot reach finalized native state on an IO worker" {
            val nativeThread = AtomicReference<Thread>()
            val caller = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "rejection-probe").apply { isDaemon = true }
            }
            val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                override fun nativeStartup() {
                    nativeThread.set(Thread.currentThread())
                }
                override fun nativeFinalize() = Unit
            })
            try {
                dispatcher.start()
                dispatcher.runConfined { Thread.currentThread() } shouldBe nativeThread.get()
                dispatcher.stop()
                val ran = AtomicInteger()
                val rejected = caller.submit<Int> {
                    runBlocking {
                        withContext(NonCancellable) {
                            dispatcher.runConfined { ran.incrementAndGet() }
                        }
                    }
                }
                val failure = shouldThrow<ExecutionException> { rejected.get(5, TimeUnit.SECONDS) }
                (failure.cause is IllegalStateException) shouldBe true
                ran.get() shouldBe 0
            } finally {
                caller.shutdownNow()
                dispatcher.close()
            }
        }

        "backpressured failure publication releases the submission lock but retains the stop barrier" {
            coroutineScope {
                val bus = RimeMessageBus(1)
                val consumerEntered = CompletableDeferred<Unit>()
                val releaseConsumer = CompletableDeferred<Unit>()
                val failureEntered = CountDownLatch(1)
                val attempts = AtomicInteger()
                val ran = AtomicInteger()
                val cause = IOException("injected startup failure with a stalled message consumer")
                val first = RimeMessage.CommitTextMessage(CommitProto("first"))
                val second = RimeMessage.CommitTextMessage(CommitProto("second"))
                val failed = RimeMessage.DeployMessage(RimeMessage.DeployMessage.State.Failure)
                val received = mutableListOf<RimeMessage<*>>()
                val consumer = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    bus.flow.take(3).collect {
                        received += it
                        consumerEntered.complete(Unit)
                        releaseConsumer.await()
                    }
                }
                val callers = Executors.newFixedThreadPool(2) { runnable ->
                    Thread(runnable, "failure-lock-probe").apply { isDaemon = true }
                }
                val dispatcher = RimeDispatcher(object : RimeDispatcher.RimeController {
                    override fun nativeStartup() {
                        if (attempts.incrementAndGet() == 1) throw cause
                    }
                    override fun nativeFinalize() = Unit
                    override fun nativeFailure(error: Throwable) {
                        error shouldBe cause
                        failureEntered.countDown()
                        bus.publish(failed)
                    }
                })
                try {
                    withContext(Dispatchers.IO) { bus.publish(first) }
                    withTimeout(5000) { consumerEntered.await() }
                    withContext(Dispatchers.IO) { bus.publish(second) }
                    dispatcher.start()
                    failureEntered.await(5, TimeUnit.SECONDS) shouldBe true

                    // This represents a UI dispatch while its message collector is delayed.
                    // If the worker holds submissionLock while publishing, neither can proceed.
                    val rejected = callers.submit<Boolean> {
                        runBlocking {
                            val task = async(dispatcher) { ran.incrementAndGet() }
                            task.join()
                            dispatcher.start() // Must not restart before failure publication ends.
                            task.isCancelled
                        }
                    }
                    rejected.get(2, TimeUnit.SECONDS) shouldBe true
                    ran.get() shouldBe 0
                    attempts.get() shouldBe 1
                    val stop = callers.submit<List<Runnable>> { dispatcher.stop() }
                    shouldThrow<TimeoutException> { stop.get(100, TimeUnit.MILLISECONDS) }

                    releaseConsumer.complete(Unit)
                    stop.get(5, TimeUnit.SECONDS).isEmpty() shouldBe true
                    withTimeout(5000) { consumer.join() }
                    received shouldBe listOf(first, second, failed)
                    dispatcher.start()
                    dispatcher.runConfined { 9 } shouldBe 9
                    attempts.get() shouldBe 2
                } finally {
                    releaseConsumer.complete(Unit)
                    consumer.cancelAndJoin()
                    callers.shutdownNow()
                    dispatcher.close()
                }
            }
        }
    })
