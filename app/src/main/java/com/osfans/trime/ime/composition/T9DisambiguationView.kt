/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.R
import com.osfans.trime.core.T9Action
import com.osfans.trime.core.T9SpanProto
import com.osfans.trime.core.T9StateProto
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp

/** Pinyin controls are separate from the existing Hanzi candidate bar. */
@SuppressLint("ViewConstructor")
class T9DisambiguationView(
    context: Context,
    private val scope: ThemeScope,
    private val action: (Int, T9Action, Int, Int, String) -> Unit,
) : LinearLayout(context) {
    private var state = T9StateProto()
    private val labelContext = ContextThemeWrapper(context, R.style.Theme_TrimeAppTheme)
    private val segments = LinearLayout(context)
    private val segmentScroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(segments, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val choicesAdapter = ChoiceAdapter()
    private val choices = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        adapter = choicesAdapter
        itemAnimator = null
    }
    private val unlock = icon("cmd_lock_open_variant_outline", R.string.t9_unlock)
    private val undo = icon("cmd_undo", R.string.undo)
    private val cancel = icon("cmd_close", R.string.t9_cancel)
    private val unlockBinding = bindView<Int>(unlock)
    private val undoBinding = bindView<Int>(undo)
    private val cancelBinding = bindView<Int>(cancel)
    private val candidateColor get() = scope.colors.candidateTextColor
    private val selectedForeground get() = scope.colors.hilitedCandidateTextColor
    private val selectedBackground get() = scope.colors.hilitedCandidateBackColor
    val managesComposition: Boolean get() = state.enabled

    init {
        id = View.generateViewId()
        orientation = VERTICAL
        isVisible = false
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(segmentScroller, LayoutParams(0, dp(48), 1f))
            addView(unlock, LayoutParams(dp(48), dp(48)))
            addView(undo, LayoutParams(dp(48), dp(48)))
            addView(cancel, LayoutParams(dp(48), dp(48)))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(choices, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        refreshColors()
    }

    private fun icon(name: String, label: Int) = ImageButton(context).apply {
        setImageDrawable(IconicsDrawable(context, name).apply { sizeDp = 22 })
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        contentDescription = context.getString(label)
        TooltipCompat.setTooltipText(this, contentDescription)
    }

    private fun label() = TextView(labelContext).apply {
        gravity = Gravity.CENTER
        textSize = 17f
        setSingleLine()
        minWidth = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
        isFocusable = true
        setTextColor(candidateColor)
    }

    /** A posted View click must still belong to the DOWN/UP revision after recycling. */
    @SuppressLint("ClickableViewAccessibility")
    private fun <T : Any> bindView(view: View): T9ActionBinding<T> {
        val binding = T9ActionBinding<T>()
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (!binding.press()) return@setOnTouchListener true

                MotionEvent.ACTION_CANCEL -> binding.cancel()

                MotionEvent.ACTION_UP -> {
                    val token = binding.release()
                    if (token == null) {
                        val cancelled = MotionEvent.obtain(event)
                        try {
                            cancelled.action = MotionEvent.ACTION_CANCEL
                            view.onTouchEvent(cancelled)
                        } finally {
                            cancelled.recycle()
                        }
                        return@setOnTouchListener true
                    }
                    view.post { view.post { binding.clearPending(token) } }
                }
            }
            false
        }
        view.setOnClickListener { if (view.isEnabled) binding.click() }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = binding.detach()
        })
        return binding
    }

    fun refreshColors() {
        background = scope.drawable("candidate_background")
        val tint = ColorStateList.valueOf(candidateColor)
        unlock.imageTintList = tint
        undo.imageTintList = tint
        cancel.imageTintList = tint
        update(state)
    }

    fun update(data: T9StateProto) {
        val focusChanged = data.focus != state.focus || data.input != state.input
        state = data
        segments.removeAllViews()
        data.segments.forEach { span ->
            val selected = span.start == data.focus
            val view = label().apply {
                text = span.spelling
                isSelected = selected
                setTextColor(if (selected) selectedForeground else candidateColor)
                setBackgroundColor(if (selected) selectedBackground else android.graphics.Color.TRANSPARENT)
                contentDescription = context.getString(
                    if (span.locked) R.string.t9_locked_syllable else R.string.t9_input_segment,
                    span.spelling,
                )
                if (selected) contentDescription = context.getString(R.string.t9_current_segment, contentDescription)
            }
            bindView<Int>(view).bind(data.revision) {
                action(data.revision, T9Action.Focus, span.start, 0, "")
            }
            segments.addView(view, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        unlock.isEnabled = data.segments.any { it.start == data.focus && it.locked }
        undo.isEnabled = data.canUndo
        cancel.isEnabled = data.input.isNotEmpty()
        unlockBinding.bind(data.revision.takeIf { unlock.isEnabled }) { action(data.revision, T9Action.Unlock, data.focus, 0, "") }
        undoBinding.bind(data.revision.takeIf { undo.isEnabled }) { action(data.revision, T9Action.Undo, 0, 0, "") }
        cancelBinding.bind(data.revision.takeIf { cancel.isEnabled }) { action(data.revision, T9Action.Cancel, 0, 0, "") }
        unlock.alpha = if (unlock.isEnabled) 1f else 0.35f
        undo.alpha = if (undo.isEnabled) 1f else 0.35f
        cancel.alpha = if (cancel.isEnabled) 1f else 0.35f
        segmentScroller.isVisible = data.segments.isNotEmpty()
        choices.isVisible = data.choices.isNotEmpty()
        choicesAdapter.update(data.choices, data.revision)
        isVisible = data.enabled && (data.input.isNotEmpty() || data.segments.isNotEmpty() || data.choices.isNotEmpty() || data.canUndo)
        if (focusChanged) choices.scrollToPosition(0)
    }

    private inner class ChoiceAdapter : RecyclerView.Adapter<ChoiceHolder>() {
        private var items = emptyArray<T9SpanProto>()
        private var revision = 0
        fun update(value: Array<T9SpanProto>, nextRevision: Int) {
            items = value.copyOf()
            revision = nextRevision
            notifyDataSetChanged()
        }
        override fun getItemCount(): Int = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChoiceHolder = ChoiceHolder(
            label().apply {
                layoutParams = RecyclerView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            },
        )
        override fun onBindViewHolder(holder: ChoiceHolder, position: Int) {
            val span = items[position]
            val boundRevision = revision
            holder.view.text = if (span.completion) context.getString(R.string.t9_completion, span.spelling) else span.spelling
            holder.view.contentDescription = holder.view.text
            holder.view.isSelected = state.segments.any { it.locked && it.start == span.start && it.end == span.end && it.spelling == span.spelling }
            holder.view.setTextColor(if (holder.view.isSelected) selectedForeground else candidateColor)
            holder.view.setBackgroundColor(if (holder.view.isSelected) selectedBackground else android.graphics.Color.TRANSPARENT)
            holder.binding.bind(boundRevision to span) { action(boundRevision, T9Action.Lock, span.start, span.end, span.spelling) }
        }
    }

    private inner class ChoiceHolder(val view: TextView) : RecyclerView.ViewHolder(view) {
        val binding = bindView<Pair<Int, T9SpanProto>>(view)
    }
}
