/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import java.util.concurrent.atomic.AtomicLong

/** Routes feedback requests to their owner thread without replaying a previous input's queue. */
internal class FeedbackRequestDispatcher(
    private val isOwnerThread: () -> Boolean,
    private val postToOwner: (() -> Unit) -> Unit,
) {
    private val generation = AtomicLong()

    /** Call on the owner thread before clearing pending feedback or replacing its resources. */
    fun invalidate() {
        generation.incrementAndGet()
    }

    fun dispatch(block: () -> Unit) {
        val submittedGeneration = generation.get()
        val guarded = {
            if (generation.get() == submittedGeneration) block()
        }
        if (isOwnerThread()) {
            guarded()
        } else {
            postToOwner(guarded)
        }
    }
}
