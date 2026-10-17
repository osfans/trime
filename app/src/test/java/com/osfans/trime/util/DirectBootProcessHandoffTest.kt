/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class DirectBootProcessHandoffTest :
    StringSpec({
        "Android 21 never queries unlock state or accesses replacement storage" {
            var finished = 0
            val handoff = DirectBootProcessHandoff { error("Privacy boundary must not run") }

            handoff.request(
                apiLevel = 21,
                directBootMode = true,
                isUserUnlocked = { error("API 24 method must not be queried") },
                finishBroadcast = { finished++ },
                replaceProcess = { error("CE restore must not run") },
            ) shouldBe false

            handoff.pending shouldBe false
            finished shouldBe 1
        }

        "normal processes never query unlock state or request replacement" {
            val handoff = DirectBootProcessHandoff { error("Normal process must remain unchanged") }

            handoff.request(
                apiLevel = 35,
                directBootMode = false,
                isUserUnlocked = { error("Normal process must not query unlock state") },
                replaceProcess = { error("Normal process must not restart") },
            ) shouldBe false

            handoff.pending shouldBe false
        }

        "a locked DE lifecycle cannot restore CE and a later unlocked lifecycle starts once" {
            var unlocked = false
            var protections = 0
            var replacements = 0
            val handoff = DirectBootProcessHandoff { protections++ }
            val request = {
                handoff.request(24, true, { unlocked }) {
                    protections shouldBe 1
                    handoff.pending shouldBe true
                    replacements++
                }
            }

            request() shouldBe false
            replacements shouldBe 0
            protections shouldBe 0
            handoff.pending shouldBe false

            unlocked = true
            request() shouldBe true
            request() shouldBe true
            replacements shouldBe 1
            protections shouldBe 1
        }

        "a lifecycle request makes a late broadcast finish once without another replacement" {
            var replacements = 0
            var finished = 0
            val handoff = DirectBootProcessHandoff {}
            handoff.request(35, true, { true }) { replacements++ } shouldBe true

            handoff.request(
                apiLevel = 35,
                directBootMode = true,
                isUserUnlocked = { error("Pending replacement must not query again") },
                finishBroadcast = { finished++ },
                replaceProcess = { replacements++ },
            ) shouldBe true

            replacements shouldBe 1
            finished shouldBe 1
        }

        "timeout and normal completion share one broadcast finish" {
            val handoff = DirectBootProcessHandoff {}
            val completion = AtomicReference<(() -> Unit)?>()
            val finished = AtomicInteger()
            handoff.request(35, true, { true }, { finished.incrementAndGet() }) {
                completion.set(it)
            } shouldBe true
            val complete = requireNotNull(completion.get())
            val start = CountDownLatch(1)
            val workers = List(2) { index ->
                thread(name = "handoff-complete-$index") {
                    check(start.await(5, TimeUnit.SECONDS))
                    complete()
                }
            }
            start.countDown()
            workers.forEach { it.join(5_000) }

            workers.any { it.isAlive } shouldBe false
            complete()
            finished.get() shouldBe 1
        }

        "concurrent lifecycle and broadcast requests protect and replace exactly once" {
            val protections = AtomicInteger()
            val replacements = AtomicInteger()
            val finishes = List(12) { AtomicInteger() }
            val failure = AtomicReference<Throwable?>()
            val handoff = DirectBootProcessHandoff { protections.incrementAndGet() }
            val ready = CountDownLatch(finishes.size)
            val start = CountDownLatch(1)
            val workers = finishes.mapIndexed { index, finished ->
                thread(name = "handoff-request-$index") {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    runCatching {
                        handoff.request(35, true, { true }, { finished.incrementAndGet() }) {
                            protections.get() shouldBe 1
                            handoff.pending shouldBe true
                            replacements.incrementAndGet()
                            it()
                            it()
                        } shouldBe true
                    }.onFailure { failure.compareAndSet(null, it) }
                }
            }
            check(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            workers.forEach { it.join(5_000) }

            workers.any { it.isAlive } shouldBe false
            failure.get() shouldBe null
            protections.get() shouldBe 1
            replacements.get() shouldBe 1
            finishes.map { it.get() } shouldBe List(finishes.size) { 1 }
        }

        "pending is published only after privacy protection completes" {
            val protectionEntered = CountDownLatch(1)
            val allowProtection = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            var protected = false
            val handoff = DirectBootProcessHandoff {
                protectionEntered.countDown()
                check(allowProtection.await(5, TimeUnit.SECONDS))
                protected = true
            }
            val requesting = thread(name = "handoff-protection") {
                runCatching {
                    handoff.request(35, true, { true }) {
                        protected shouldBe true
                        handoff.pending shouldBe true
                    }
                }.onFailure { failure.set(it) }
                finished.countDown()
            }
            check(protectionEntered.await(5, TimeUnit.SECONDS))
            try {
                handoff.pending shouldBe false
                finished.count shouldBe 1L
            } finally {
                allowProtection.countDown()
                requesting.join(5_000)
            }

            requesting.isAlive shouldBe false
            failure.get() shouldBe null
            handoff.pending shouldBe true
        }

        "replacement setup failure finishes the broadcast and keeps the old process pending" {
            var protections = 0
            var finishes = 0
            val handoff = DirectBootProcessHandoff { protections++ }
            shouldThrow<IllegalStateException> {
                handoff.request(35, true, { true }, { finishes++ }) {
                    it()
                    error("Failed scheduling replacement")
                }
            }

            protections shouldBe 1
            finishes shouldBe 1
            handoff.pending shouldBe true
            handoff.request(35, true, { error("Already pending") }) {
                error("Must not restart replacement")
            } shouldBe true
        }

        "a failed unlock query never accesses CE or leaves replacement pending" {
            var finishes = 0
            val handoff = DirectBootProcessHandoff { error("No confirmed unlock") }
            shouldThrow<IllegalStateException> {
                handoff.request(35, true, { error("Unlock query unavailable") }, { finishes++ }) {
                    error("Must not access CE")
                }
            }

            handoff.pending shouldBe false
            finishes shouldBe 1
        }
    })
