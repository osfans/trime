/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.performance

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import com.osfans.trime.BuildConfig
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.ime.core.PrivacyModeState
import java.util.concurrent.atomic.AtomicLong

/** Opt-in release diagnostics. Bounded numeric state, no content, disabled in private editors. */
object InputLatencyLog {
    private const val CAPACITY = 256
    private val requestIds = AtomicLong()
    private val cacheIds = AtomicLong()
    private val requests = InputRequestLedger(CAPACITY)

    val enabled: Boolean get() = BuildConfig.PERFORMANCE_TRACING && !PrivacyModeState.active

    fun now(): Long = SystemClock.elapsedRealtimeNanos()

    fun nextCacheId(): Long = cacheIds.incrementAndGet()

    fun request(editor: Long, feedbackNanos: Long): InputRequest? {
        if (!enabled) return null
        val request = InputRequest(requestIds.incrementAndGet(), editor, now(), feedbackNanos)
        synchronized(requests) {
            requests.record(request)
        }
        write(
            "request",
            request.id,
            editor,
            0,
            request.submittedNanos,
            " feedback_ns=$feedbackNanos submitted_ns=${request.submittedNanos}",
        )
        return request
    }

    fun engineStart(request: InputRequest?) {
        if (request != null && request.started.compareAndSet(false, true)) {
            write("engine_start", request.id, request.editor, 0)
        }
    }

    fun rimeQueued(request: InputRequest?) {
        if (request != null && request.queued.compareAndSet(false, true)) {
            write("rime_queued", request.id, request.editor, 0)
        }
    }

    fun computed(request: InputRequest?, snapshot: CandidateSnapshot) {
        if (request == null) return
        synchronized(requests) {
            request.hasSnapshot = true
            request.snapshotGeneration = snapshot.generation
            write("compute_end", request.id, snapshot.editorToken, snapshot.generation)
            if (request.retired) {
                request.supersededGeneration = snapshot.generation
                stale(snapshot)
            }
        }
    }

    fun candidateDraw(snapshot: CandidateSnapshot, hasCandidates: Boolean = true) {
        if (!enabled || !snapshot.isValid || snapshot.requestId == 0L) return
        synchronized(requests) {
            val request = requests[snapshot.requestId] ?: return
            if (request.editor != snapshot.editorToken || request.drawn ||
                request.snapshotGeneration != snapshot.generation || request.supersededGeneration == snapshot.generation
            ) {
                return
            }
            request.drawn = true
            write(if (hasCandidates) "candidate_draw" else "candidate_clear", snapshot.requestId, snapshot.editorToken, snapshot.generation)
        }
    }

    fun editorCommit(snapshot: CandidateSnapshot) {
        if (snapshot.requestId == 0L) return
        synchronized(requests) {
            requests.committed(snapshot.requestId, snapshot.editorToken)
        }
        write("editor_commit", snapshot.requestId, snapshot.editorToken, snapshot.generation)
    }

    fun failedCommit(snapshot: CandidateSnapshot) {
        if (snapshot.requestId != 0L) write("job_failed", snapshot.requestId, snapshot.editorToken, snapshot.generation)
    }

    fun stale(snapshot: CandidateSnapshot) {
        if (snapshot.requestId != 0L) write("stale", snapshot.requestId, snapshot.editorToken, snapshot.generation)
    }

    /** Retire only the display snapshot. The input and its commit remain on the ordered bus. */
    fun supersede(snapshot: CandidateSnapshot) {
        if (!enabled || snapshot.requestId == 0L) return
        synchronized(requests) {
            val request = requests[snapshot.requestId] ?: return
            if (!request.drawn && request.snapshotGeneration == snapshot.generation && request.supersededGeneration != snapshot.generation) {
                request.supersededGeneration = snapshot.generation
                stale(snapshot)
            }
        }
    }

    fun finish(request: InputRequest?, failed: Boolean) {
        if (request != null) {
            val completion = synchronized(requests) { requests.finish(request, failed) }
            if (completion.cancelled) {
                write("cancelled", request.id, request.editor, 0)
            }
            if (completion.failed) {
                write("job_failed", request.id, request.editor, request.snapshotGeneration)
            }
        }
    }

    fun reset() {
        synchronized(requests) {
            requests.values.forEach { request ->
                request.retired = true
                if (request.hasSnapshot && !request.drawn && request.supersededGeneration != request.snapshotGeneration) {
                    request.supersededGeneration = request.snapshotGeneration
                    stale(CandidateSnapshot(request.editor, request.snapshotGeneration, request.id))
                }
            }
            requests.clear()
        }
    }

    fun keyboardCache(windowId: Long, layouts: Int, keys: Int) {
        if (enabled) write("keyboard_cache", 0, 0, 0, extra = " window=$windowId layouts=$layouts keys=$keys")
    }

    // The application's Timber trees rewrite tags; this numeric protocol needs a stable tag in release and debug.
    @SuppressLint("LogNotTimber")
    private fun write(event: String, request: Long, editor: Long, generation: Long, timestamp: Long = now(), extra: String = "") {
        if (enabled) Log.i("TrimePerf", "v=1 event=$event request=$request editor=$editor generation=$generation t_ns=$timestamp$extra")
    }
}
