// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled.window

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.candidates.unrolled.CandidatesPagingSource
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import kotlin.math.max

abstract class BaseUnrolledCandidateWindow(
    protected val context: Context,
    protected val service: TrimeInputMethodService,
    protected val rime: RimeSession,
    protected val theme: Theme,
    private val bar: QuickBar,
    private val windowManager: BoardWindowManager,
    private val compactCandidate: CompactCandidateModule,
) : BoardWindow.NoBarBoardWindow(),
    InputBroadcastReceiver {
    private lateinit var lifecycleCoroutineScope: LifecycleCoroutineScope
    private lateinit var candidateLayout: UnrolledCandidateLayout

    protected val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.generalStyle.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = ColorManager.getColor("candidate_separator_color")
        }
    }

    abstract fun onCreateCandidateLayout(): UnrolledCandidateLayout

    final override fun onCreateView(): View {
        candidateLayout =
            onCreateCandidateLayout().apply {
                recyclerView.apply {
                    // disable item cross-fade animation
                    itemAnimator = null
                }
            }
        return candidateLayout
    }

    abstract val adapter: PagingCandidateViewAdapter
    abstract val layoutManager: RecyclerView.LayoutManager

    private var offsetJob: Job? = null

    private val candidatesPager by lazy {
        Pager(PagingConfig(pageSize = 48)) {
            CandidatesPagingSource(
                rime,
                offset = adapter.offset,
            )
        }
    }

    private var candidatesSubmitJob: Job? = null

    override fun onAttached() {
        lifecycleCoroutineScope = candidateLayout.findViewTreeLifecycleOwner()!!.lifecycleScope
        bar.unrollButtonStateMachine.push(UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesAttached)
        offsetJob =
            lifecycleCoroutineScope.launch {
                compactCandidate.unrolledCandidateOffset.collect {
                    updateCandidatesWithOffset(it)
                }
            }
        candidatesSubmitJob =
            lifecycleCoroutineScope.launch {
                candidatesPager.flow.collect {
                    adapter.submitData(it)
                }
            }
    }

    fun bindCandidateUiViewHolder(holder: CandidateViewHolder) {
        holder.itemView.run {
            setOnClickListener { view ->
                rime.launchOnReady {
                    InputFeedbackManager.keyPressVibrate(view)
                    it.selectCandidate(holder.idx)
                }
            }
            setOnLongClickListener { view ->
                compactCandidate.showCandidateAction(holder.idx, holder.text, view)
                true
            }
        }
    }

    private fun updateCandidatesWithOffset(offset: Int) {
        val candidates = compactCandidate.adapter.items
        if (candidates.isEmpty()) {
            windowManager.attachWindow(KeyboardWindow)
        } else {
            adapter.refreshWithOffset(offset)
            lifecycleCoroutineScope.launch(Dispatchers.Main) {
                candidateLayout.resetPosition()
            }
        }
    }

    override fun onDetached() {
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesDetached,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (compactCandidate.adapter.run { isLastPage && (previous + itemCount) == adapter.offset }),
        )
        offsetJob?.cancel()
        candidatesSubmitJob?.cancel()
    }
}
