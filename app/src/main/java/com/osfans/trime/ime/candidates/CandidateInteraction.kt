/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import com.osfans.trime.core.CandidateSnapshot

/** An index has meaning only in the engine snapshot that supplied it. */
data class CandidateIdentity(val snapshot: CandidateSnapshot, val index: Int) {
    val isValid: Boolean get() = snapshot.isValid && index >= 0
}

/** Captures the row at touch-down so a rebound view cannot accept the release. */
class CandidateTouchState {
    private var pressed: CandidateIdentity? = null
    var isActive = false
        private set

    fun begin(identity: CandidateIdentity?) {
        pressed = identity
        isActive = true
    }

    fun cancel() {
        pressed = null
    }

    fun accepts(identity: CandidateIdentity?): Boolean = identity?.isValid == true && (!isActive || pressed == identity)

    fun finish() {
        pressed = null
        isActive = false
    }
}

/** Candidate text is not a unique identifier: duplicates and hash collisions are valid. */
internal fun candidateStableId(index: Int): Long = index.toLong()

/** Repeated layout notifications must not reset an expanded list's viewport. */
class CandidateViewportState {
    private var snapshot = CandidateSnapshot.EMPTY
    private var offset = -1

    fun update(nextSnapshot: CandidateSnapshot, nextOffset: Int): Boolean {
        val changed = snapshot != nextSnapshot || offset != nextOffset
        snapshot = nextSnapshot
        offset = nextOffset
        return changed
    }
}

/** A collapsed window is recreated; retain its row and signed pixel offset without retaining views. */
class CandidateViewportAnchor {
    private var snapshot = CandidateSnapshot.EMPTY
    private var offset = -1
    private var index: Int? = null
    private var pixelOffset = 0

    fun update(nextSnapshot: CandidateSnapshot, nextOffset: Int) {
        if (snapshot != nextSnapshot || offset != nextOffset || !nextSnapshot.isValid) {
            index = null
            pixelOffset = 0
        }
        snapshot = nextSnapshot
        offset = nextOffset
    }

    fun remember(
        currentSnapshot: CandidateSnapshot,
        currentOffset: Int,
        firstVisibleIndex: Int,
        total: Int = -1,
        firstVisiblePixelOffset: Int = 0,
    ): Boolean {
        if (!currentSnapshot.isValid || snapshot != currentSnapshot || offset != currentOffset ||
            currentOffset < 0 || firstVisibleIndex < currentOffset || (total >= 0 && firstVisibleIndex >= total)
        ) {
            return false
        }
        index = firstVisibleIndex
        pixelOffset = firstVisiblePixelOffset
        return true
    }

    fun restore(currentSnapshot: CandidateSnapshot, currentOffset: Int): Int? = index.takeIf {
        currentSnapshot.isValid && snapshot == currentSnapshot && offset == currentOffset
    }

    fun restoreOffset(currentSnapshot: CandidateSnapshot, currentOffset: Int): Int? = pixelOffset.takeIf { restore(currentSnapshot, currentOffset) != null }

    fun clear() {
        snapshot = CandidateSnapshot.EMPTY
        offset = -1
        index = null
        pixelOffset = 0
    }
}
