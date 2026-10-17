/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.candidates.CandidateGestureFrame
import com.osfans.trime.ime.candidates.CandidateIdentity

/** Real production view dispatch assertions; does not claim a native tap or visual acceptance. */
object CandidateInteractionAssertions {
    fun verify(instrumentation: Instrumentation): Int {
        var checks = 0
        instrumentation.runOnMainSync {
            check(!AppPrefs.defaultInstance().keyboard.vibrateOnKeyPress.getValue()) { "Gesture fixture requires vibration disabled" }
            check(!AppPrefs.defaultInstance().keyboard.vibrateOnKeyRelease.getValue()) { "Gesture fixture requires release vibration disabled" }
            val view = CandidateGestureFrame(instrumentation.targetContext)
            val first = CandidateIdentity(CandidateSnapshot(17, 29, 31), 2)
            var clicks = 0
            var longClicks = 0
            view.setOnClickListener { clicks++ }

            fun touch(action: Int) {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, 20f, 20f, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    check(view.onTouchEvent(event))
                } finally {
                    event.recycle()
                }
            }

            view.identity = first
            touch(MotionEvent.ACTION_DOWN)
            touch(MotionEvent.ACTION_UP)
            check(clicks == 1) { "Unchanged candidate did not click exactly once" }
            checks++

            touch(MotionEvent.ACTION_DOWN)
            view.identity = first.copy(snapshot = first.snapshot.copy(generation = 30))
            touch(MotionEvent.ACTION_UP)
            check(clicks == 1) { "Rebinding a held candidate selected its replacement" }
            checks++

            touch(MotionEvent.ACTION_DOWN)
            view.identity = view.identity!!.copy(index = 3)
            touch(MotionEvent.ACTION_UP)
            check(clicks == 1) { "Moving to another native index selected its replacement" }
            checks++

            touch(MotionEvent.ACTION_DOWN)
            view.setOnLongClickListener {
                longClicks++
                true
            }
            check(view.performLongClick() && longClicks == 1)
            view.identity = view.identity!!.copy(snapshot = CandidateSnapshot(18, 31, 32))
            check(!view.performLongClick() && longClicks == 1) { "Stale long press opened another candidate's actions" }
            touch(MotionEvent.ACTION_UP)
            check(clicks == 1)
            view.setOnLongClickListener(null)
            checks++

            check(view.performClick() && clicks == 2) { "Accessibility click without a touch was rejected" }
            checks++

            touch(MotionEvent.ACTION_DOWN)
            touch(MotionEvent.ACTION_CANCEL)
            touch(MotionEvent.ACTION_UP)
            check(clicks == 2) { "Scrolling cancellation selected a candidate" }
            checks++

            view.identity = null
            check(!view.performClick() && !view.performLongClick()) { "An empty candidate holder accepted an action" }
            check(clicks == 2 && longClicks == 1)
            checks++
        }
        return checks
    }
}
