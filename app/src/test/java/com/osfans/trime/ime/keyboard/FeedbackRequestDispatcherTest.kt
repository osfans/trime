/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private fun runFeedbackWorker(block: () -> Unit) {
    val failure = AtomicReference<Throwable?>()
    val worker = Thread {
        try {
            block()
        } catch (error: Throwable) {
            failure.set(error)
        }
    }.apply { isDaemon = true }
    worker.start()
    worker.join(5_000)
    check(!worker.isAlive) { "Feedback worker did not finish" }
    failure.get()?.let { throw it }
}

private class FeedbackOwnerQueue {
    val owner: Thread = Thread.currentThread()
    private val requests = ConcurrentLinkedQueue<() -> Unit>()
    val dispatcher = FeedbackRequestDispatcher(
        isOwnerThread = { Thread.currentThread() === owner },
        postToOwner = ::post,
    )

    fun post(block: () -> Unit) {
        requests.add(block)
    }

    fun drain() {
        check(Thread.currentThread() === owner)
        while (true) (requests.poll() ?: return).invoke()
    }
}

class FeedbackRequestDispatcherTest :
    StringSpec({
        "a request already on the owner runs immediately without posting" {
            val owner = Thread.currentThread()
            val calls = mutableListOf<Thread>()
            val dispatcher = FeedbackRequestDispatcher(
                isOwnerThread = { Thread.currentThread() === owner },
                postToOwner = { error("An owner request must not be posted") },
            )
            dispatcher.dispatch { calls.add(Thread.currentThread()) }
            calls shouldBe listOf(owner)
        }

        "a real worker queues feedback without touching its owner state" {
            val queue = FeedbackOwnerQueue()
            val calls = mutableListOf<Thread>()
            runFeedbackWorker { queue.dispatcher.dispatch { calls.add(Thread.currentThread()) } }
            calls shouldBe emptyList()
            queue.drain()
            calls shouldBe listOf(queue.owner)
        }

        "a lifecycle change drops old queued work while accepting a new request" {
            val queue = FeedbackOwnerQueue()
            val spoken = mutableListOf<String>()
            runFeedbackWorker { queue.dispatcher.dispatch { spoken.add("old input") } }
            queue.dispatcher.invalidate()
            runFeedbackWorker { queue.dispatcher.dispatch { spoken.add("new input") } }
            queue.drain()
            spoken shouldBe listOf("new input")
        }

        "invalidation also rejects a worker delayed before its post reaches the queue" {
            val owner = Thread.currentThread()
            val requests = ConcurrentLinkedQueue<() -> Unit>()
            val enteringPost = CountDownLatch(1)
            val allowPost = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            var executions = 0
            val dispatcher = FeedbackRequestDispatcher(
                isOwnerThread = { Thread.currentThread() === owner },
                postToOwner = { request ->
                    enteringPost.countDown()
                    check(allowPost.await(5, TimeUnit.SECONDS))
                    requests.add(request)
                },
            )
            val worker = Thread {
                try {
                    dispatcher.dispatch { executions++ }
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }.apply { isDaemon = true }
            worker.start()
            try {
                check(enteringPost.await(5, TimeUnit.SECONDS))
                dispatcher.invalidate()
            } finally {
                allowPost.countDown()
                worker.join(5_000)
            }
            worker.isAlive shouldBe false
            failure.get()?.let { throw it }
            requests.size shouldBe 1
            checkNotNull(requests.poll()).invoke()
            executions shouldBe 0
        }

        "worker speech requests retain the latest text until their resource becomes ready" {
            val queue = FeedbackOwnerQueue()
            val backendThreads = mutableListOf<Thread>()
            val spoken = mutableListOf<String>()
            var pending: String? = null
            var completion: ((Boolean) -> Unit)? = null
            val resource = FeedbackResource<Any> { }
            resource.init()
            fun speakPending() {
                pending?.let(spoken::add)
                pending = null
            }
            fun request(text: String) {
                queue.dispatcher.dispatch {
                    pending = text
                    val value = resource.acquire(
                        enabled = true,
                        create = { complete ->
                            backendThreads.add(Thread.currentThread())
                            completion = complete
                            Any()
                        },
                        onInitialized = { _, ready ->
                            backendThreads.add(Thread.currentThread())
                            if (ready) speakPending() else pending = null
                        },
                    )
                    if (value == null) {
                        pending = null
                    } else if (resource.ready === value) {
                        speakPending()
                    }
                }
            }
            runFeedbackWorker { request("committed text") }
            runFeedbackWorker { request("latest key") }
            backendThreads shouldBe emptyList()
            queue.drain()
            backendThreads shouldBe listOf(queue.owner)
            spoken shouldBe emptyList()
            runFeedbackWorker { queue.post { checkNotNull(completion)(true) } }
            queue.drain()
            backendThreads shouldBe listOf(queue.owner, queue.owner)
            spoken shouldBe listOf("latest key")
            resource.destroy()
        }

        "a queued old request cannot allocate a resource after destruction and reinitialization" {
            val queue = FeedbackOwnerQueue()
            val created = mutableListOf<String>()
            val released = mutableListOf<String>()
            val resource = FeedbackResource<String> { released.add(it) }
            resource.init()
            fun request(text: String) {
                queue.dispatcher.dispatch {
                    resource.acquire(
                        enabled = true,
                        create = { complete ->
                            created.add(text)
                            complete(true)
                            text
                        },
                    )
                }
            }
            runFeedbackWorker { request("old input") }
            queue.dispatcher.invalidate()
            resource.destroy()
            queue.dispatcher.invalidate()
            resource.init()
            runFeedbackWorker { request("new input") }
            queue.drain()
            created shouldBe listOf("new input")
            resource.ready shouldBe "new input"
            resource.destroy()
            released shouldBe listOf("new input")
        }
    })
