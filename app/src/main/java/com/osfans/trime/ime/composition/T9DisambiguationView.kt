/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.R
import com.osfans.trime.core.T9Action
import com.osfans.trime.core.T9SpanProto
import com.osfans.trime.core.T9StateProto
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.BoundActionImageButton
import com.osfans.trime.ime.candidates.BoundActionTextView
import com.osfans.trime.ime.candidates.VersionedClickBinding
import splitties.dimensions.dp

/** Independent syllable controls. Hanzi remain in the existing candidate bar. */
// Requires an input-window theme and engine callback; not an XML widget.
@SuppressLint("ViewConstructor")
class T9DisambiguationView(
    context: Context,
    private val scope: ThemeScope,
    private val action: (Int, T9Action, Int, Int, String) -> Unit,
) : LinearLayout(context) {
    private var state = T9StateProto()
    private val normalTextColor get() = scope.colors.candidateTextColor
    private val selectedForeground get() = scope.colors.hilitedCandidateTextColor
    private val selectedBackground get() = scope.colors.hilitedCandidateBackColor
    private val segments = LinearLayout(context)
    private val segmentScroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(segments, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val adapter = ChoiceAdapter()
    private val labelContext = ContextThemeWrapper(context, R.style.Theme_TrimeAppTheme)
    private var layoutActive = false
    private var compositionPanelActive = false
    private var sideMode = false
    private var fullSidebar = false
    val sidebar = FrameLayout(context).apply { id = View.generateViewId() }
    private val sideContent = LinearLayout(context).apply { orientation = VERTICAL }
    private val segmentRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val undo = icon("cmd_undo", R.string.undo)
    private val cancel = icon("cmd_close", R.string.t9_cancel)
    private val unlock = icon("cmd_lock_open_variant_outline", R.string.t9_unlock)
    private val undoAction = VersionedClickBinding<Int>(undo, R.string.undo)
    private val cancelAction = VersionedClickBinding<Int>(cancel, R.string.t9_cancel)
    private val unlockAction = VersionedClickBinding<Int>(unlock, R.string.t9_unlock)

    // Two lines are used for fuzzy/source annotations. Keep the user's SP scaling;
    // shrinking the text to fit a fixed 48dp row defeats the accessibility setting.
    private val rowHeight by lazy {
        val bounds = label().paint.fontMetricsInt
        maxOf(dp(48), 2 * (bounds.bottom - bounds.top) + dp(4))
    }
    private val singleRowHeight by lazy {
        val bounds = label().paint.fontMetricsInt
        maxOf(dp(48), bounds.bottom - bounds.top + dp(4))
    }
    val managesComposition get() = layoutActive || compositionPanelActive || state.enabled
    val maximumTopControlsHeight get() = if (fullSidebar) 0 else rowHeight * if (sideMode) 1 else 2
    val topControlsHeight: Int
        get() = if (!managesComposition || fullSidebar) {
            0
        } else {
            (if (segmentRow.isVisible) segmentRow.layoutParams.height else 0) +
                (if (!sideMode && choices.isVisible) choices.layoutParams.height else 0)
        }
    private val choices = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        adapter = this@T9DisambiguationView.adapter
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
    }

    init {
        id = View.generateViewId()
        isVisible = false
        background = scope.drawable("candidate_background")
        sidebar.background = scope.drawable("candidate_background")
        orientation = VERTICAL
        actions.addView(unlock, LayoutParams(dp(48), dp(48)))
        actions.addView(undo, LayoutParams(dp(48), dp(48)))
        actions.addView(cancel, LayoutParams(dp(48), dp(48)))
        segmentRow.addView(segmentScroller, LayoutParams(0, singleRowHeight, 1f))
        segmentRow.addView(actions, LayoutParams(dp(144), dp(48)))
        addView(segmentRow, LayoutParams(LayoutParams.MATCH_PARENT, singleRowHeight))
        addView(choices, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
        sidebar.addView(sideContent, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        updateVisibility()
    }

    fun setKeyboardLayout(active: Boolean, side: Boolean, compactSidebar: Boolean = false, compositionPanel: Boolean = active) {
        layoutActive = active
        compositionPanelActive = compositionPanel
        if (sideMode == side && fullSidebar == compactSidebar) {
            updateVisibility()
            return
        }
        sideMode = side
        fullSidebar = side && compactSidebar
        (segmentScroller.parent as? ViewGroup)?.removeView(segmentScroller)
        (actions.parent as ViewGroup).removeView(actions)
        (choices.parent as ViewGroup).removeView(choices)
        sideContent.removeAllViews()
        (choices.layoutManager as LinearLayoutManager).orientation = if (side) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL
        if (fullSidebar) {
            sideContent.addView(choices, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            sideContent.addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        } else {
            segmentRow.addView(segmentScroller, 0, LayoutParams(0, singleRowHeight, 1f))
            segmentRow.addView(actions, LayoutParams(dp(144), dp(48)))
            if (side) {
                sideContent.addView(choices, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            } else {
                addView(choices, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
            }
        }
        // Recreate holders because their fixed axis changes with the list orientation.
        choices.adapter = null
        choices.recycledViewPool.clear()
        choices.adapter = adapter
        update(state)
    }

    private fun updateVisibility() {
        val segmentHeight = if (state.segments.any { it.sources != 0 }) rowHeight else singleRowHeight
        segmentRow.layoutParams?.let {
            if (it.height != segmentHeight) {
                it.height = segmentHeight
                segmentRow.layoutParams = it
            }
        }
        segmentScroller.layoutParams?.let {
            if (it.height != segmentHeight) {
                it.height = segmentHeight
                segmentScroller.layoutParams = it
            }
        }
        if (!sideMode) {
            val height = if (state.choices.any { it.sources != 0 }) rowHeight else singleRowHeight
            choices.layoutParams?.let {
                if (it.height != height) {
                    it.height = height
                    choices.layoutParams = it
                }
            }
        }
        val hasState = state.input.isNotEmpty() || state.segments.isNotEmpty() || state.choices.isNotEmpty() || state.canUndo
        segmentRow.isVisible = !fullSidebar && managesComposition && hasState
        segmentScroller.isVisible = state.segments.isNotEmpty()
        actions.isVisible = managesComposition && hasState
        choices.isVisible = managesComposition && (state.choices.isNotEmpty() || (fullSidebar && state.segments.isNotEmpty()))
        // Keep the reserved column through empty/input transitions. GONE would
        // widen the keyboard and rebuild held keys when the first result arrives.
        sidebar.visibility = if (!sideMode) {
            GONE
        } else if (managesComposition && hasState) {
            VISIBLE
        } else {
            INVISIBLE
        }
        isVisible = !fullSidebar && managesComposition && hasState
    }

    fun refreshColors() {
        background = scope.drawable("candidate_background")
        sidebar.background = scope.drawable("candidate_background")
        val tint = ColorStateList.valueOf(normalTextColor)
        undo.imageTintList = tint
        cancel.imageTintList = tint
        unlock.imageTintList = tint
        update(state)
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    private fun icon(name: String, label: Int) = BoundActionImageButton(context).apply {
        setImageDrawable(IconicsDrawable(context, name).apply { sizeDp = 22 })
        imageTintList = ColorStateList.valueOf(normalTextColor)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        contentDescription = context.getString(label)
        TooltipCompat.setTooltipText(this, contentDescription)
    }

    private fun label() = BoundActionTextView(labelContext).apply {
        gravity = Gravity.CENTER
        textSize = 17f
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        minWidth = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(normalTextColor)
    }

    private fun sourceLabel(sources: Int): String {
        val labels = listOf(
            "n/l", "z/zh", "c/ch", "s/sh", "en/eng", "in/ing",
            context.getString(R.string.t9_source_adjacent),
            context.getString(R.string.t9_source_missing),
            context.getString(R.string.t9_source_repeat),
        )
        return labels.filterIndexed { index, _ -> sources and (1 shl index) != 0 }.joinToString(", ")
    }

    private fun showSpelling(view: TextView, span: T9SpanProto, normal: String) {
        view.setSingleLine(span.sources == 0)
        view.maxLines = if (span.sources == 0) 1 else 2
        if (span.sources == 0) {
            view.text = normal
            view.contentDescription = normal
        } else {
            val source = sourceLabel(span.sources)
            val text = SpannableStringBuilder(span.spelling).append('\n')
            val start = text.length
            text.append(source)
            text.setSpan(RelativeSizeSpan(0.65f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            view.text = text
            val description = if (span.sources and 63 != 0) context.getString(R.string.t9_source_fuzzy, source) else source
            view.contentDescription = context.getString(R.string.t9_suggestion_source, span.spelling, description)
        }
        TooltipCompat.setTooltipText(view, view.contentDescription)
    }

    fun update(data: T9StateProto) {
        val focusChanged = data.focus != state.focus || data.input != state.input
        state = data
        segments.removeAllViews()
        data.segments.forEach { span ->
            segments.addView(
                label().apply {
                    showSpelling(this, span, span.spelling)
                    val selected = span.start == data.focus
                    isSelected = selected
                    setTextColor(if (selected) selectedForeground else normalTextColor)
                    setBackgroundColor(if (selected) selectedBackground else android.graphics.Color.TRANSPARENT)
                    if (span.locked) {
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                            IconicsDrawable(context, "cmd_lock_outline").apply { sizeDp = 14 },
                            null,
                            null,
                            null,
                        )
                        compoundDrawablePadding = dp(4)
                        TextViewCompat.setCompoundDrawableTintList(
                            this,
                            ColorStateList.valueOf(if (selected) selectedForeground else normalTextColor),
                        )
                    }
                    contentDescription = context.getString(
                        if (span.locked) R.string.t9_locked_syllable else R.string.t9_input_segment,
                        contentDescription,
                    )
                    if (selected) contentDescription = context.getString(R.string.t9_current_segment, contentDescription)
                    VersionedClickBinding<Int>(this, R.string.a11y_t9_focus_segment).bind(data.revision) {
                        action(data.revision, T9Action.Focus, span.start, 0, "")
                    }
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
            )
        }
        unlock.isEnabled = data.segments.any { it.start == data.focus && it.locked }
        undo.isEnabled = data.canUndo
        cancel.isEnabled = data.input.isNotEmpty()
        unlockAction.bind(data.revision.takeIf { unlock.isEnabled }) { action(data.revision, T9Action.Unlock, data.focus, 0, "") }
        undoAction.bind(data.revision.takeIf { undo.isEnabled }) { action(data.revision, T9Action.Undo, 0, 0, "") }
        cancelAction.bind(data.revision.takeIf { cancel.isEnabled }) { action(data.revision, T9Action.Cancel, 0, 0, "") }
        unlock.alpha = if (unlock.isEnabled) 1f else 0.35f
        undo.alpha = if (undo.isEnabled) 1f else 0.35f
        cancel.alpha = if (cancel.isEnabled) 1f else 0.35f
        adapter.update(data.choices, data.revision)
        updateVisibility()
        if (focusChanged) {
            choices.scrollToPosition(if (fullSidebar && data.segments.isNotEmpty() && data.choices.isNotEmpty()) 1 else 0)
            val selectedIndex = data.segments.indexOfFirst { it.start == data.focus }
            segments.getChildAt(selectedIndex)?.takeUnless { fullSidebar }?.let { child ->
                child.post {
                    child.requestRectangleOnScreen(Rect(0, 0, child.width, child.height), true)
                }
            }
        }
    }

    private inner class ChoiceAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items = emptyArray<T9SpanProto>()
        private var revision = -1
        private var header = false
        private var boundSegments = emptyArray<T9SpanProto>()
        private var boundFocus = 0

        fun update(value: Array<T9SpanProto>, newRevision: Int) {
            val nextHeader = fullSidebar && state.segments.isNotEmpty()
            val nextSegments = state.segments
            val nextFocus = state.focus
            if (items.contentEquals(value) && revision == newRevision && header == nextHeader &&
                boundSegments.contentEquals(nextSegments) && boundFocus == nextFocus
            ) {
                return
            }
            val previous = items
            val previousRevision = revision
            val previousSegments = boundSegments
            val previousFocus = boundFocus
            val oldOffset = if (header) 1 else 0
            val newOffset = if (nextHeader) 1 else 0
            val difference = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = previous.size + oldOffset
                override fun getNewListSize() = value.size + newOffset
                override fun areItemsTheSame(old: Int, new: Int): Boolean {
                    val oldHeader = oldOffset == 1 && old == 0
                    val newHeader = newOffset == 1 && new == 0
                    if (oldHeader || newHeader) return oldHeader && newHeader
                    return previous[old - oldOffset] == value[new - newOffset]
                }

                override fun areContentsTheSame(old: Int, new: Int): Boolean {
                    if (oldOffset == 1 && old == 0) {
                        return previousRevision == newRevision && previousFocus == nextFocus && previousSegments.contentEquals(nextSegments)
                    }
                    val oldSpan = previous[old - oldOffset]
                    val newSpan = value[new - newOffset]
                    return previousRevision == newRevision && oldSpan == newSpan &&
                        isSelected(oldSpan, previousSegments) == isSelected(newSpan, nextSegments)
                }
            })
            header = nextHeader
            items = value.copyOf()
            revision = newRevision
            boundSegments = nextSegments.copyOf()
            boundFocus = nextFocus
            difference.dispatchUpdatesTo(this)
        }

        private fun isSelected(span: T9SpanProto, segments: Array<T9SpanProto>) = segments.any { it.locked && it.start == span.start && it.end == span.end && it.spelling == span.spelling }

        override fun getItemCount() = items.size + if (header) 1 else 0
        override fun getItemViewType(position: Int) = if (header && position == 0) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == 1) return SegmentHolder(FrameLayout(context))
            return ChoiceHolder(
                label().apply {
                    layoutParams = RecyclerView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                    if (sideMode) {
                        layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight)
                        setPadding(dp(4), 0, dp(4), 0)
                    }
                },
            )
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is SegmentHolder) {
                val height = if (boundSegments.any { it.sources != 0 }) rowHeight else singleRowHeight
                holder.container.layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, height)
                (segmentScroller.parent as? ViewGroup)?.removeView(segmentScroller)
                holder.container.addView(segmentScroller, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                return
            }
            holder as ChoiceHolder
            val span = items[position - if (header) 1 else 0]
            if (sideMode) {
                holder.text.layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, if (span.sources != 0) rowHeight else singleRowHeight)
            }
            val boundRevision = revision
            holder.text.setTextColor(normalTextColor)
            val description = if (span.completion) context.getString(R.string.t9_completion, span.spelling) else span.spelling
            showSpelling(holder.text, span, if (sideMode && span.completion) "${span.spelling}+" else description)
            if (span.sources == 0) holder.text.contentDescription = description
            holder.text.isSelected = isSelected(span, boundSegments)
            holder.binding.bind(boundRevision to span) { action(boundRevision, T9Action.Lock, span.start, span.end, span.spelling) }
        }
    }

    private class ChoiceHolder(val text: TextView) : RecyclerView.ViewHolder(text) {
        val binding = VersionedClickBinding<Pair<Int, T9SpanProto>>(text, R.string.a11y_t9_choose_syllable)
    }
    private class SegmentHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}
