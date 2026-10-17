/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.performance

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Numeric timing identity only; never retains a key, candidate, or committed text. */
class InputRequest(
    val id: Long,
    val editor: Long,
    val submittedNanos: Long,
    val feedbackNanos: Long,
) : AbstractCoroutineContextElement(Key) {
    val started = AtomicBoolean(false)
    val queued = AtomicBoolean(false)

    @Volatile var hasSnapshot = false

    @Volatile var committed = false

    @Volatile var evicted = false
    var retired = false
    var drawn = false
    var snapshotGeneration = 0L
    var supersededGeneration = 0L

    companion object Key : CoroutineContext.Key<InputRequest>
}
