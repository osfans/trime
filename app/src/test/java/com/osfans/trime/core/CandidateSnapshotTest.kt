/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import com.osfans.trime.ime.candidates.CandidateRow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CandidateSnapshotTest :
    StringSpec({
        "successive snapshots of one editor are monotonically distinct" {
            val state = CandidateSnapshotState()
            val first = state.publish(editorToken = 7, requestId = 41)
            val second = state.publish(editorToken = 7, requestId = 41)
            second.generation shouldBe first.generation + 1
            state.accepts(first, 7) shouldBe false
            state.accepts(second, 7) shouldBe true

            state.invalidate()
            val third = state.publish(editorToken = 7, requestId = 42)
            third.generation shouldBe second.generation + 1
            state.accepts(second, 7) shouldBe false
            state.accepts(third, 7) shouldBe true
        }

        "invalidation revokes a snapshot before another result is published" {
            val state = CandidateSnapshotState()
            val shown = state.publish(7, 41)
            state.accepts(shown, 7) shouldBe true
            state.invalidate()
            state.current shouldBe CandidateSnapshot.EMPTY
            state.accepts(shown, 7) shouldBe false
        }

        "a matching generation cannot cross editors or request identities" {
            val state = CandidateSnapshotState()
            val shown = state.publish(7, 41)
            state.accepts(shown, 8) shouldBe false
            state.accepts(shown.copy(editorToken = 8), 8) shouldBe false
            state.accepts(shown.copy(requestId = 42), 7) shouldBe false
            val nextEditor = state.publish(8, 42)
            state.accepts(shown, 7) shouldBe false
            state.accepts(nextEditor, 8) shouldBe true
        }

        "empty or unowned snapshots cannot authorize candidate actions" {
            val state = CandidateSnapshotState()
            CandidateSnapshot.EMPTY.isValid shouldBe false
            state.accepts(CandidateSnapshot.EMPTY, 0) shouldBe false
            state.accepts(CandidateSnapshot.EMPTY, 7) shouldBe false
            val unowned = state.publish(editorToken = 0, requestId = 41)
            unowned.isValid shouldBe false
            state.accepts(unowned, 0) shouldBe false
        }

        "candidate correlation also works when performance tracing is disabled" {
            val state = CandidateSnapshotState()
            val shown = state.publish(editorToken = 7)
            shown.requestId shouldBe 0
            shown.isValid shouldBe true
            state.accepts(shown, 7) shouldBe true
        }

        "next-word rows require an explicit current identity and cannot impersonate composition rows" {
            val state = CandidateSnapshotState()
            val shown = state.publish(7, 41, isPrediction = true)
            state.acceptsSelection(null, 7) shouldBe false
            state.acceptsSelection(shown, 7) shouldBe true
            state.acceptsSelection(shown.copy(isPrediction = false), 7) shouldBe false
            state.acceptsSelection(shown, 8) shouldBe false

            val typed = state.publish(7, 42)
            state.acceptsSelection(shown, 7) shouldBe false
            state.acceptsSelection(typed, 7) shouldBe true
            state.acceptsSelection(null, 7) shouldBe true
        }

        "prediction identity survives an asynchronous row then expires at a boundary" {
            val state = CandidateSnapshotState()
            val shown = state.publish(7, isPrediction = true)
            val row = CandidateRow(CandidateProto("测试后词", "", ""), 0, shown)
            row.snapshot.isPrediction shouldBe true
            state.invalidate()
            state.acceptsSelection(row.snapshot, 7) shouldBe false
        }

        "an asynchronous candidate row retains its original snapshot and global index" {
            val state = CandidateSnapshotState()
            val origin = state.publish(7, 41)
            val row = CandidateRow(CandidateProto("candidate", "", ""), 112, origin)
            state.publish(7, 42)
            row.index shouldBe 112
            row.snapshot shouldBe origin
            state.accepts(row.snapshot, 7) shouldBe false
        }

        "JNI candidate and response constructors retain their original signatures" {
            val candidatesArray = Array<CandidateProto>::class.java
            val pagedConstructor = Candidates.Paged::class.java.getDeclaredConstructor(
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                candidatesArray,
            )
            val paged = pagedConstructor.newInstance(false, false, false, 0, emptyArray<CandidateProto>())
            paged.snapshot shouldBe CandidateSnapshot.EMPTY
            paged.isPrediction shouldBe false
            Candidates.Paged::class.java.getDeclaredMethod("setPrediction", Boolean::class.javaPrimitiveType)
                .invoke(paged, true)
            paged.isPrediction shouldBe true

            val bulkConstructor = Candidates.Bulk::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                candidatesArray,
            )
            val bulk = bulkConstructor.newInstance(-1, 0, emptyArray<CandidateProto>())
            bulk.snapshot shouldBe CandidateSnapshot.EMPTY
            bulk.isPrediction shouldBe false
            Candidates.Bulk::class.java.getDeclaredMethod("setPrediction", Boolean::class.javaPrimitiveType)
                .invoke(bulk, true)
            bulk.isPrediction shouldBe true

            val responseConstructor = RimeResponse::class.java.getDeclaredConstructor(
                CommitProto::class.java,
                CompositionProto::class.java,
                Candidates::class.java,
                StatusProto::class.java,
            )
            val response = responseConstructor.newInstance(CommitProto(null), CompositionProto(), bulk, StatusProto())
            response.candidates shouldBe bulk
            response.commit.text shouldBe null
        }
    })
