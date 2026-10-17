// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Proxy

private class ControlledSession(
    private val scopeFailure: Throwable? = null,
    private val readyFailure: Throwable? = null,
) : RimeSession {
    val owner = SupervisorJob()
    val entered = CompletableDeferred<Job>()
    val release = CompletableDeferred<Unit>()
    val completion = CompletableDeferred<Throwable?>()
    val uncaught = CompletableDeferred<Throwable>()
    private val scope = CoroutineScope(
        owner + Dispatchers.Default + CoroutineExceptionHandler { _, failure -> uncaught.complete(failure) },
    )
    private val unusedApi by lazy {
        Proxy.newProxyInstance(RimeApi::class.java.classLoader, arrayOf(RimeApi::class.java)) { _, method, _ ->
            error("Session scheduling tests must not call RimeApi.${method.name}")
        } as RimeApi
    }

    override val lifecycleScope: CoroutineScope
        get() {
            scopeFailure?.let { throw it }
            return scope
        }

    override fun <T> run(block: suspend RimeApi.() -> T): T = error("launchOnReady must not use synchronous run")

    override fun runIfReady(block: suspend RimeApi.() -> Unit): Unit = error("launchOnReady must not use runIfReady")

    override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T {
        val job = checkNotNull(currentCoroutineContext()[Job])
        job.invokeOnCompletion { completion.complete(it) }
        entered.complete(job)
        release.await()
        readyFailure?.let { throw it }
        return block(unusedApi)
    }

    suspend fun releaseAndJoin(): Job = withTimeout(5_000) {
        val job = entered.await()
        release.complete(Unit)
        job.join()
        job
    }

    fun close() {
        owner.cancel()
    }
}

class SessionLaunchTest :
    StringSpec({
        "a closed scope getter does not launch or execute the operation" {
            val closed = RimeSessionClosedException("ended-owner")
            closed.message shouldBe "Session ended-owner is not established"
            val session = ControlledSession(scopeFailure = closed)
            try {
                var operations = 0
                val result: Unit = session.launchOnReady { operations += 1 }
                result shouldBe Unit
                operations shouldBe 0
                session.entered.isCompleted shouldBe false
                session.owner.children.none() shouldBe true
            } finally {
                session.close()
            }
        }

        "a session closed after scheduling cancels the actual child with its original cause" {
            val closed = RimeSessionClosedException("queued-owner")
            val session = ControlledSession(readyFailure = closed)
            try {
                var operations = 0
                session.launchOnReady { operations += 1 }
                val job = session.releaseAndJoin()
                job.isCancelled shouldBe true
                val failure = session.completion.await()
                (failure is CancellationException) shouldBe true
                failure?.cause shouldBe closed
                operations shouldBe 0
                session.uncaught.isCompleted shouldBe false
            } finally {
                session.close()
            }
        }

        "an unrelated scope getter failure still reaches the caller" {
            val failure = IllegalStateException("invalid scope state")
            val session = ControlledSession(scopeFailure = failure)
            try {
                shouldThrow<IllegalStateException> {
                    session.launchOnReady { error("Operation must not run") }
                } shouldBe failure
                session.entered.isCompleted shouldBe false
                session.owner.children.none() shouldBe true
            } finally {
                session.close()
            }
        }

        "unrelated ready and operation failures remain uncaught instead of becoming closed-owner cancellation" {
            for (failBeforeBlock in listOf(true, false)) {
                val failure = IllegalStateException("invalid maintenance state")
                val session = ControlledSession(readyFailure = failure.takeIf { failBeforeBlock })
                try {
                    var operations = 0
                    session.launchOnReady {
                        operations += 1
                        throw failure
                    }
                    session.releaseAndJoin().isCancelled shouldBe true
                    withTimeout(5_000) { session.uncaught.await() } shouldBe failure
                    session.completion.await() shouldBe failure
                    operations shouldBe if (failBeforeBlock) 0 else 1
                } finally {
                    session.close()
                }
            }
        }

        "an existing cancellation keeps its cause and does not execute the operation" {
            val cancellation = CancellationException("original owner cancellation")
            val session = ControlledSession(readyFailure = cancellation)
            try {
                var operations = 0
                session.launchOnReady { operations += 1 }
                session.releaseAndJoin().isCancelled shouldBe true
                session.completion.await() shouldBe cancellation
                session.uncaught.isCompleted shouldBe false
                operations shouldBe 0
            } finally {
                session.close()
            }
        }

        "closing the original owner cancels a ready waiter without executing its operation" {
            val session = ControlledSession()
            try {
                var operations = 0
                session.launchOnReady { operations += 1 }
                val job = withTimeout(5_000) { session.entered.await() }
                session.close()
                withTimeout(5_000) { job.join() }
                job.isCancelled shouldBe true
                (session.completion.await() is CancellationException) shouldBe true
                operations shouldBe 0
                session.uncaught.isCompleted shouldBe false
            } finally {
                session.close()
            }
        }

        "a ready live session executes exactly once and completes its actual child normally" {
            val session = ControlledSession()
            try {
                var operations = 0
                session.launchOnReady { operations += 1 }
                val job = session.releaseAndJoin()
                job.isCompleted shouldBe true
                job.isCancelled shouldBe false
                session.completion.await() shouldBe null
                session.uncaught.isCompleted shouldBe false
                operations shouldBe 1
            } finally {
                session.close()
            }
        }
    })
