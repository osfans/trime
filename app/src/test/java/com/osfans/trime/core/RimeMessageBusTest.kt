/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class RimeMessageBusTest :
    StringSpec({
        "paused consumers receive every burst commit key and frame in order with editor ownership" {
            coroutineScope {
                val capacity = 15
                val bus = RimeMessageBus(capacity)
                val release = CompletableDeferred<Unit>()
                val received = List(4) { mutableListOf<RimeMessage<*>>() }
                val entered = List(4) { CompletableDeferred<Unit>() }
                val expected = (0 until 100).flatMap { index ->
                    listOf(
                        RimeMessage.CommitTextMessage(CommitProto("commit-$index")),
                        RimeMessage.InlinePreeditMessage(InlinePreeditProto("preedit-$index")),
                        RimeMessage.CompositionMessage(CompositionProto("composition-$index")),
                        RimeMessage.PagedCandidatesMessage(Candidates.Paged()),
                        RimeMessage.StatusMessage(StatusProto()),
                        RimeMessage.T9Message(T9StateProto()),
                        RimeMessage.KeyMessage(RimeMessage.KeyMessage.Data(KeyValue(index + 32), KeyModifiers.of(0), true)),
                        RimeMessage.OptionMessage(RimeMessage.OptionMessage.Data("ascii_mode", index % 2 == 0)),
                    ).onEach { it.editorToken = index / 10L + 1 }
                }
                val consumers = received.mapIndexed { index, output ->
                    launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        bus.flow.take(expected.size).collect {
                            output += it
                            if (output.size == 1) {
                                entered[index].complete(Unit)
                                release.await()
                            }
                        }
                    }
                }
                val produced = AtomicInteger()
                val producer = async(Dispatchers.IO) {
                    expected.forEach {
                        bus.publish(it)
                        produced.incrementAndGet()
                    }
                }
                try {
                    withTimeout(5000) { entered.forEach { it.await() } }
                    withTimeoutOrNull(100) { producer.join() } shouldBe null
                    // All consumers hold the first frame. The producer cannot retain the
                    // rest of the burst in an unbounded queue or silently drop overflow.
                    (produced.get() in 1..capacity + 1) shouldBe true
                    release.complete(Unit)
                    withTimeout(5000) {
                        producer.await()
                        consumers.forEach { it.join() }
                    }
                    received.forEach { output ->
                        output.size shouldBe expected.size
                        output.zip(expected).forEach { (actual, original) ->
                            (actual === original) shouldBe true
                            actual.editorToken shouldBe original.editorToken
                        }
                    }
                } finally {
                    release.complete(Unit)
                    consumers.forEach { it.cancelAndJoin() }
                }
            }
        }

        "startup without subscribers never blocks or replays old editor output" {
            val bus = RimeMessageBus(1)
            withTimeout(5000) {
                withContext(Dispatchers.IO) {
                    repeat(4096) { bus.publish(RimeMessage.CommitTextMessage(CommitProto("old-$it"))) }
                }
                coroutineScope {
                    val next = async(start = CoroutineStart.UNDISPATCHED) { bus.flow.first() }
                    val current = RimeMessage.CommitTextMessage(CommitProto("current"))
                    withContext(Dispatchers.IO) { bus.publish(current) }
                    (next.await() === current) shouldBe true
                }
            }
        }

        "canceling the last slow subscriber releases the blocked native producer" {
            coroutineScope {
                val bus = RimeMessageBus(1)
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val consumer = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    bus.flow.collect {
                        entered.complete(Unit)
                        release.await()
                    }
                }
                val producer = async(Dispatchers.IO) {
                    repeat(100) { bus.publish(RimeMessage.CommitTextMessage(CommitProto("$it"))) }
                }
                try {
                    withTimeout(5000) { entered.await() }
                    withTimeoutOrNull(100) { producer.join() } shouldBe null
                    consumer.cancelAndJoin()
                    withTimeout(5000) { producer.await() }
                } finally {
                    consumer.cancelAndJoin()
                    release.complete(Unit)
                }
            }
        }

        "reentrant cache handlers publish nested schema before outer status under backpressure" {
            coroutineScope {
                val bus = RimeMessageBus(1)
                val release = CompletableDeferred<Unit>()
                val entered = CompletableDeferred<Unit>()
                val first = RimeMessage.CommitTextMessage(CommitProto("first"))
                val schema = RimeMessage.SchemaMessage(SchemaItem("schema", "Schema"))
                val status = RimeMessage.StatusMessage(StatusProto())
                val output = mutableListOf<RimeMessage<*>>()
                val consumer = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    bus.flow.take(3).collect {
                        output += it
                        if (it === first) {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                }
                val producer = async(Dispatchers.IO) {
                    bus.publish(first)
                    bus.publish(status) { bus.publish(schema) }
                }
                try {
                    withTimeout(5000) { entered.await() }
                    withTimeoutOrNull(100) { producer.join() } shouldBe null
                    release.complete(Unit)
                    withTimeout(5000) {
                        producer.await()
                        consumer.join()
                    }
                    output shouldBe listOf(first, schema, status)
                } finally {
                    release.complete(Unit)
                    consumer.cancelAndJoin()
                }
            }
        }
    })
