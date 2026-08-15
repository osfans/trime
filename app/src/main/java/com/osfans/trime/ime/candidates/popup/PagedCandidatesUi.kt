/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.popup

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.Candidates
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.recyclerview.recyclerView

class PagedCandidatesUi(
    override val ctx: Context,
    val theme: Theme,
    private val onCandidateClick: (Int) -> Unit,
    private val onCandidateAction: (Int, String, View) -> Unit,
    private val onPrevPage: () -> Unit,
    private val onNextPage: () -> Unit,
) : Ui {
    private var candidates = Candidates.Paged()

    private var isHorizontal = true

    sealed class UiHolder(
        open val ui: Ui,
    ) : RecyclerView.ViewHolder(ui.root) {
        class Candidate(
            override val ui: LabeledCandidateItemUi,
        ) : UiHolder(ui)

        class Pagination(
            override val ui: PaginationUi,
        ) : UiHolder(ui)
    }

    private val candidatesAdapter =
        object : BaseQuickAdapter<CandidateProto, UiHolder>() {
            init {
                // We must do this to avoid ArrayIndexOutOfBoundsException
                // https://github.com/google/flexbox-layout/issues/363#issuecomment-382949953
                setHasStableIds(true)
            }

            override fun getItemId(position: Int): Long = items.getOrNull(position).hashCode().toLong()

            override fun getItemCount(items: List<CandidateProto>) = items.size + (if (candidates.hasPrevPage || candidates.hasNextPage) 1 else 0)

            override fun getItemViewType(
                position: Int,
                list: List<CandidateProto>,
            ) = if (position < list.size) 0 else 1

            override fun onCreateViewHolder(
                context: Context,
                parent: ViewGroup,
                viewType: Int,
            ): UiHolder = when (viewType) {
                0 -> UiHolder.Candidate(LabeledCandidateItemUi(ctx, theme))
                else -> UiHolder.Pagination(PaginationUi(ctx, theme)).apply {
                    ui.prevIcon.setOnClickListener {
                        onPrevPage.invoke()
                    }
                    ui.nextIcon.setOnClickListener {
                        onNextPage.invoke()
                    }
                }
            }.apply {
                // assign default LayoutParams, otherwise updateLayoutParams won't work
                ui.root.layoutParams = FlexboxLayoutManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            }

            override fun onBindViewHolder(
                holder: UiHolder,
                position: Int,
                item: CandidateProto?,
            ) {
                when (holder) {
                    is UiHolder.Candidate -> {
                        val candidate = item ?: return
                        holder.ui.update(candidate, position == candidates.highlighted)
                        holder.ui.root.setOnClickListener {
                            onCandidateClick.invoke(position)
                        }
                        holder.ui.root.setOnLongClickListener { v ->
                            onCandidateAction.invoke(position, candidate.text, v)
                            true
                        }
                        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
                            width = if (isHorizontal) WRAP_CONTENT else MATCH_PARENT
                        }
                    }
                    is UiHolder.Pagination -> {
                        holder.ui.update(candidates)
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
        recyclerView {
            itemAnimator = null
            isFocusable = false
            adapter = candidatesAdapter
            layoutManager = candidatesLayoutManager
            overScrollMode = View.OVER_SCROLL_NEVER
        }

    fun update(
        candidates: Candidates.Paged,
        horizontal: Boolean,
        layout: PopupCandidatesLayout,
    ) {
        this.candidates = candidates
        this.isHorizontal = when (layout) {
            PopupCandidatesLayout.AUTOMATIC -> horizontal
            else -> layout == PopupCandidatesLayout.HORIZONTAL
        }
        candidatesLayoutManager.apply {
            flexDirection = when (layout) {
                PopupCandidatesLayout.HORIZONTAL -> FlexDirection.ROW
                PopupCandidatesLayout.VERTICAL_REVERSE -> FlexDirection.COLUMN_REVERSE
                PopupCandidatesLayout.AUTOMATIC ->
                    if (horizontal) FlexDirection.ROW else FlexDirection.COLUMN
                else -> FlexDirection.COLUMN
            }
            alignItems = if (isHorizontal) AlignItems.BASELINE else AlignItems.STRETCH
        }
        candidatesAdapter.submitList(candidates.candidates.toList())
    }
}
