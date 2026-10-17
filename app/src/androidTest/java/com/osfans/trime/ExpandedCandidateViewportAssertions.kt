/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.RimeApi
import com.osfans.trime.ime.candidates.CandidateIdentity
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.candidates.unrolled.window.FlexboxUnrolledCandidateWindow
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.kodein.di.direct
import org.kodein.di.instance

/** Uses two newly constructed production windows in the real IME and verifies the editor commit. */
object ExpandedCandidateViewportAssertions {
    suspend fun verify(instrumentation: Instrumentation, input: InputView, service: TrimeInputMethodService, editor: EditText): Int {
        fun <T> main(block: () -> T): T {
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            return checkNotNull(outcome).getOrThrow()
        }

        fun phase(name: String, outcome: String, viewport: JSONObject? = null) {
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("expanded_viewport_phase", name)
                    putString("expanded_viewport_outcome", outcome)
                    putLong("expanded_viewport_uptime_ms", SystemClock.uptimeMillis())
                    viewport?.let { putString("expanded_viewport_state", it.toString()) }
                },
            )
        }

        suspend fun until(
            name: String,
            viewport: () -> JSONObject,
            onSatisfied: (() -> Unit)? = null,
            predicate: () -> Boolean,
        ) {
            fun observedState(): JSONObject = runCatching(viewport).getOrElse {
                JSONObject().put("diagnostic_error", it.javaClass.name).put("message", it.message)
            }
            var failedViewport: JSONObject? = null
            phase(name, "START")
            try {
                withTimeout(30_000) {
                    while (!main {
                            try {
                                if (predicate()) {
                                    onSatisfied?.invoke()
                                    true
                                } else {
                                    false
                                }
                            } catch (error: Throwable) {
                                // Capture a failing assertion before another main-thread frame can run.
                                failedViewport = observedState()
                                throw error
                            }
                        }
                    ) {
                        delay(40)
                    }
                }
            } catch (error: Throwable) {
                val observed = failedViewport ?: main { observedState() }
                phase(name, if (error is TimeoutCancellationException) "TIMEOUT" else "FAILED", observed)
                throw error
            }
            phase(name, "PASS")
        }

        suspend fun <T> native(block: suspend RimeApi.() -> T): T {
            var outcome: Result<T>? = null
            val job = main { service.postRimeJob { outcome = runCatching { block() } } }
            job.join()
            check(!job.isCancelled) { "Expanded candidate native job was cancelled" }
            return checkNotNull(outcome).getOrThrow()
        }

        fun event(view: View, down: Long, action: Int, x: Float, y: Float) {
            val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                check(view.dispatchTouchEvent(motion))
            } finally {
                motion.recycle()
            }
        }

        val manager = main { input.di.direct.instance<BoardWindowManager>() }
        val compact = main { input.di.direct.instance<CompactCandidateDelegate>() }
        val originalEditor = main { editor.text.toString() }
        val snapshot = main { compact.adapter.snapshot }
        check(snapshot.isValid && main { service.isCurrentEditor(snapshot.editorToken) })
        var checks = 0
        var active: FlexboxUnrolledCandidateWindow? = null
        var observedLayoutWindowIdentity: Int? = null
        var layoutCallbackCount = 0
        var layoutCallbackDiagnosticError: String? = null
        val layoutCallbackHistory = ArrayList<JSONObject>(8)

        fun list(): RecyclerView = checkNotNull(manager.view.findViewById<UnrolledCandidateLayout>(R.id.unrolled_candidate_view)).recyclerView
        fun first(window: FlexboxUnrolledCandidateWindow, recycler: RecyclerView): CandidateViewHolder? {
            val position = window.layoutManager.findFirstVisibleItemPosition()
            return recycler.findViewHolderForAdapterPosition(position) as? CandidateViewHolder
        }

        fun label(holder: CandidateViewHolder): TextView {
            fun descendants(view: View): Sequence<View> = sequence {
                yield(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
            }
            return descendants(holder.itemView).filterIsInstance<TextView>().first { it.text.toString() == holder.text }
        }

        fun firstReadable(recycler: RecyclerView): CandidateViewHolder? = (0 until recycler.childCount)
            .mapNotNull { recycler.getChildViewHolder(recycler.getChildAt(it)) as? CandidateViewHolder }
            .filter {
                val text = label(it)
                val visible = Rect()
                text.isShown && text.getGlobalVisibleRect(visible) && visible.height() == text.height && visible.width() > 0
            }
            .minByOrNull { it.idx }

        fun pixelOffset(window: FlexboxUnrolledCandidateWindow, recycler: RecyclerView, holder: CandidateViewHolder): Int = window.layoutManager.getDecoratedTop(holder.itemView) - recycler.paddingTop

        fun settled(recycler: RecyclerView): Boolean = recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
            recycler.isLaidOut && !recycler.isLayoutRequested && !recycler.isComputingLayout && !recycler.hasPendingAdapterUpdates()

        // Test-only diagnostics read live state on the main thread and never alter it.
        fun field(owner: Any, name: String): Any? {
            var type: Class<*>? = owner.javaClass
            while (type != null) {
                val current = type
                try {
                    return current.getDeclaredField(name).apply { isAccessible = true }.get(owner)
                } catch (_: NoSuchFieldException) {
                    type = current.superclass
                }
            }
            error("Missing diagnostic field: $name")
        }

        fun rectangle(view: View): JSONObject {
            val visible = Rect()
            val shown = view.getGlobalVisibleRect(visible)
            return JSONObject().put("global_visible", shown)
                .put("visible", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
                .put("local", JSONArray(listOf(view.left, view.top, view.right, view.bottom)))
                .put("height", view.height).put("width", view.width).put("is_shown", view.isShown)
        }

        fun callbackState(window: FlexboxUnrolledCandidateWindow, recycler: RecyclerView, target: Int): JSONObject {
            val items = window.adapter.snapshot().items
            val position = items.indexOfFirst { it.index == target && it.snapshot == window.adapter.snapshot }
            val holder = if (position >= 0) recycler.findViewHolderForAdapterPosition(position) as? CandidateViewHolder else null
            return JSONObject().put("uptime_ms", SystemClock.uptimeMillis())
                .put("attached", manager.isAttached(window)).put("current_editor", service.isCurrentEditor(snapshot.editorToken))
                .put("adapter_snapshot", window.adapter.snapshot.toString()).put("compact_snapshot", compact.adapter.snapshot.toString())
                .put("adapter_offset", window.adapter.offset).put("adapter_item_count", window.adapter.itemCount)
                .put("loaded_first_index", items.firstOrNull()?.index ?: JSONObject.NULL)
                .put("loaded_last_index", items.lastOrNull()?.index ?: JSONObject.NULL)
                .put("pending_scroll_index", field(window, "pendingScrollIndex") ?: JSONObject.NULL)
                .put("pending_scroll_position", field(window, "pendingScrollPosition") ?: JSONObject.NULL)
                .put("remembered_index", field(window, "rememberedIndex") ?: JSONObject.NULL)
                .put("remembered_pixel_offset", field(window, "rememberedPixelOffset") ?: JSONObject.NULL)
                .put("has_visible_bookmark", field(window, "hasVisibleBookmark") ?: JSONObject.NULL)
                .put("restoring_viewport", field(window, "restoringViewport") ?: JSONObject.NULL)
                .put("target_position", position).put("target_holder_index", holder?.idx ?: JSONObject.NULL)
                .put("target_holder_snapshot", holder?.snapshot?.toString() ?: JSONObject.NULL)
                .put("target_pixel_offset", holder?.let { pixelOffset(window, recycler, it) } ?: JSONObject.NULL)
                .put("first_visible_position", window.layoutManager.findFirstVisibleItemPosition())
                .put("first_holder_index", first(window, recycler)?.idx ?: JSONObject.NULL)
                .put("first_pixel_offset", first(window, recycler)?.let { pixelOffset(window, recycler, it) } ?: JSONObject.NULL)
                .put("layout_requested", recycler.isLayoutRequested).put("computing_layout", recycler.isComputingLayout)
                .put("pending_adapter_updates", recycler.hasPendingAdapterUpdates()).put("layout_suppressed", recycler.isLayoutSuppressed)
                .put("is_laid_out", recycler.isLaidOut).put("is_attached_to_window", recycler.isAttachedToWindow)
                .put("is_shown", recycler.isShown).put("scroll_state", recycler.scrollState)
                .put("can_scroll_forward", recycler.canScrollVertically(1)).put("can_scroll_backward", recycler.canScrollVertically(-1))
                .put("child_count", recycler.childCount).put("height", recycler.height).put("width", recycler.width)
                .put("parent_layout_requested", (recycler.parent as? View)?.isLayoutRequested ?: JSONObject.NULL)
        }

        fun viewport(
            window: FlexboxUnrolledCandidateWindow,
            recycler: RecyclerView? = manager.view.findViewById<UnrolledCandidateLayout>(R.id.unrolled_candidate_view)?.recyclerView,
            expectedIndex: Int? = null,
            expectedPixel: Int? = null,
            expectedReadable: Int? = null,
        ): JSONObject {
            val currentWindow = field(manager, "currentWindow")
            val items = window.adapter.snapshot().items
            val state = JSONObject()
                .put("expected_index", expectedIndex ?: JSONObject.NULL)
                .put("expected_pixel_offset", expectedPixel ?: JSONObject.NULL)
                .put("expected_readable_index", expectedReadable ?: JSONObject.NULL)
                .put("expected_snapshot", snapshot.toString())
                .put("current_window", currentWindow?.javaClass?.name ?: JSONObject.NULL)
                .put("current_window_identity", currentWindow?.let(System::identityHashCode) ?: JSONObject.NULL)
                .put("expected_window_identity", System.identityHashCode(window))
                .put("attached", manager.isAttached(window))
                .put("current_editor", service.isCurrentEditor(snapshot.editorToken))
                .put("adapter_snapshot", window.adapter.snapshot.toString())
                .put("compact_snapshot", compact.adapter.snapshot.toString())
                .put("adapter_offset", window.adapter.offset)
                .put("adapter_total", window.adapter.total)
                .put("adapter_item_count", window.adapter.itemCount)
                .put("loaded_item_count", items.size)
                .put("loaded_first_index", items.firstOrNull()?.index ?: JSONObject.NULL)
                .put("loaded_last_index", items.lastOrNull()?.index ?: JSONObject.NULL)
                .put("pending_scroll_index", field(window, "pendingScrollIndex") ?: JSONObject.NULL)
                .put("pending_scroll_position", field(window, "pendingScrollPosition") ?: JSONObject.NULL)
                .put("remembered_index", field(window, "rememberedIndex") ?: JSONObject.NULL)
                .put("remembered_pixel_offset", field(window, "rememberedPixelOffset") ?: JSONObject.NULL)
                .put("shared_index", compact.expandedViewportAnchor.restore(snapshot, window.adapter.offset) ?: JSONObject.NULL)
                .put("shared_pixel_offset", compact.expandedViewportAnchor.restoreOffset(snapshot, window.adapter.offset) ?: JSONObject.NULL)
            if (System.identityHashCode(window) == observedLayoutWindowIdentity) {
                state.put("layout_callback_count", layoutCallbackCount).put("layout_callback_history", JSONArray(layoutCallbackHistory))
                    .put("layout_callback_diagnostic_error", layoutCallbackDiagnosticError ?: JSONObject.NULL)
            }
            if (recycler == null) return state.put("recycler", JSONObject.NULL)
            fun holderState(holder: CandidateViewHolder?): Any {
                if (holder == null) return JSONObject.NULL
                return JSONObject().put("index", holder.idx).put("snapshot", holder.snapshot.toString())
                    .put("pixel_offset", pixelOffset(window, recycler, holder))
                    .put("item_rectangle", rectangle(holder.itemView))
                    .put(
                        "label_rectangle",
                        runCatching { rectangle(label(holder)) }.getOrElse {
                            JSONObject().put("error", it.javaClass.name)
                        },
                    )
            }
            val children = JSONArray()
            for (index in 0 until minOf(recycler.childCount, 24)) {
                children.put(holderState(recycler.getChildViewHolder(recycler.getChildAt(index)) as? CandidateViewHolder))
            }
            val readable = runCatching { holderState(firstReadable(recycler)) }.getOrElse {
                JSONObject().put("error", it.javaClass.name)
            }
            return state.put("recycler_identity", System.identityHashCode(recycler))
                .put("recycler_rectangle", rectangle(recycler))
                .put("scroll_state", recycler.scrollState).put("is_laid_out", recycler.isLaidOut)
                .put("layout_requested", recycler.isLayoutRequested).put("computing_layout", recycler.isComputingLayout)
                .put("pending_adapter_updates", recycler.hasPendingAdapterUpdates())
                .put("layout_manager", window.layoutManager.javaClass.name)
                .put("layout_callback_present", (recycler.parent as? UnrolledCandidateLayout)?.onCandidatesLaidOut != null)
                .put("parent_layout_requested", (recycler.parent as? View)?.isLayoutRequested ?: JSONObject.NULL)
                .put("parent_class", recycler.parent?.javaClass?.name ?: JSONObject.NULL)
                .put("grandparent_class", recycler.parent?.parent?.javaClass?.name ?: JSONObject.NULL)
                .put("first_visible_position", window.layoutManager.findFirstVisibleItemPosition())
                .put("first_holder", holderState(first(window, recycler)))
                .put("first_readable_holder", readable).put("child_count", recycler.childCount)
                .put("sampled_children", children)
        }

        try {
            suspend fun prepareScrolledWindow(): Triple<FlexboxUnrolledCandidateWindow, RecyclerView, Int> {
                val initial = main {
                    FlexboxUnrolledCandidateWindow(input.di).also {
                        active = it
                        manager.attachWindow(it)
                    }
                }
                until("initial-window", { viewport(initial) }) {
                    manager.isAttached(initial) && initial.adapter.snapshot == snapshot &&
                        list().isShown && list().isLaidOut && !list().isLayoutRequested && first(initial, list())?.snapshot == snapshot
                }
                val initialList = main { list() }
                val initialIndex = main { checkNotNull(first(initial, initialList)).idx }
                main {
                    val down = SystemClock.uptimeMillis()
                    val x = initialList.width / 2f
                    event(initialList, down, MotionEvent.ACTION_DOWN, x, initialList.height * 0.85f)
                    for (step in 1..6) event(initialList, down, MotionEvent.ACTION_MOVE, x, initialList.height * (0.85f - step * 0.1f))
                    event(initialList, down, MotionEvent.ACTION_UP, x, initialList.height * 0.25f)
                    initialList.stopScroll()
                }
                until("physical-scroll-idle", { viewport(initial, initialList, initialIndex) }) {
                    settled(initialList) &&
                        compact.adapter.snapshot == snapshot && initial.adapter.snapshot == snapshot &&
                        first(initial, initialList)?.let { it.snapshot == snapshot && it.idx > initialIndex } == true
                }
                return Triple(initial, initialList, initialIndex)
            }
            val (initial, initialList, initialIndex) = prepareScrolledWindow()
            suspend fun verifyPartialRowCollapse(): Triple<CandidateIdentity, Int, CandidateIdentity> {
                var partialRowHeight = 0
                val partial = main {
                    val holder = checkNotNull(first(initial, initialList))
                    check(holder.itemView.height > 9)
                    partialRowHeight = holder.itemView.height
                    // Only the bottom nine pixels of this same row should remain visible.
                    val wantedOffset = 9 - holder.itemView.height
                    CandidateIdentity(holder.snapshot, holder.idx) to wantedOffset
                }
                val partialScrollHistory = ArrayList<JSONObject>(8)
                var capturedSelected: Triple<CandidateIdentity, Int, CandidateIdentity>? = null
                until(
                    "partial-leading-row",
                    {
                        viewport(initial, initialList, partial.first.index, partial.second)
                            .put("fixture_scroll_adjustments", JSONArray(partialScrollHistory))
                    },
                    onSatisfied = {
                        check(capturedSelected == null) { "Expanded candidate collapse was captured more than once" }
                        // Predicate, row sampling and collapse share one main-thread dispatch.
                        capturedSelected = run {
                            check(settled(initialList))
                            check(compact.adapter.snapshot == snapshot && initial.adapter.snapshot == snapshot)
                            val holder = checkNotNull(first(initial, initialList))
                            check(holder.snapshot == snapshot && holder.idx > initialIndex)
                            val identity = CandidateIdentity(holder.snapshot, holder.idx)
                            val savedOffset = pixelOffset(initial, initialList, holder)
                            check(identity == partial.first && savedOffset == partial.second && savedOffset < 0)
                            val leadingLabel = label(holder)
                            val leadingVisible = Rect()
                            check(!leadingLabel.getGlobalVisibleRect(leadingVisible) || leadingVisible.height() < leadingLabel.height) {
                                "Partial leading row exposes a fully readable candidate label"
                            }
                            val readable = checkNotNull(firstReadable(initialList))
                            check(readable.snapshot == snapshot && readable.idx > holder.idx)
                            val readableIdentity = CandidateIdentity(readable.snapshot, readable.idx)
                            check(editor.text.toString() == originalEditor) { "Expanded scrolling committed editor text" }
                            // Read the row immediately before onDetached captures it, without an intervening frame.
                            manager.attachWindow(KeyboardWindow)
                            check(!manager.isAttached(initial)) { "Collapsed expanded window remains the current window" }
                            val restored = compact.expandedViewportAnchor.restore(snapshot, initial.adapter.offset)
                            check(restored == identity.index) {
                                "Collapsed anchor differs: expected=${identity.index}, actual=$restored, offset=${initial.adapter.offset}, snapshot=$snapshot"
                            }
                            check(compact.expandedViewportAnchor.restoreOffset(snapshot, initial.adapter.offset) == savedOffset) {
                                "Collapsed anchor lost its signed pixel offset: expected=$savedOffset"
                            }
                            Triple(identity, savedOffset, readableIdentity)
                        }
                    },
                ) {
                    if (!settled(initialList) || !manager.isAttached(initial) || !service.isCurrentEditor(snapshot.editorToken) ||
                        compact.adapter.snapshot != snapshot || initial.adapter.snapshot != snapshot
                    ) {
                        return@until false
                    }
                    val holder = first(initial, initialList) ?: return@until false
                    if (CandidateIdentity(holder.snapshot, holder.idx) != partial.first) return@until false
                    check(holder.itemView.height == partialRowHeight) { "Partial candidate row height changed" }
                    val beforeOffset = pixelOffset(initial, initialList, holder)
                    if (beforeOffset == partial.second) return@until true
                    check(partialScrollHistory.size < 8) { "Unable to prepare exact partial candidate viewport after eight scrolls" }
                    val requested = beforeOffset - partial.second
                    val beforeState = callbackState(initial, initialList, partial.first.index)
                    var consumed = 0
                    val observer = object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            consumed += dy
                        }
                    }
                    // scrollBy can consume pending layout work and only part of the requested
                    // distance. Prepare the fixture on settled frames; never relax its target.
                    initialList.addOnScrollListener(observer)
                    try {
                        initialList.scrollBy(0, requested)
                        initialList.stopScroll()
                    } finally {
                        initialList.removeOnScrollListener(observer)
                    }
                    val diagnostic = JSONObject().put("attempt", partialScrollHistory.size + 1)
                        .put("requested_dy", requested).put("on_scrolled_dy", consumed)
                        .put("before", beforeState).put("after", callbackState(initial, initialList, partial.first.index))
                    partialScrollHistory.add(diagnostic)
                    phase("partial-leading-row-adjustment", "OBSERVED", diagnostic)
                    // Recheck identity and exact offset on the next settled frame before collapse.
                    false
                }
                val selected = checkNotNull(capturedSelected)
                checks++
                return selected
            }
            val selected = verifyPartialRowCollapse()
            suspend fun verifyCollapsedOverlayRemoval() {
                // The normal 100ms exit Slide temporarily reparents the removed root into an overlay.
                until("old-window-overlay-removed", { viewport(initial, initialList, selected.first.index, selected.second, selected.third.index) }) {
                    !manager.view.isLayoutRequested && initialList.parent?.parent == null
                }
                main {
                    check(!manager.isAttached(initial)) { "Destroyed expanded window became current again" }
                    val restored = compact.expandedViewportAnchor.restore(snapshot, initial.adapter.offset)
                    check(restored == selected.first.index) {
                        "Destroyed anchor differs: expected=${selected.first.index}, actual=$restored, offset=${initial.adapter.offset}, snapshot=$snapshot"
                    }
                    check(compact.expandedViewportAnchor.restoreOffset(snapshot, initial.adapter.offset) == selected.second)
                }
                checks++
            }
            verifyCollapsedOverlayRemoval()

            suspend fun verifyReopenedViewport(): Pair<FlexboxUnrolledCandidateWindow, RecyclerView> {
                val reopened = main {
                    FlexboxUnrolledCandidateWindow(input.di).also {
                        active = it
                        manager.attachWindow(it)
                        val window = it
                        val layout = field(window, "candidateLayout") as UnrolledCandidateLayout
                        val originalCallback = checkNotNull(layout.onCandidatesLaidOut)
                        observedLayoutWindowIdentity = System.identityHashCode(window)
                        layout.onCandidatesLaidOut = {
                            val before = runCatching {
                                callbackState(window, layout.recyclerView, selected.first.index)
                            }
                            try {
                                originalCallback()
                            } finally {
                                if (layoutCallbackCount < Int.MAX_VALUE) layoutCallbackCount++
                                runCatching {
                                    val after = runCatching {
                                        callbackState(window, layout.recyclerView, selected.first.index)
                                    }
                                    fun state(observed: Result<JSONObject>): JSONObject = observed.getOrElse { error ->
                                        JSONObject().put("diagnostic_error", error.javaClass.name)
                                    }
                                    val record = JSONObject().put("before", state(before)).put("after", state(after))
                                        .put("callback_number", layoutCallbackCount)
                                    if (layoutCallbackHistory.size == 8) layoutCallbackHistory.removeAt(0)
                                    layoutCallbackHistory.add(record)
                                }.onFailure { error -> layoutCallbackDiagnosticError = error.javaClass.name }
                            }
                        }
                    }
                }
                check(reopened !== initial && reopened.adapter !== initial.adapter && reopened.layoutManager !== initial.layoutManager)
                until("reopened-viewport", { viewport(reopened, expectedIndex = selected.first.index, expectedPixel = selected.second, expectedReadable = selected.third.index) }) {
                    manager.isAttached(reopened) && reopened.adapter.snapshot == snapshot &&
                        list().isShown && !list().isLayoutRequested && first(reopened, list())?.let {
                            CandidateIdentity(it.snapshot, it.idx) == selected.first && pixelOffset(reopened, list(), it) == selected.second
                        } == true && firstReadable(list())?.let { CandidateIdentity(it.snapshot, it.idx) == selected.third } == true
                }
                val reopenedList = main { list() }
                check(reopenedList !== initialList)
                checks++
                return reopened to reopenedList
            }
            val (reopened, reopenedList) = verifyReopenedViewport()
            suspend fun verifyRepeatedRelayout() {
                repeat(2) { round ->
                    main { reopenedList.requestLayout() }
                    until("ordinary-relayout-${round + 1}", { viewport(reopened, reopenedList, selected.first.index, selected.second, selected.third.index) }) {
                        !reopenedList.isLayoutRequested && first(reopened, reopenedList)?.let {
                            CandidateIdentity(it.snapshot, it.idx) == selected.first && pixelOffset(reopened, reopenedList, it) == selected.second
                        } == true && firstReadable(reopenedList)?.let { CandidateIdentity(it.snapshot, it.idx) == selected.third } == true
                    }
                    check(main { editor.text.toString() == originalEditor }) { "Reopening or relayout committed editor text" }
                    checks++
                }
            }
            verifyRepeatedRelayout()
            suspend fun verifyRestoredPointerCommit() {
                val expected = native { getCandidates(selected.third.index, 1, snapshot).single().text }
                var commitStart = -1
                var committedBaseline = ""
                val expectedEditor = main {
                    val value = editor.text
                    val start = BaseInputConnection.getComposingSpanStart(value)
                    val end = BaseInputConnection.getComposingSpanEnd(value)
                    if (start >= 0 && end >= start) {
                        commitStart = start
                        committedBaseline = value.toString().substring(0, start) + value.toString().substring(end)
                        value.toString().substring(0, start) + expected + value.toString().substring(end)
                    } else {
                        check(editor.selectionStart == editor.selectionEnd && editor.selectionStart >= 0)
                        val caret = editor.selectionStart
                        commitStart = caret
                        committedBaseline = value.toString()
                        value.toString().substring(0, caret) + expected + value.toString().substring(caret)
                    }
                }
                main {
                    val holder = checkNotNull(firstReadable(reopenedList))
                    check(CandidateIdentity(holder.snapshot, holder.idx) == selected.third && holder.text == expected) {
                        val observed = viewport(reopened, reopenedList, selected.first.index, selected.second, selected.third.index)
                            .put("identity_matches", CandidateIdentity(holder.snapshot, holder.idx) == selected.third)
                            .put("native_text_matches", holder.text == expected)
                            .put("checked_holder_index", holder.idx)
                            .put("checked_holder_snapshot", holder.snapshot.toString())
                            .put("expected_pointer_index", selected.third.index)
                            .put("expected_pointer_snapshot", selected.third.snapshot.toString())
                        phase("restored-pointer-target", "FAILED", observed)
                        "Check failed."
                    }
                    val child = holder.itemView
                    val left = maxOf(0, child.left)
                    val right = minOf(reopenedList.width, child.right)
                    val top = maxOf(0, child.top)
                    val bottom = minOf(reopenedList.height, child.bottom)
                    check(left < right && top < bottom)
                    val down = SystemClock.uptimeMillis()
                    event(reopenedList, down, MotionEvent.ACTION_DOWN, (left + right) / 2f, (top + bottom) / 2f)
                    event(reopenedList, down, MotionEvent.ACTION_UP, (left + right) / 2f, (top + bottom) / 2f)
                }
                until("native-exact-editor-commit", { viewport(reopened, reopenedList, selected.first.index, selected.second, selected.third.index) }) {
                    editor.text.toString() == expectedEditor
                }
                native { check(getRawInput().isEmpty()) }
                instrumentation.waitForIdleSync()
                check(main { editor.text.toString() == expectedEditor }) { "Restored candidate committed more than once or selected another entry" }
                checks++
                // Remove only this test's verified native commit; later fixtures own the same editor.
                main {
                    check(service.isCurrentEditor(snapshot.editorToken) && service.currentInputEditorInfo?.fieldId == editor.id)
                    check(editor.text.toString() == expectedEditor && commitStart >= 0)
                    check(BaseInputConnection.getComposingSpanStart(editor.text) < 0 && BaseInputConnection.getComposingSpanEnd(editor.text) < 0)
                    val commitEnd = commitStart + expected.length
                    check(commitEnd <= editor.text.length && editor.text.substring(commitStart, commitEnd) == expected)
                    val connection = checkNotNull(service.currentInputConnection)
                    check(connection.setSelection(commitEnd, commitEnd))
                    check(connection.deleteSurroundingText(expected.length, 0))
                }
                withTimeout(30_000) {
                    while (!main {
                            check(service.isCurrentEditor(snapshot.editorToken) && service.currentInputEditorInfo?.fieldId == editor.id)
                            editor.text.toString() == committedBaseline
                        }
                    ) {
                        delay(40)
                    }
                }
                native { check(getRawInput().isEmpty()) }
                instrumentation.waitForIdleSync()
                main {
                    check(service.isCurrentEditor(snapshot.editorToken) && service.currentInputEditorInfo?.fieldId == editor.id)
                    check(editor.text.toString() == committedBaseline) { "Expanded candidate fixture changed pre-existing editor text" }
                }
            }
            verifyRestoredPointerCommit()
        } finally {
            main { if (active?.let(manager::isAttached) == true) manager.attachWindow(KeyboardWindow) }
        }
        return checks
    }
}
