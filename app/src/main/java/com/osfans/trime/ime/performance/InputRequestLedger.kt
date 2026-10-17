/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.performance

/** Bounded diagnostics only. Callers synchronize on this ledger; input jobs own their requests. */
internal class InputRequestLedger(private val capacity: Int) {
    private val retained = LinkedHashMap<Long, InputRequest>()

    init {
        require(capacity > 0)
    }

    val values get() = retained.values

    operator fun get(id: Long): InputRequest? = retained[id]

    fun record(request: InputRequest) {
        if (retained.size == capacity) {
            val oldest = retained.entries.iterator()
            oldest.next().value.evicted = true
            oldest.remove()
        }
        retained[request.id] = request
    }

    fun committed(id: Long, editor: Long) {
        retained[id]?.takeIf { it.editor == editor }?.committed = true
    }

    fun finish(request: InputRequest, failed: Boolean): Completion = Completion(
        // A late commit can no longer update an evicted request. Its absence cannot prove cancellation.
        cancelled = !request.evicted && !request.hasSnapshot && !request.committed,
        failed = failed,
    )

    fun clear() = retained.clear()

    data class Completion(val cancelled: Boolean, val failed: Boolean)
}
