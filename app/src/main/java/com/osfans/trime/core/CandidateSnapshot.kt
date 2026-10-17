/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

/** Java-side identity of one native candidate snapshot; contains no input content. */
data class CandidateSnapshot(
    val editorToken: Long = 0,
    val generation: Long = 0,
    val requestId: Long = 0,
    /** Next-word suggestions belong to a different state from an unfinished composition. */
    val isPrediction: Boolean = false,
) {
    val isValid: Boolean get() = editorToken > 0 && generation > 0

    companion object {
        val EMPTY = CandidateSnapshot()
    }
}

/** Mutated only on the Rime dispatcher, read by asynchronous candidate consumers. */
class CandidateSnapshotState {
    private var generation = 0L

    @Volatile var current: CandidateSnapshot = CandidateSnapshot.EMPTY
        private set

    fun publish(editorToken: Long, requestId: Long = 0, isPrediction: Boolean = false): CandidateSnapshot {
        check(generation < Long.MAX_VALUE)
        current = CandidateSnapshot(editorToken, ++generation, requestId, isPrediction)
        return current
    }

    fun invalidate() {
        current = CandidateSnapshot.EMPTY
    }

    fun accepts(expected: CandidateSnapshot?, editorToken: Long): Boolean = expected == null || (expected.isValid && expected.editorToken == editorToken && expected == current)

    /** A next-word suggestion requires a concrete row identity, never an implicit key binding. */
    fun acceptsSelection(expected: CandidateSnapshot?, editorToken: Long): Boolean = (expected != null || !current.isPrediction) && accepts(expected, editorToken)
}

class StaleCandidateSnapshotException : IllegalStateException("Candidate snapshot is no longer current")
