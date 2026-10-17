/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CandidateSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CandidateInteractionTest :
    StringSpec({
        val identity = CandidateIdentity(CandidateSnapshot(7, 11, 19), 2)

        "a refresh between touch down and release cannot select the replacement row" {
            val touch = CandidateTouchState()
            touch.begin(identity)
            touch.accepts(identity) shouldBe true
            listOf(
                identity.copy(index = 3),
                identity.copy(snapshot = identity.snapshot.copy(generation = 12)),
                identity.copy(snapshot = identity.snapshot.copy(editorToken = 8)),
                identity.copy(snapshot = identity.snapshot.copy(requestId = 20)),
            ).forEach { replacement -> touch.accepts(replacement) shouldBe false }
            touch.finish()
            touch.begin(identity.copy(snapshot = identity.snapshot.copy(generation = 12)))
            touch.accepts(identity.copy(snapshot = identity.snapshot.copy(generation = 12))) shouldBe true
        }

        "cancelled scrolling and rebound touches cannot revive by restoring the same row" {
            val touch = CandidateTouchState()
            touch.begin(identity)
            touch.cancel()
            touch.accepts(identity) shouldBe false
            touch.accepts(identity.copy(index = 3)) shouldBe false
            touch.finish()
            touch.begin(identity)
            touch.accepts(identity) shouldBe true
        }

        "accessibility actions work without a touch but require a valid row identity" {
            val touch = CandidateTouchState()
            touch.accepts(identity) shouldBe true
            touch.accepts(null) shouldBe false
            touch.accepts(identity.copy(index = -1)) shouldBe false
            touch.accepts(identity.copy(snapshot = CandidateSnapshot.EMPTY)) shouldBe false
            touch.begin(null)
            touch.accepts(identity) shouldBe false
        }

        "duplicate text and real string hash collisions receive separate stable row IDs" {
            val first = CandidateProto("FB", "", "")
            val collision = CandidateProto("Ea", "", "")
            first.hashCode() shouldBe collision.hashCode()
            val rows = listOf(first, collision, first).mapIndexed { index, candidate ->
                CandidateRow(candidate, index, identity.snapshot)
            }
            rows.map { candidateStableId(it.index) }.toSet().size shouldBe 3
            candidateStableId(Int.MAX_VALUE) shouldBe Int.MAX_VALUE.toLong()
        }

        "reopening and repeated layout notifications retain one expanded snapshot viewport" {
            val viewport = CandidateViewportState()
            viewport.update(identity.snapshot, 16) shouldBe true
            repeat(100) { viewport.update(identity.snapshot, 16) shouldBe false }
            viewport.update(identity.snapshot, 0) shouldBe true
            viewport.update(identity.snapshot, 0) shouldBe false
            viewport.update(identity.snapshot.copy(generation = 12), 0) shouldBe true
            viewport.update(identity.snapshot.copy(editorToken = 8), 0) shouldBe true
        }

        "destroyed expanded windows share their row and signed pixel position in the current source" {
            val anchor = CandidateViewportAnchor()
            anchor.update(identity.snapshot, 16)
            anchor.remember(identity.snapshot, 16, 53, firstVisiblePixelOffset = -71) shouldBe true
            // These are separate window instances, rather than reused View or paging caches.
            repeat(2) {
                CandidateViewportState().update(identity.snapshot, 16) shouldBe true
                anchor.restore(identity.snapshot, 16) shouldBe 53
                anchor.restoreOffset(identity.snapshot, 16) shouldBe -71
            }
            repeat(100) {
                anchor.update(identity.snapshot, 16)
                anchor.restore(identity.snapshot, 16) shouldBe 53
                anchor.restoreOffset(identity.snapshot, 16) shouldBe -71
            }
            anchor.restore(identity.snapshot, 16) shouldBe 53
            anchor.restoreOffset(identity.snapshot, 16) shouldBe -71
            anchor.remember(identity.snapshot, 16, 15) shouldBe false
            anchor.remember(identity.snapshot, 16, 200, 200) shouldBe false
            anchor.restore(identity.snapshot, 16) shouldBe 53
            anchor.restoreOffset(identity.snapshot, 16) shouldBe -71
            anchor.remember(identity.snapshot, 16, 54, firstVisiblePixelOffset = 9) shouldBe true
            anchor.restore(identity.snapshot, 16) shouldBe 54
            anchor.restoreOffset(identity.snapshot, 16) shouldBe 9
            anchor.remember(identity.snapshot, 16, 54) shouldBe true
            anchor.restoreOffset(identity.snapshot, 16) shouldBe 0
        }

        "changed editor engine generation request or paging offset invalidates both stored coordinates" {
            listOf(
                identity.snapshot.copy(editorToken = 8) to 16,
                identity.snapshot.copy(generation = 12) to 16,
                identity.snapshot.copy(requestId = 20) to 16,
                identity.snapshot to 0,
                CandidateSnapshot.EMPTY to 16,
            ).forEach { (next, offset) ->
                val anchor = CandidateViewportAnchor()
                anchor.update(identity.snapshot, 16)
                anchor.remember(identity.snapshot, 16, 53, firstVisiblePixelOffset = -71) shouldBe true
                anchor.update(next, offset)
                anchor.restore(next, offset) shouldBe null
                anchor.restoreOffset(next, offset) shouldBe null
                anchor.restore(identity.snapshot, 16) shouldBe null
                anchor.restoreOffset(identity.snapshot, 16) shouldBe null
                anchor.remember(identity.snapshot, 16, 53) shouldBe false
                anchor.update(identity.snapshot, 16)
                anchor.restore(identity.snapshot, 16) shouldBe null
                anchor.restoreOffset(identity.snapshot, 16) shouldBe null
            }
            val anchor = CandidateViewportAnchor()
            anchor.update(identity.snapshot, 16)
            anchor.remember(identity.snapshot, 16, 53, firstVisiblePixelOffset = -71) shouldBe true
            anchor.clear()
            anchor.remember(identity.snapshot, 16, 53) shouldBe false
            anchor.restore(identity.snapshot, 16) shouldBe null
            anchor.restoreOffset(identity.snapshot, 16) shouldBe null
        }

        "late or out of range windows cannot replace a current partial row bookmark" {
            val anchor = CandidateViewportAnchor()
            anchor.update(identity.snapshot, 2)
            anchor.remember(identity.snapshot, 2, 3, 64, -71) shouldBe true
            anchor.remember(identity.snapshot.copy(requestId = 20), 2, 4, 64, 9) shouldBe false
            anchor.remember(identity.snapshot, 0, 4, 64, 9) shouldBe false
            listOf(-1, 1, 64, 100).forEach { index ->
                anchor.remember(identity.snapshot, 2, index, 64, 9) shouldBe false
            }
            anchor.remember(identity.snapshot, 2, 3, 0, 9) shouldBe false
            anchor.restore(identity.snapshot, 2) shouldBe 3
            anchor.restoreOffset(identity.snapshot, 2) shouldBe -71
            anchor.remember(identity.snapshot, 2, 4, -1, 9) shouldBe true
            anchor.restore(identity.snapshot, 2) shouldBe 4
            anchor.restoreOffset(identity.snapshot, 2) shouldBe 9
        }
    })
