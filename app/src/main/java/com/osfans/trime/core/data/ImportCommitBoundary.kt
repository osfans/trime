// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A dispatcher may discard a committed import's result when its caller is cancelled. */
internal class ImportCommitBoundary {
    @Volatile
    var isCommitted = false
        private set

    fun markCommitted() {
        isCommitted = true
    }

    suspend fun <T> preserveChoice(
        restorePreviousChoice: (Throwable) -> Unit,
        releasePreviousChoice: () -> Unit,
        block: suspend () -> T,
    ): T {
        try {
            return block().also { releasePreviousChoice() }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                try {
                    if (isCommitted) releasePreviousChoice() else restorePreviousChoice(failure)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
            }
            throw failure
        }
    }
}
