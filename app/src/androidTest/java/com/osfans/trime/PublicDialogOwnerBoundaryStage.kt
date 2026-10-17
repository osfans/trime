/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Looper
import android.os.SystemClock
import com.osfans.trime.ui.common.dialog.PublicAlertDialogs
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.util.importErrorDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONObject

/** Auxiliary owner-liveness boundary; not a real importer or production200ms timing test. */
internal object PublicDialogOwnerBoundaryStage {
    fun verify(
        instrumentation: Instrumentation,
        owner: Activity,
        token: String,
        workDeadline: Long,
        report: JSONObject,
        passed: (String) -> Unit,
    ) {
        check(Looper.myLooper() != Looper.getMainLooper())
        fun budget(): Long = minOf(5_000L, workDeadline - SystemClock.uptimeMillis()).also {
            check(it > 0L) { "Owner boundary exceeded the original host work deadline" }
        }
        report.put("synthetic_action", true).put("loading_threshold_test_parameter_ms", 0)
            .put("real_importer_timing_tested", false).put("production_200ms_tested", false)
            .put("global_window_inventory_tested", false)
        var sameTurn: Result<Unit>? = null
        instrumentation.runOnMainSync {
            sameTurn = runCatching {
                check(!owner.isFinishing && !owner.isDestroyed && owner.hasWindowFocus())
                check(PublicAlertDialogs.canShow(owner))
                owner.finish()
                check(owner.isFinishing && !owner.isDestroyed) { "Not the actual pre-onDestroy finishing interval" }
                check(!PublicAlertDialogs.canShow(owner))
                val rejected = runCatching { PublicAlertDialogs.builder(owner) }.exceptionOrNull()
                check(rejected is IllegalStateException) { "The strict synchronous factory must still reject this owner" }
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                try {
                    val error = scope.async(start = CoroutineStart.UNDISPATCHED) {
                        owner.importErrorDialog("Synthetic ending-owner error $token")
                    }
                    check(error.isCompleted) { "Error-helper guard suspended beyond the same Main turn" }
                    runBlocking { error.await() } // Already completed; preserve the actual helper failure.
                } finally {
                    scope.cancel()
                }
                report.put("same_turn_finishing", owner.isFinishing)
                    .put("same_turn_destroyed", owner.isDestroyed)
                    .put("strict_builder_rejected", true).put("actual_import_error_returned", true)
            }
        }
        checkNotNull(sameTurn).getOrThrow()
        budget()
        passed("actual finish before onDestroy skips the shared error Window while the synchronous factory remains strict")

        fun loadingAction(failure: Throwable?) {
            var actionReached = false
            runBlocking {
                withTimeout(budget()) {
                    withLoadingDialog(owner, threshold = 0L) {
                        // The child launched first gets to enqueue its Main show attempt before this barrier.
                        yield()
                        withContext(Dispatchers.Main.immediate) {
                            check(owner.isFinishing || owner.isDestroyed)
                            check(!PublicAlertDialogs.canShow(owner))
                            actionReached = true
                        }
                        if (failure != null) throw failure
                    }
                }
            }
            check(actionReached) { "Controlled action was cancelled by the expired loading Window" }
        }
        loadingAction(null)
        report.put("controlled_loading_action_completed", true)
        passed("expired loading owner skips only the Window and lets the controlled action finish")
        val expected = IllegalArgumentException("Synthetic controlled action failure $token")
        val observed = runCatching { loadingAction(expected) }.exceptionOrNull()
        check(observed === expected) { "The original controlled action failure was replaced: $observed" }
        report.put("controlled_loading_original_failure_preserved", true)
        budget()
        passed("expired loading owner preserves the identical controlled action exception through the original finally")
    }
}
