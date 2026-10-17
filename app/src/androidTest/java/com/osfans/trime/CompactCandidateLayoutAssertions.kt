/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderNode
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.data.theme.ColorKey
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.CandidateIdentity
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateLayoutManagers
import com.osfans.trime.ime.candidates.compact.CompactCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxVerticalDecoration

/** Actual production adapter/views/managers; the caller verifies the resulting native commit. */
object CompactCandidateLayoutAssertions {
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun recordLayout(parent: FrameLayout, width: Int, height: Int) {
        // This fixture checks layout and dispatch, not pixels. Patterned ripples require
        // a hardware canvas even when a completed gesture is first drawn offscreen.
        val node = RenderNode("owned-compact-layout")
        try {
            val canvas = node.beginRecording(width, height)
            try {
                check(canvas.isHardwareAccelerated)
                parent.draw(canvas)
            } finally {
                node.endRecording()
            }
        } finally {
            try {
                // draw() can consume a pending ripple entry left by the completed gesture.
                parent.jumpDrawablesToCurrentState()
            } finally {
                node.discardDisplayList()
            }
        }
    }

    data class Outcome(
        val checks: Int,
        val selected: CandidateIdentity,
        val expectedText: String,
        val legacyRelayoutResetObserved: Boolean,
        val scrollDiagnostics: String,
    )

    fun verify(instrumentation: Instrumentation, candidates: Array<CandidateProto>, snapshot: CandidateSnapshot): Outcome {
        check(candidates.size in 8..144 && snapshot.isValid)
        var outcome: Result<Outcome>? = null
        instrumentation.runOnMainSync {
            outcome = runCatching {
                val context = instrumentation.targetContext
                val scope = ThemeScope(Theme("owned-compact-layout")).apply {
                    updateScheme(ColorKey.entries.associate { it.key to "#ff334455" })
                }
                val width = (context.resources.displayMetrics.density * 320).toInt()
                val height = (context.resources.displayMetrics.density * 40).toInt()
                val pixels = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) else null
                val canvas = pixels?.let { Canvas(it) }
                val fixtures = mutableListOf<RecyclerView>()
                var checks = 0
                val clicks = mutableListOf<CandidateIdentity>()

                fun layout(view: RecyclerView) {
                    val parent = view.parent as FrameLayout
                    view.requestLayout()
                    parent.requestLayout()
                    parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    parent.layout(0, 0, width, height)
                    // Every gesture has ended before this offscreen draw.
                    parent.jumpDrawablesToCurrentState()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        recordLayout(parent, width, height)
                    } else {
                        parent.draw(checkNotNull(canvas))
                    }
                    check(view.childCount > 0)
                }

                fun fixture(manager: RecyclerView.LayoutManager, horizontal: Boolean): RecyclerView {
                    val adapter = CompactCandidateViewAdapter(scope).apply {
                        horizontalScroll = horizontal
                        layoutFlexShrink = 0f
                        updateLayoutParams(0, 0f)
                    }
                    val view = RecyclerView(context).apply {
                        itemAnimator = null
                        layoutManager = manager
                        this.adapter = adapter
                        addItemDecoration(FlexboxVerticalDecoration(ShapeDrawable(RectShape()).apply { intrinsicWidth = 2 }))
                    }
                    adapter.setOnItemClickListener { _, row, _ ->
                        val holder = checkNotNull(view.findContainingViewHolder(row) as? CandidateViewHolder)
                        clicks.add(CandidateIdentity(holder.snapshot, holder.idx))
                    }
                    adapter.updateCandidates(candidates, 200, 0, snapshot)
                    FrameLayout(context).addView(view, FrameLayout.LayoutParams(width, height))
                    fixtures.add(view)
                    layout(view)
                    return view
                }

                fun event(view: RecyclerView, down: Long, action: Int, x: Float, y: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        check(view.dispatchTouchEvent(event))
                    } finally {
                        event.recycle()
                    }
                }

                fun swipe(view: RecyclerView) {
                    val down = SystemClock.uptimeMillis()
                    val y = height / 2f
                    event(view, down, MotionEvent.ACTION_DOWN, width * 0.85f, y)
                    for (step in 1..6) event(view, down, MotionEvent.ACTION_MOVE, width * (0.85f - step * 0.1f), y)
                    event(view, down, MotionEvent.ACTION_UP, width * 0.25f, y)
                    view.stopScroll()
                }

                fun anchor(view: RecyclerView): Pair<Int, Int> {
                    val manager = checkNotNull(view.layoutManager)
                    val index = CompactCandidateLayoutManagers.firstVisibleIndex(manager)
                    val child = checkNotNull(manager.findViewByPosition(index))
                    return index to (child.left - view.paddingLeft)
                }

                try {
                    // Same dependency and item views as the removed NOWRAP mode; record the
                    // negative control separately, without claiming it resets on every OS.
                    val legacy = fixture(FlexboxLayoutManager(context, FlexDirection.ROW, FlexWrap.NOWRAP), false)
                    swipe(legacy)
                    val oldAnchor = anchor(legacy)
                    repeat(2) { layout(legacy) }
                    val legacyReset = anchor(legacy) != oldAnchor
                    check(clicks.isEmpty()) { "Legacy negative-control swipe selected a word" }

                    val manager = CompactCandidateLayoutManagers.create(context, true)
                    check(manager is LinearLayoutManager && manager.orientation == LinearLayoutManager.HORIZONTAL)
                    val view = fixture(manager, true)
                    val initial = anchor(view)
                    check(initial.first == 0) { "Horizontal fixture did not start at the first candidate: $initial" }
                    val firstItemWidth = manager.getDecoratedMeasuredWidth(checkNotNull(manager.findViewByPosition(0)))
                    val anchors = mutableListOf(initial)
                    var scrolled = initial
                    var swipes = 0
                    fun scrollDiagnostics() = "width=$width height=$height firstItemWidth=$firstItemWidth anchors=$anchors swipes=$swipes density=${context.resources.displayMetrics.density} fontScale=${context.resources.configuration.fontScale} touchSource=${InputDevice.SOURCE_TOUCHSCREEN} clicks=$clicks"
                    // A large candidate may need more than the fixed 192dp gesture to leave
                    // the viewport. Every real swipe must move forward without selecting;
                    // the original nonzero-index and later relayout/commit checks still apply.
                    while (scrolled.first == 0 && swipes < 8) {
                        val beforeSwipe = scrolled
                        swipe(view)
                        swipes++
                        scrolled = anchor(view)
                        anchors.add(scrolled)
                        check(
                            (
                                scrolled.first > beforeSwipe.first ||
                                    (scrolled.first == beforeSwipe.first && scrolled.second < beforeSwipe.second)
                                ) &&
                                clicks.isEmpty(),
                        ) { "Actual horizontal gesture did not move forward safely: ${scrollDiagnostics()}" }
                    }
                    check(scrolled.first > 0 && clicks.isEmpty()) { "Actual horizontal view did not scroll safely: ${scrollDiagnostics()}" }
                    checks++
                    repeat(2) {
                        layout(view)
                        check(anchor(view) == scrolled) { "Ordinary relayout changed the visible candidate index/offset" }
                        checks++
                    }
                    val child = checkNotNull(manager.findViewByPosition(scrolled.first))
                    val holder = view.getChildViewHolder(child) as CandidateViewHolder
                    val expected = CandidateIdentity(holder.snapshot, holder.idx)
                    check(expected == CandidateIdentity(snapshot, scrolled.first) && holder.text == candidates[expected.index].text)
                    val x = (maxOf(0, child.left) + minOf(width, child.right)) / 2f
                    val down = SystemClock.uptimeMillis()
                    event(view, down, MotionEvent.ACTION_DOWN, x, height / 2f)
                    event(view, down, MotionEvent.ACTION_UP, x, height / 2f)
                    check(clicks == listOf(expected)) { "Actual scrolled row dispatched a wrong or duplicate native index" }
                    checks++

                    // Reuse holders through both mode changes; draw exercises the shared divider
                    // with ordinary RecyclerView params and then wrapped Flexbox params.
                    val adapter = view.adapter as CompactCandidateViewAdapter
                    adapter.horizontalScroll = false
                    adapter.layoutFlexShrink = 1f
                    view.layoutManager = CompactCandidateLayoutManagers.create(context, false)
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    layout(view)
                    check(!view.canScrollHorizontally(1) && !view.canScrollHorizontally(-1))
                    val wrapped = anchor(view)
                    swipe(view)
                    layout(view)
                    check(anchor(view) == wrapped && clicks == listOf(expected)) { "Default wrapped mode scrolled or selected during a swipe" }
                    check((0 until view.childCount).all { view.getChildAt(it).layoutParams is FlexboxLayoutManager.LayoutParams })
                    checks++
                    adapter.horizontalScroll = true
                    adapter.layoutFlexShrink = 0f
                    view.layoutManager = CompactCandidateLayoutManagers.create(context, true)
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    layout(view)
                    check((0 until view.childCount).all { view.getChildAt(it).layoutParams is RecyclerView.LayoutParams && view.getChildAt(it).layoutParams !is FlexboxLayoutManager.LayoutParams })
                    checks++
                    Outcome(checks, expected, candidates[expected.index].text, legacyReset, scrollDiagnostics())
                } finally {
                    fixtures.forEach {
                        it.stopScroll()
                        it.adapter = null
                        it.layoutManager = null
                        (it.parent as FrameLayout).removeView(it)
                    }
                    pixels?.recycle()
                }
            }
        }
        return checkNotNull(outcome).getOrThrow()
    }
}
