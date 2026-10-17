/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

/**
 * Process-wide privacy snapshot used by services which outlive an IME view, such as the
 * clipboard listener. It deliberately stores no EditorInfo or user content.
 */
object PrivacyModeState {
    private val lock = Any()

    private var nextPersistencePermit = 1L

    private val persistencePermits = mutableSetOf<Long>()

    @Volatile
    private var snapshot = Snapshot()

    private data class Snapshot(
        val editorOwner: Long = 0,
        val editorPrivate: Boolean = false,
        val manualRequested: Boolean = false,
        val manual: Boolean = false,
        val processReplacementPending: Boolean = false,
        val epoch: Long = 0,
    ) {
        val active: Boolean
            get() = editorPrivate || manual || processReplacementPending
    }

    val active: Boolean
        get() = snapshot.active

    fun initialize(manual: Boolean) {
        synchronized(lock) {
            persistencePermits.clear()
            snapshot = Snapshot(
                manualRequested = manual,
                manual = manual,
                epoch = snapshot.epoch + 1,
            )
        }
    }

    /** Keep the old DE process private until it exits, including across editor/preference changes. */
    fun beginProcessReplacement() {
        synchronized(lock) {
            if (snapshot.processReplacementPending) return
            snapshot = snapshot.copy(
                processReplacementPending = true,
                epoch = snapshot.epoch + 1,
            )
        }
    }

    fun beginInput(
        owner: Long,
        mode: PrivacyMode,
        retainPrivacyUntilUpdate: Boolean = false,
    ) {
        require(owner != 0L)
        synchronized(lock) {
            snapshot = snapshot.copy(
                editorOwner = owner,
                editorPrivate = mode.password || mode.appRequested || retainPrivacyUntilUpdate,
                manualRequested = mode.manual,
                manual = mode.manual,
                epoch = snapshot.epoch + 1,
            )
        }
    }

    fun updateInput(
        owner: Long,
        mode: PrivacyMode,
        beforeOrdinary: () -> Unit = {},
    ): Boolean = synchronized(lock) {
        if (owner == 0L || snapshot.editorOwner != owner) return@synchronized false
        val updated = snapshot.copy(
            editorPrivate = mode.password || mode.appRequested,
            // A preference callback may have delivered a newer manual request while the native
            // editor bind was in flight. Never overwrite that newer process-wide decision with
            // the mode captured by the queued bind.
            manual = snapshot.manualRequested,
            epoch = snapshot.epoch + 1,
        )
        publishSnapshot(updated, beforeOrdinary)
        true
    }

    fun endInput(
        owner: Long,
        beforeOrdinary: () -> Unit = {},
    ) {
        synchronized(lock) {
            if (owner == 0L || snapshot.editorOwner != owner) return
            val updated = snapshot.copy(
                editorOwner = 0,
                editorPrivate = false,
                manual = snapshot.manualRequested,
                epoch = snapshot.epoch + 1,
            )
            publishSnapshot(updated, beforeOrdinary)
        }
    }

    fun setManual(
        enabled: Boolean,
        beforeOrdinary: () -> Unit = {},
    ) {
        synchronized(lock) {
            val enforced = enabled || (snapshot.editorOwner != 0L && snapshot.manual)
            if (snapshot.manualRequested == enabled && snapshot.manual == enforced) return
            val updated = snapshot.copy(
                manualRequested = enabled,
                manual = enforced,
                epoch = snapshot.epoch + 1,
            )
            publishSnapshot(updated, beforeOrdinary)
        }
    }

    /**
     * Publish an ordinary snapshot only after the caller has captured the private clipboard
     * boundary. The callback runs while [lock] still makes the previous private snapshot visible
     * to every persistence observer and permit acquisition.
     */
    private fun publishSnapshot(
        updated: Snapshot,
        beforeOrdinary: () -> Unit,
    ) {
        if (snapshot.active && !updated.active) beforeOrdinary()
        snapshot = updated
    }

    /**
     * Capture the privacy generation before reading content outside this lock. A later permit for
     * that content is accepted only if no editor or manual privacy boundary intervened.
     */
    fun observeOrdinaryPersistence(): Long? = synchronized(lock) {
        if (snapshot.active) null else snapshot.epoch
    }

    /**
     * Linearize a persistence operation before a privacy boundary.
     *
     * A permit that was granted while input was ordinary remains valid until the operation
     * releases it. Privacy changes reject every later acquisition immediately, without waiting
     * for Room or blocking the IME main thread.
     */
    fun acquirePersistencePermit(): Long? = synchronized(lock) {
        acquirePersistencePermitLocked(expectedEpoch = null)
    }

    /** Acquire a permit for content read after [observeOrdinaryPersistence]. */
    fun acquirePersistencePermit(observedEpoch: Long): Long? = synchronized(lock) {
        acquirePersistencePermitLocked(expectedEpoch = observedEpoch)
    }

    private fun acquirePersistencePermitLocked(expectedEpoch: Long?): Long? {
        if (snapshot.active || (expectedEpoch != null && snapshot.epoch != expectedEpoch)) return null
        val permit = nextPersistencePermit
        nextPersistencePermit = if (permit == Long.MAX_VALUE) 1L else permit + 1L
        check(persistencePermits.add(permit)) { "Persistence permit collision" }
        return permit
    }

    fun allowsPersistence(permit: Long): Boolean = synchronized(lock) {
        permit in persistencePermits
    }

    /**
     * Publish persisted content only while this permit still belongs to an ordinary session.
     *
     * The callback must stay short: it runs under the same lock as privacy transitions so either
     * the publication finishes before a private snapshot is visible, or the private transition
     * wins and the publication is rejected. A permit remains valid for its already-linearized
     * Room write across that boundary; this gate only prevents the result from being exposed in
     * memory while privacy is active.
     */
    fun publishIfOrdinary(
        permit: Long,
        publish: () -> Unit,
    ): Boolean = synchronized(lock) {
        if (snapshot.active || permit !in persistencePermits) return@synchronized false
        publish()
        true
    }

    fun releasePersistencePermit(permit: Long) {
        synchronized(lock) {
            persistencePermits.remove(permit)
        }
    }
}
