/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private class TestFeedbackResource(val id: Int)

class FeedbackResourceTest :
    StringSpec({
        "disabled feedback and initialization alone allocate no resources" {
            val released = mutableListOf<TestFeedbackResource>()
            val owner = FeedbackResource<TestFeedbackResource> { released.add(it) }
            var creations = 0
            val create: ((Boolean) -> Unit) -> TestFeedbackResource = {
                TestFeedbackResource(++creations)
            }
            owner.canAcquire shouldBe false
            owner.acquire(enabled = true, create = create) shouldBe null
            owner.init()
            owner.canAcquire shouldBe true
            owner.acquire(enabled = false, create = create) shouldBe null
            owner.canAcquire shouldBe true
            owner.current shouldBe null
            owner.ready shouldBe null
            owner.destroy()
            owner.canAcquire shouldBe false
            creations shouldBe 0
            released shouldBe emptyList()
        }

        "the first enabled request creates once and waits for readiness" {
            val owner = FeedbackResource<TestFeedbackResource> { }
            val notifications = mutableListOf<Int>()
            var creations = 0
            var complete: ((Boolean) -> Unit)? = null
            val create: ((Boolean) -> Unit) -> TestFeedbackResource = {
                complete = it
                TestFeedbackResource(++creations)
            }
            owner.init()
            val first = owner.acquire(true, create) { resource, _ -> notifications.add(resource.id) }
            owner.ready shouldBe null
            repeat(5) { owner.acquire(true, create) shouldBe first }
            creations shouldBe 1
            notifications shouldBe emptyList()
            checkNotNull(complete)(true)
            owner.ready shouldBe first
            notifications shouldBe listOf(1)
            // Some backends can notify repeatedly; delivery still belongs to one initialization.
            checkNotNull(complete)(true)
            checkNotNull(complete)(false)
            owner.ready shouldBe first
            notifications shouldBe listOf(1)
            owner.acquire(false, create) shouldBe null
            creations shouldBe 1
            owner.destroy()
        }

        "an immediate callback is delivered only after the factory returns its resource" {
            val events = mutableListOf<String>()
            val owner = FeedbackResource<TestFeedbackResource> { }
            owner.init()
            val resource = owner.acquire(
                enabled = true,
                create = { complete ->
                    events.add("create")
                    complete(true)
                    events.add("return")
                    TestFeedbackResource(7)
                },
                onInitialized = { value, ready ->
                    events.add("ready ${value.id} $ready")
                    owner.ready shouldBe value
                },
            )
            events shouldBe listOf("create", "return", "ready 7 true")
            owner.ready shouldBe resource
            owner.destroy()
        }

        "callbacks from a destroyed pending resource cannot ready its replacement" {
            val released = mutableListOf<Int>()
            val notifications = mutableListOf<Int>()
            val completions = mutableListOf<(Boolean) -> Unit>()
            val owner = FeedbackResource<TestFeedbackResource> { released.add(it.id) }
            var creations = 0
            val create: ((Boolean) -> Unit) -> TestFeedbackResource = {
                completions.add(it)
                TestFeedbackResource(++creations)
            }
            val initialized: (TestFeedbackResource, Boolean) -> Unit = { value, _ -> notifications.add(value.id) }
            owner.init()
            owner.acquire(true, create, initialized)
            owner.destroy()
            owner.destroy()
            owner.init()
            val replacement = owner.acquire(true, create, initialized)
            completions[0](true)
            completions[0](false)
            owner.current shouldBe replacement
            owner.ready shouldBe null
            notifications shouldBe emptyList()
            released shouldBe listOf(1)
            completions[1](true)
            owner.ready shouldBe replacement
            notifications shouldBe listOf(2)
            owner.destroy()
            completions[1](true)
            released shouldBe listOf(1, 2)
            notifications shouldBe listOf(2)
            owner.current shouldBe null
        }

        "a failed initialization releases once and does not retry on every key" {
            val released = mutableListOf<Int>()
            val results = mutableListOf<Boolean>()
            val owner = FeedbackResource<TestFeedbackResource> { released.add(it.id) }
            var creations = 0
            var complete: ((Boolean) -> Unit)? = null
            val create: ((Boolean) -> Unit) -> TestFeedbackResource = {
                complete = it
                TestFeedbackResource(++creations)
            }
            owner.init()
            owner.acquire(true, create) { _, ready -> results.add(ready) }
            checkNotNull(complete)(false)
            checkNotNull(complete)(true)
            owner.canAcquire shouldBe false
            owner.current shouldBe null
            owner.ready shouldBe null
            repeat(5) { owner.acquire(true, create) shouldBe null }
            creations shouldBe 1
            released shouldBe listOf(1)
            results shouldBe listOf(false)
            owner.destroy()
            owner.init()
            owner.canAcquire shouldBe true
            owner.acquire(true, create)
            creations shouldBe 2
            checkNotNull(complete)(true)
            owner.ready?.id shouldBe 2
            owner.destroy()
            released shouldBe listOf(1, 2)
        }

        "factory exceptions do not strand an attempt or trigger an automatic retry loop" {
            var creations = 0
            val owner = FeedbackResource<TestFeedbackResource> { }
            val create: ((Boolean) -> Unit) -> TestFeedbackResource = {
                creations++
                throw IllegalStateException("backend unavailable")
            }
            owner.init()
            shouldThrow<IllegalStateException> { owner.acquire(true, create) }
            owner.canAcquire shouldBe false
            owner.current shouldBe null
            owner.ready shouldBe null
            owner.acquire(true, create) shouldBe null
            creations shouldBe 1
            owner.destroy()
            owner.init()
            owner.canAcquire shouldBe true
            owner.acquire(true, { TestFeedbackResource(2) })?.id shouldBe 2
            owner.destroy()
        }

        "an immediate initialization failure releases only after the factory returns" {
            val events = mutableListOf<String>()
            val owner = FeedbackResource<TestFeedbackResource> { events.add("release ${it.id}") }
            owner.init()
            val result = owner.acquire(
                enabled = true,
                create = { complete ->
                    complete(false)
                    events.add("return")
                    TestFeedbackResource(3)
                },
                onInitialized = { _, ready -> events.add("initialized $ready") },
            )
            result shouldBe null
            owner.ready shouldBe null
            events shouldBe listOf("return", "release 3", "initialized false")
            owner.destroy()
            events shouldBe listOf("return", "release 3", "initialized false")
        }

        "destroying during creation releases the returned resource without publishing it" {
            val released = mutableListOf<Int>()
            var notifications = 0
            val owner = FeedbackResource<TestFeedbackResource> { released.add(it.id) }
            owner.init()
            val result = owner.acquire(
                enabled = true,
                create = { complete ->
                    complete(true)
                    owner.destroy()
                    owner.init()
                    TestFeedbackResource(1)
                },
                onInitialized = { _, _ -> notifications++ },
            )
            result shouldBe null
            released shouldBe listOf(1)
            notifications shouldBe 0
            owner.current shouldBe null
            owner.acquire(true, { TestFeedbackResource(2) })?.id shouldBe 2
            owner.destroy()
            released shouldBe listOf(1, 2)
        }
    })
