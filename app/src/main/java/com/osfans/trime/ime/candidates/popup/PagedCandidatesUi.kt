/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.view.isNotEmpty
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.Candidates
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.CandidateIdentity
import com.osfans.trime.ime.candidates.CandidateRow
import com.osfans.trime.ime.candidates.candidateStableId
import com.osfans.trime.ime.performance.InputLatencyLog
import splitties.views.dsl.core.Ui

class PagedCandidatesUi(
    override val ctx: Context,
    val scope: ThemeScope,
    private val onCandidateClick: (Int, CandidateSnapshot) -> Unit,
    private val onCandidateAction: (Int, String, View, CandidateSnapshot) -> Unit,
    private val onPrevPage: (CandidateSnapshot) -> Unit,
    private val onNextPage: (CandidateSnapshot) -> Unit,
    private val isCurrentSnapshot: (CandidateSnapshot) -> Boolean,
) : Ui {
    private var candidates = Candidates.Paged()

    private var isHorizontal = true

    sealed class UiHolder(
        open val ui: Ui,
    ) : RecyclerView.ViewHolder(ui.root) {
        var snapshot = CandidateSnapshot()

        class Candidate(
            override val ui: LabeledCandidateItemUi,
        ) : UiHolder(ui)

        class Pagination(
            override val ui: PaginationUi,
        ) : UiHolder(ui)
    }

    private val candidatesAdapter =
        object : BaseQuickAdapter<CandidateRow, UiHolder>() {
            init {
                // We must do this to avoid ArrayIndexOutOfBoundsException
                // https://github.com/google/flexbox-layout/issues/363#issuecomment-382949953
                setHasStableIds(true)
            }

            override fun getItemId(position: Int): Long = items.getOrNull(position)?.let { candidateStableId(it.index) } ?: Long.MAX_VALUE

            override fun getItemCount(items: List<CandidateRow>) = items.size + (if (candidates.hasPrevPage || candidates.hasNextPage) 1 else 0)

            override fun getItemViewType(
                position: Int,
                list: List<CandidateRow>,
            ) = if (position < list.size) 0 else 1

            override fun onCreateViewHolder(
                context: Context,
                parent: ViewGroup,
                viewType: Int,
            ): UiHolder = when (viewType) {
                0 -> UiHolder.Candidate(LabeledCandidateItemUi(ctx, scope))
                else -> UiHolder.Pagination(PaginationUi(ctx, scope))
            }.apply {
                // assign default LayoutParams, otherwise updateLayoutParams won't work
                ui.root.layoutParams = FlexboxLayoutManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            }

            override fun onBindViewHolder(
                holder: UiHolder,
                position: Int,
                item: CandidateRow?,
            ) {
                when (holder) {
                    is UiHolder.Candidate -> {
                        val row = item ?: return
                        val candidate = row.candidate
                        holder.snapshot = row.snapshot
                        holder.ui.root.identity = CandidateIdentity(row.snapshot, row.index)
                        holder.ui.update(candidate, position == candidates.highlighted, row.snapshot.isPrediction)
                        holder.ui.root.setOnClickListener {
                            onCandidateClick.invoke(row.index, row.snapshot)
                        }
                        holder.ui.root.setOnLongClickListener { v ->
                            onCandidateAction.invoke(row.index, candidate.text, v, row.snapshot)
                            true
                        }
                        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                            width = if (isHorizontal) WRAP_CONTENT else MATCH_PARENT
                        }
                    }

                    is UiHolder.Pagination -> {
                        val page = candidates
                        holder.snapshot = page.snapshot
                        holder.ui.update(page, { onPrevPage.invoke(page.snapshot) }, { onNextPage.invoke(page.snapshot) })
                        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                            flexGrow = 1f
                            width = if (isHorizontal) WRAP_CONTENT else MATCH_PARENT
                            alignSelf = if (isHorizontal) AlignItems.CENTER else AlignItems.STRETCH
                        }
                    }
                }
            }
        }

    private val candidatesLayoutManager =
        FlexboxLayoutManager(ctx).apply {
            flexWrap = FlexWrap.WRAP
        }

    override val root =
        object : RecyclerView(ctx) {
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (!InputLatencyLog.enabled) return
                val snapshot = candidates.snapshot
                if (!isShown || !isLaidOut || !isCurrentSnapshot(snapshot)) return
                for (index in 0 until childCount) {
                    val holder = getChildViewHolder(getChildAt(index)) as? UiHolder ?: return
                    if (holder.snapshot != snapshot) return
                }
                if (candidatesAdapter.itemCount == 0 || isNotEmpty()) {
                    InputLatencyLog.candidateDraw(snapshot, hasCandidates = candidates.candidates.isNotEmpty())
                }
            }
        }.apply {
            itemAnimator = null
            isFocusable = false
            adapter = candidatesAdapter
            layoutManager = candidatesLayoutManager
            overScrollMode = View.OVER_SCROLL_NEVER
        }

    fun update(
        candidates: Candidates.Paged,
        layout: PopupCandidatesLayout,
    ) {
        this.candidates = candidates
        this.isHorizontal = when (layout) {
            PopupCandidatesLayout.AUTOMATIC -> candidates.isHorizontalLayout
            else -> layout == PopupCandidatesLayout.HORIZONTAL
        }
        candidatesLayoutManager.apply {
            flexDirection = when (layout) {
                PopupCandidatesLayout.HORIZONTAL -> FlexDirection.ROW

                PopupCandidatesLayout.VERTICAL_REVERSE -> FlexDirection.COLUMN_REVERSE

                PopupCandidatesLayout.AUTOMATIC ->
                    if (isHorizontal) FlexDirection.ROW else FlexDirection.COLUMN

                else -> FlexDirection.COLUMN
            }
            alignItems = if (isHorizontal) AlignItems.BASELINE else AlignItems.STRETCH
        }
        candidatesAdapter.submitList(
            candidates.candidates.mapIndexed { index, candidate ->
                CandidateRow(candidate, index, candidates.snapshot)
            },
        )
    }

    /** Re-binds visible rows so they re-render with the current scheme's colors. */
    fun refreshColors() {
        candidatesAdapter.notifyItemRangeChanged(0, candidatesAdapter.itemCount)
    }
}
