/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CursorAnchorSubscriptionTest :
    StringSpec({
        "ordinary soft keyboards never send redundant unsubscribe requests" {
            val subscription = CursorAnchorSubscription()
            repeat(100) { subscription.update(false) { error("No monitor was requested") } shouldBe false }
        }
        "accepted monitoring is enabled and disabled once per transition" {
            val subscription = CursorAnchorSubscription()
            val requests = mutableListOf<Boolean>()
            val request: (Boolean) -> Boolean = {
                requests.add(it)
                true
            }
            repeat(2) { subscription.update(true, request) shouldBe true }
            repeat(2) { subscription.update(false, request) shouldBe false }
            requests shouldBe listOf(true, false)
        }
        "a rejected monitor may be retried without an unnecessary unsubscribe" {
            val subscription = CursorAnchorSubscription()
            subscription.update(true) { false } shouldBe false
            subscription.update(false) { error("The monitor was rejected") } shouldBe false
            subscription.update(true) { true } shouldBe true
        }
        "new editors neither inherit nor cancel the previous editor's monitor" {
            val subscription = CursorAnchorSubscription()
            subscription.update(true) { true } shouldBe true
            subscription.reset()
            subscription.update(false) { error("The previous connection is inactive") } shouldBe false
            var requested = false
            subscription.update(true) {
                requested = it
                true
            } shouldBe true
            requested shouldBe true
        }
    })
