/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import com.osfans.trime.core.CandidateSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PublishedAccessibilityIdentityTest :
    StringSpec({
        val original = CandidateIdentity(CandidateSnapshot(7, 11, 19), 2)

        "service actions require a published node and reject each rebound identity dimension" {
            val state = PublishedAccessibilityIdentity<CandidateIdentity>()
            state.accepts(original) shouldBe false
            state.publish(original)
            state.accepts(original) shouldBe true
            listOf(
                original.copy(index = 3),
                original.copy(snapshot = original.snapshot.copy(editorToken = 8)),
                original.copy(snapshot = original.snapshot.copy(generation = 12)),
                original.copy(snapshot = original.snapshot.copy(requestId = 20)),
                original.copy(snapshot = original.snapshot.copy(isPrediction = true)),
            ).forEach { replacement ->
                state.accepts(replacement) shouldBe false
                state.publish(replacement)
                state.accepts(replacement) shouldBe true
                state.accepts(original) shouldBe false
                state.publish(original)
            }
        }

        "clearing a binding prevents an old node from reviving when the same identity returns" {
            val state = PublishedAccessibilityIdentity<Int>()
            state.publish(12)
            state.clear()
            state.accepts(12) shouldBe false
            state.accepts(null) shouldBe false
            state.publish(12)
            state.accepts(12) shouldBe true
            state.publish(null)
            state.accepts(12) shouldBe false
        }
    })
