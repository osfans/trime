/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.performance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputRequestLedgerTest :
    StringSpec({
        fun request(id: Long) = InputRequest(id, editor = 7, submittedNanos = id, feedbackNanos = 0)

        "the 257th request evicts only the oldest diagnostic object" {
            val ledger = InputRequestLedger(256)
            val first = request(1)
            ledger.record(first)
            for (id in 2L..256L) ledger.record(request(id))
            ledger.values.size shouldBe 256
            ledger[1] shouldBe first
            first.evicted shouldBe false

            val next = request(257)
            ledger.record(next)
            ledger.values.size shouldBe 256
            ledger[1] shouldBe null
            ledger[257] shouldBe next
            first.evicted shouldBe true
            ledger[2]?.evicted shouldBe false
        }

        "a late successful direct commit cannot turn an evicted request into cancellation" {
            val ledger = InputRequestLedger(256)
            val first = request(1)
            ledger.record(first)
            for (id in 2L..300L) ledger.record(request(id))
            ledger.values.size shouldBe 256
            first.evicted shouldBe true

            // This is the same update as the logger's late editor_commit after diagnostics eviction.
            ledger.committed(first.id, first.editor)
            first.committed shouldBe false
            ledger.finish(first, failed = false) shouldBe InputRequestLedger.Completion(cancelled = false, failed = false)
        }

        "a real failure remains reportable after eviction" {
            val ledger = InputRequestLedger(256)
            val first = request(1)
            ledger.record(first)
            for (id in 2L..257L) ledger.record(request(id))

            ledger.finish(first, failed = true) shouldBe InputRequestLedger.Completion(cancelled = false, failed = true)
        }

        "an unfinished retained request still reports cancellation and any real failure" {
            val ledger = InputRequestLedger(256)
            val pending = request(1)
            ledger.record(pending)

            ledger.finish(pending, failed = false) shouldBe InputRequestLedger.Completion(cancelled = true, failed = false)
            ledger.finish(pending, failed = true) shouldBe InputRequestLedger.Completion(cancelled = true, failed = true)
        }

        "retained successful commits and computed snapshots do not report cancellation" {
            val ledger = InputRequestLedger(256)
            val committed = request(1)
            val computed = request(2)
            ledger.record(committed)
            ledger.record(computed)
            ledger.committed(committed.id, editor = 8)
            committed.committed shouldBe false
            ledger.committed(committed.id, committed.editor)
            computed.hasSnapshot = true

            ledger.finish(committed, failed = false) shouldBe InputRequestLedger.Completion(cancelled = false, failed = false)
            ledger.finish(computed, failed = false) shouldBe InputRequestLedger.Completion(cancelled = false, failed = false)
            ledger.finish(computed, failed = true) shouldBe InputRequestLedger.Completion(cancelled = false, failed = true)
        }
    })
