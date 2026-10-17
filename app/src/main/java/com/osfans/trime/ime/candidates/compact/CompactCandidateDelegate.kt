/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.ContextThemeWrapper
import androidx.core.view.isNotEmpty
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.R
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.Candidates
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.CandidateViewportAnchor
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxVerticalDecoration
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.performance.InputLatencyLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import kotlin.math.max

class CompactCandidateDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    data class UnrolledCandidateState(
        val offset: Int,
        val total: Int,
        val highlighted: Int,
        val snapshot: CandidateSnapshot,
        val anchor: Int,
    )
    private val context: ContextThemeWrapper by instance()
    private val scope: ThemeScope by instance()
    private val inputView: InputView by instance()
    private val service: TrimeInputMethodService by instance()
    private val bar: InputBarDelegate by instance()
    private val rime: RimeSession by instance()

    private val theme: Theme
        get() = scope.theme

    private val fillStyle by AppPrefs.defaultInstance().keyboard.horizontalCandidateMode
    private val horizontalScroll by AppPrefs.defaultInstance().candidates.compactCandidateScroll

    private val maxSpanCountPref by lazy {
        AppPrefs.defaultInstance().keyboard.run {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                maxSpanCount
            } else {
                maxSpanCountLandscape
            }
        }
    }

    private var layoutMinWidth = 0
    private var layoutFlexGrow = 0f

    /**
     * (for [CompactCandidateMode.AUTO_FILL] only)
     * Second layout pass is needed when:
     * [^1] total candidates count < maxSpanCount && [^2] RecyclerView cannot display all of them
     * In that case, displayed candidates should be stretched evenly (by setting flexGrow to 1.0f).
     */
    private var secondLayoutPassNeeded = false
    private var secondLayoutPassDone = false

    private val _unrolledCandidateOffset =
        MutableSharedFlow<UnrolledCandidateState>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val unrolledCandidateOffset = _unrolledCandidateOffset.asSharedFlow()
    val expandedViewportAnchor = CandidateViewportAnchor()

    fun refreshUnrolled(childCount: Int) {
        val scrolling = layoutManager is LinearLayoutManager
        val offset = if (scrolling) 0 else childCount
        val anchor = if (scrolling) CompactCandidateLayoutManagers.firstVisibleIndex(layoutManager).coerceAtLeast(0) else offset
        expandedViewportAnchor.update(adapter.snapshot, offset)
        _unrolledCandidateOffset.tryEmit(UnrolledCandidateState(offset, adapter.total, adapter.highlightedIdx, adapter.snapshot, anchor))
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesUpdated,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (adapter.total == offset),
        )
    }

    /** Restyles the compact list after a scheme switch; visible rows re-bind. */
    fun refreshColors() {
        separatorDrawable.paint.color = scope.colors.candidateSeparatorColor
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    val adapter: CompactCandidateViewAdapter by lazy {
        CompactCandidateViewAdapter(scope).apply {
            setOnItemClickListener { _, view, _ ->
                val holder = this@CompactCandidateDelegate.view.findContainingViewHolder(view) as? CandidateViewHolder
                    ?: return@setOnItemClickListener
                val index = holder.idx
                val snapshot = holder.snapshot
                service.postRimeJob { selectCandidate(index, global = true, expectedSnapshot = snapshot) }
            }
            setOnItemLongClickListener { _, view, _ ->
                val holder = this@CompactCandidateDelegate.view.findContainingViewHolder(view) as? CandidateViewHolder
                    ?: return@setOnItemLongClickListener false
                inputView.showCandidateActionMenu(holder.idx, holder.text, view, global = true, snapshot = holder.snapshot)
                true
            }
        }
    }

    fun updateLayoutParams(minWidth: Int, flexGrow: Float) {
        layoutMinWidth = minWidth
        layoutFlexGrow = flexGrow
    }

    private val scrollingLayoutManager by lazy { CompactCandidateLayoutManagers.create(context, true, ::onLayoutCompleted) }
    private val wrappedLayoutManager by lazy { CompactCandidateLayoutManagers.create(context, false, ::onLayoutCompleted) }
    private var activeLayoutManager: RecyclerView.LayoutManager? = null

    val layoutManager: RecyclerView.LayoutManager
        get() = activeLayoutManager ?: desiredLayoutManager().also { activeLayoutManager = it }

    private fun desiredLayoutManager() = if (horizontalScroll) scrollingLayoutManager else wrappedLayoutManager

    private fun onLayoutCompleted(manager: RecyclerView.LayoutManager) {
        val count = manager.childCount
        if (manager is FlexboxLayoutManager && secondLayoutPassNeeded) {
            if (count < adapter.itemCount) {
                // Updating flexGrow requests another layout; apply it once.
                if (secondLayoutPassDone) return
                secondLayoutPassDone = true
                for (index in 0 until count) {
                    manager.getChildAt(index)!!.updateLayoutParams<FlexboxLayoutManager.LayoutParams> { flexGrow = 1f }
                }
            } else {
                secondLayoutPassNeeded = false
            }
        }
        refreshUnrolled(count)
    }

    private val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.style.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = scope.colors.candidateSeparatorColor
        }
    }

    val view: RecyclerView by lazy {
        object : RecyclerView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (!horizontalScroll && fillStyle == CompactCandidateMode.AUTO_FILL) {
                    val maxSpanCount = maxSpanCountPref.getValue()
                    layoutMinWidth = w / maxSpanCount - separatorDrawable.intrinsicWidth
                }
            }

            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (!InputLatencyLog.enabled) return
                val candidateAdapter = this@CompactCandidateDelegate.adapter
                val snapshot = candidateAdapter.snapshot
                if (!isShown || !isLaidOut || !service.isCurrentEditor(snapshot.editorToken) ||
                    snapshot != rime.run { candidateSnapshotCached }
                ) {
                    return
                }
                for (index in 0 until childCount) {
                    val holder = getChildViewHolder(getChildAt(index)) as? CandidateViewHolder ?: return
                    if (holder.snapshot != snapshot) return
                }
                if (candidateAdapter.itemCount == 0 || isNotEmpty()) {
                    InputLatencyLog.candidateDraw(snapshot, hasCandidates = candidateAdapter.itemCount > 0)
                }
            }
        }.apply {
            id = R.id.candidate_view
            itemAnimator = null
            isFocusable = false
            isFocusableInTouchMode = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                defaultFocusHighlightEnabled = false
            }
            adapter = this@CompactCandidateDelegate.adapter
            layoutManager = this@CompactCandidateDelegate.layoutManager
            addItemDecoration(FlexboxVerticalDecoration(separatorDrawable))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (horizontalScroll && dx != 0) refreshUnrolled(recyclerView.childCount)
                }
            })
        }
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        val (total, highlighted, candidates) = data

        val maxSpanCount = maxSpanCountPref.getValue()

        adapter.horizontalScroll = horizontalScroll
        val nextManager = desiredLayoutManager()
        if (layoutManager !== nextManager) {
            view.stopScroll()
            activeLayoutManager = nextManager
            view.layoutManager = nextManager
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
        if (data.snapshot != adapter.snapshot) {
            expandedViewportAnchor.clear()
            view.stopScroll()
            layoutManager.scrollToPosition(0)
        }

        when {
            horizontalScroll -> {
                layoutMinWidth = 0
                layoutFlexGrow = 0f
                secondLayoutPassNeeded = false
            }

            fillStyle == CompactCandidateMode.NEVER_FILL -> {
                layoutMinWidth = 0
                layoutFlexGrow = 0f
                secondLayoutPassNeeded = false
            }

            fillStyle == CompactCandidateMode.AUTO_FILL -> {
                layoutMinWidth = view.width / maxSpanCount - separatorDrawable.intrinsicWidth
                layoutFlexGrow = if (candidates.size < maxSpanCount) 0f else 1f
                // [^1] total candidates count < maxSpanCount
                secondLayoutPassNeeded = candidates.size < maxSpanCount
                secondLayoutPassDone = false
            }

            else -> {
                layoutMinWidth = 0
                layoutFlexGrow = 1f
                secondLayoutPassNeeded = false
            }
        }

        adapter.updateLayoutParams(layoutMinWidth, layoutFlexGrow)
        adapter.layoutFlexShrink = if (horizontalScroll) 0f else 1f
        adapter.updateCandidates(candidates, total, highlighted, data.snapshot)

        // not sure why empty candidates won't trigger `FlexboxLayoutManager#onLayoutCompleted()`
        if (candidates.isEmpty()) {
            refreshUnrolled(0)
        }
    }
}
