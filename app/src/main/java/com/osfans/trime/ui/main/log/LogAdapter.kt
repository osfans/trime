/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.log

import android.graphics.Typeface
import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.resources.styledColor

/**
 * The recycler view adapter to contain the log text view.
 *
 * This file is adapted from fcitx5-android project.
 * Source: [fcitx5-android/LogAdapter](https://github.com/fcitx5-android/fcitx5-android/blob/5ac719c3547165a3e77fe265c471a5a211580320/app/src/main/java/org/fcitx/fcitx5/android/ui/main/log/LogAdapter.kt)
 */
class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {
    // Both buffers have the same bounded window. Pending records are committed
    // with their notifications in one frame, never mutating RecyclerView's data
    // before the matching update. Total text retention is at most two windows.
    private val entries = LogBuffer()
    private val pending = LogBuffer()
    private var recyclerView: RecyclerView? = null
    private var updateScheduled = false
    private val update = Runnable { flushPending() }

    init {
        setHasStableIds(true)
    }

    inner class ViewHolder(
        val textView: TextView,
    ) : RecyclerView.ViewHolder(textView) {
        val foregroundColor = textView.styledColor(android.R.attr.colorForeground)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        scheduleUpdate()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        flushPending()
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /** Queue an unstyled record; only visible TextViews receive a color. */
    @MainThread
    fun append(
        line: String,
        isLogcat: Boolean = false,
        priority: Char? = null,
    ) {
        pending.append(line, isLogcat, priority)
        scheduleUpdate()
    }

    private fun scheduleUpdate() {
        val view = recyclerView ?: return
        if (pending.size == 0 || updateScheduled) return
        updateScheduled = true
        view.postOnAnimation(update)
    }

    /** Commit one bounded batch before layout, scrolling, copying or exporting. */
    @MainThread
    fun flushPending() {
        recyclerView?.removeCallbacks(update)
        updateScheduled = false
        if (pending.size == 0) return
        val oldSize = entries.size
        val oldHeader = entries.hasOmissions
        val firstId = if (oldSize == 0) 0L else entries[0].id
        pending.drainTo(entries)
        val newHeader = entries.hasOmissions
        val removed = if (oldSize == 0) 0 else (entries[0].id - firstId).coerceAtMost(oldSize.toLong()).toInt()
        val remaining = oldSize - removed
        if (removed != 0) notifyItemRangeRemoved(if (oldHeader) 1 else 0, removed)
        if (!oldHeader && newHeader) notifyItemInserted(0)
        val added = entries.size - remaining
        if (added != 0) notifyItemRangeInserted((if (newHeader) 1 else 0) + remaining, added)
    }

    /** Clear both the displayed window and records waiting for the next frame. */
    @MainThread
    fun clear() {
        recyclerView?.removeCallbacks(update)
        updateScheduled = false
        pending.clear()
        val size = itemCount
        entries.clear()
        if (size != 0) notifyItemRangeRemoved(0, size)
    }

    /** Snapshot the current retained suffix, including any omission notice. */
    @MainThread
    fun logString(omissionNotice: String): String {
        flushPending()
        return entries.snapshot(omissionNotice)
    }

    override fun getItemCount() = entries.size + if (entries.hasOmissions) 1 else 0

    override fun getItemId(position: Int): Long = if (entries.hasOmissions && position == 0) {
        Long.MIN_VALUE
    } else {
        entries[position - if (entries.hasOmissions) 1 else 0].id
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(
        TextView(parent.context).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
                setTextClassifier(TextClassifier.NO_OP)
            }
            layoutParams =
                MarginLayoutParams(MarginLayoutParams.WRAP_CONTENT, MarginLayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
        },
    )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        if (entries.hasOmissions && position == 0) {
            holder.textView.setTextColor(holder.foregroundColor)
            holder.textView.setText(R.string.log_content_omitted)
            return
        }
        val entry = entries[position - if (entries.hasOmissions) 1 else 0]
        holder.textView.setTextColor(
            if (entry.isLogcat) {
                ContextCompat.getColor(
                    holder.textView.context,
                    when (entry.priority) {
                        'V', 'D' -> R.color.grey_700
                        'I' -> R.color.blue_500
                        'W' -> R.color.yellow_800
                        'E' -> R.color.red_400
                        'F' -> R.color.red_A700
                        else -> R.color.colorPrimary
                    },
                )
            } else {
                holder.foregroundColor
            },
        )
        holder.textView.text = entry.text
    }
}
