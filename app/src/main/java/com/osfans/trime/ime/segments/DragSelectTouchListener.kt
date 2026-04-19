/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DragSelectTouchListener(
    context: Context,
    private val adapter: SegmentsAdapter,
    private val onSelectionChanged: () -> Unit,
) : RecyclerView.OnItemTouchListener {

    private var isDragSelecting = false
    private var startPosition = -1
    private var lastEndPosition = -1
    private var targetState = false

    private var selectionSnapshot: Set<Int> = emptySet()

    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        val child = rv.findChildViewUnder(e.x, e.y)
        val position = child?.let { rv.getChildAdapterPosition(it) } ?: -1

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (position != -1) {
                    initialX = e.x
                    initialY = e.y
                    startPosition = position
                    return false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (startPosition == -1) return false

                if (!isDragSelecting) {
                    val dx = abs(e.x - initialX)
                    val dy = abs(e.y - initialY)

                    if (dx > touchSlop || dy > touchSlop) {
                        if (dx > dy) { // core condition: x offset larger than y offset
                            isDragSelecting = true

                            // disable scrolling
                            rv.parent?.requestDisallowInterceptTouchEvent(true)

                            // get the snapshot and judge the target state
                            selectionSnapshot = adapter.selectionSnapshot
                            targetState = !selectionSnapshot.contains(startPosition)

                            // update state at start position immediately
                            updateRange(rv, startPosition, startPosition)

                            return true
                        } else {
                            // make scrolling still work
                            startPosition = -1
                            return false
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                startPosition = -1
                isDragSelecting = false
            }
        }
        return isDragSelecting
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
