// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.candidates

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CandidatePinRulesTest :
    StringSpec({
        "pins are isolated by exact schema and input, and replace without duplicates" {
            val a = CandidatePin("full", "xinghe", "星河")
            val b = CandidatePin("nine", "946443", "星河")
            val rules = CandidatePinRules(listOf(a, b))
            rules.replace(rules.pin(a.copy(text = "形合")))
            rules.entries shouldBe listOf(b, a.copy(text = "形合"))
            rules.reset("full", "xinghe") shouldBe listOf(b)
            rules.reset("full", "xing") shouldBe rules.entries
        }
        "duplicate and malformed stored keys are rejected instead of silently losing a pin" {
            val entry = CandidatePin("full", "xinghe", "星河")
            shouldThrow<IllegalArgumentException> { CandidatePinRules(listOf(entry, entry)) }
            shouldThrow<IllegalArgumentException> { CandidatePinRules(listOf(entry.copy(text = ""))) }
            shouldThrow<IllegalArgumentException> { CandidatePinRules(listOf(entry.copy(input = "x\u0000y"))) }
        }
        "replacing at capacity remains possible but new keys cannot grow storage unbounded" {
            val initial = List(CandidatePinRules.MAX_ENTRIES) { CandidatePin("full", "code$it", "phrase$it") }
            val rules = CandidatePinRules(initial)
            rules.pin(initial.first().copy(text = "replacement")).size shouldBe initial.size
            shouldThrow<IllegalArgumentException> { rules.pin(CandidatePin("nine", "222", "example")) }
            rules.entries shouldBe initial
        }
    })
