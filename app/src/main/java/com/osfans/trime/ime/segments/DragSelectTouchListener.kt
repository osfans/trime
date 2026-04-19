/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class DragSelectTouchListener(
    private val adapter: SegmentsAdapter,
    private val onSelectionChanged: () -> Unit,
) : RecyclerView.OnItemTouchListener {

    private var isDragSelecting = false
    private var startPosition = -1
    private var endPosition = -1
    private var lastEndPosition = -1
    private var targetState = false

    private var selectionSnapshot: Set<Int> = emptySet()

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        val child = rv.findChildViewUnder(e.x, e.y)
        val position = child?.let { rv.getChildAdapterPosition(it) } ?: -1

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (position != -1) {
                    isDragSelecting = true
                    startPosition = position
                    endPosition = position

                    val snapshot = adapter.selectionSnapshot
                    val state = !snapshot.contains(position)
                    selectionSnapshot = snapshot
                    targetState = state
                    updateRange(rv, startPosition, startPosition)
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!isDragSelecting) return

        val child = rv.findChildViewUnder(e.x, e.y)
        val position = child?.let { rv.getChildAdapterPosition(it) } ?: -1

        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (position != -1 && position != lastEndPosition) {
                    updateRange(rv, startPosition, position)
                    lastEndPosition = position
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragSelecting = false
                startPosition = -1
                endPosition = -1
                lastEndPosition = -1
                rv.parent?.requestDisallowInterceptTouchEvent(false)
                onSelectionChanged.invoke()
            }
        }
    }

    private fun updateRange(rv: RecyclerView, start: Int, end: Int) {
        val minNew = min(start, end)
        val maxNew = max(start, end)
        val minOld = min(start, lastEndPosition)
        val maxOld = max(start, lastEndPosition)

        val minEval = min(minNew, minOld)
        val maxEval = max(maxNew, maxOld)

        for (i in minEval..maxEval) {
            val isInsideNewRange = i in minNew..maxNew

            val expectedState = if (isInsideNewRange) {
                targetState
            } else {
                selectionSnapshot.contains(i)
            }

            if (adapter.isSegmentSelected(i) != expectedState) {
                adapter.setSegmentSelected(i, expectedState)
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}
