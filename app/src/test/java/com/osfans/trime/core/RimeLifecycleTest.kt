// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException

class RimeLifecycleTest :
    StringSpec({
        "a lifecycle failure racing shutdown preserves the recoverable cause" {
            val lifecycle = RimeLifecycleRegistry()
            lifecycle.emitEvent(RimeLifecycle.Event.ON_START)
            lifecycle.emitEvent(RimeLifecycle.Event.ON_READY)
            val cause = IOException("in-session reload failed")
            lifecycle.fail(cause)
            shouldThrow<RimeUnavailableException> {
                lifecycle.emitEvent(RimeLifecycle.Event.ON_STOP)
            }.cause shouldBe cause
            shouldThrow<RimeUnavailableException> {
                lifecycle.emitEvent(RimeLifecycle.Event.ON_STOPPED)
            }.cause shouldBe cause
            lifecycle.emitEvent(RimeLifecycle.Event.ON_START)
            lifecycle.emitEvent(RimeLifecycle.Event.ON_READY)
        }

        "startup failure releases waiters and a new generation can become ready" {
            val lifecycle = RimeLifecycleRegistry()
            lifecycle.emitEvent(RimeLifecycle.Event.ON_START)
            val cause = IOException("asset copy failed")
            lifecycle.fail(cause)
            withTimeout(5000) {
                shouldThrow<RimeUnavailableException> { lifecycle.whenReady {} }.cause shouldBe cause
            }
            lifecycle.emitEvent(RimeLifecycle.Event.ON_START)
            lifecycle.failureCause shouldBe null
            lifecycle.emitEvent(RimeLifecycle.Event.ON_READY)
            lifecycle.whenReady { 7 } shouldBe 7
        }

        "clients joining during shutdown survive into the next generation" {
            val lifecycle = RimeLifecycleRegistry()
            lifecycle.emitEvent(RimeLifecycle.Event.ON_START)
            lifecycle.emitEvent(RimeLifecycle.Event.ON_READY)
            val old = lifecycle.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
            lifecycle.emitEvent(RimeLifecycle.Event.ON_STOP)
            old.join()
            old.isCancelled shouldBe true
            val next = lifecycle.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
            lifecycle.emitEvent(RimeLifecycle.Event.ON_STOPPED)
            next.isActive shouldBe true
            lifecycle.emitEvent(RimeLifecycle.Event.ON_START)
            lifecycle.emitEvent(RimeLifecycle.Event.ON_READY)
            next.isActive shouldBe true
            lifecycle.emitEvent(RimeLifecycle.Event.ON_STOP)
            next.join()
            next.isCancelled shouldBe true
            lifecycle.emitEvent(RimeLifecycle.Event.ON_STOPPED)
        }
    })
