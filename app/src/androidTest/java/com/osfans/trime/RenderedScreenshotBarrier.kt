/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Binds an original screenshot to actual draws of an unchanged attached hierarchy. */
internal object RenderedScreenshotBarrier {
    fun capture(instrumentation: Instrumentation, root: View, verify: () -> Unit): Pair<Bitmap, JSONObject> {
        val started = SystemClock.uptimeMillis()
        val deadline = started + 10_000
        fun main(block: () -> Unit) {
            var failure: Throwable? = null
            instrumentation.runOnMainSync { runCatching(block).onFailure { failure = it } }
            failure?.let { throw it }
        }
        fun await(latch: CountDownLatch, label: String) {
            check(latch.await((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)) { "Screenshot $label timed out" }
        }
        var hardwareCommit = false
        main {
            check(root.isAttachedToWindow && root.isShown && root.viewTreeObserver.isAlive)
            verify()
            hardwareCommit = Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated
        }
        repeat(2) {
            val drawn = CountDownLatch(1)
            val committed = CountDownLatch(if (hardwareCommit) 1 else 0)
            val failure = AtomicReference<Throwable?>()
            var observer: ViewTreeObserver? = null
            val listener = ViewTreeObserver.OnDrawListener {
                runCatching(verify).onFailure { failure.compareAndSet(null, it) }
                drawn.countDown()
            }
            val callback = Runnable { committed.countDown() }
            try {
                main {
                    verify()
                    val current = root.viewTreeObserver
                    observer = current
                    current.addOnDrawListener(listener)
                    // ViewRootImpl captures these before onDraw. Register before invalidating.
                    if (Build.VERSION.SDK_INT >= 29 && hardwareCommit) current.registerFrameCommitCallback(callback)
                    root.invalidate()
                }
                await(drawn, "draw")
                await(committed, "frame submission")
                failure.get()?.let { throw it }
            } finally {
                // Never remove an OnDrawListener from inside onDraw itself.
                main {
                    observer?.takeIf { it.isAlive }?.let { current ->
                        current.removeOnDrawListener(listener)
                        if (Build.VERSION.SDK_INT >= 29 && hardwareCommit) current.unregisterFrameCommitCallback(callback)
                    }
                }
            }
        }
        // API21 has no commit callback. These are subsequent vsync callbacks, not a
        // claim that SurfaceFlinger has presented a particular buffer on either API.
        repeat(2) {
            val nextFrame = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val callback = Runnable {
                runCatching(verify).onFailure { failure.set(it) }
                nextFrame.countDown()
            }
            try {
                main { root.postOnAnimation(callback) }
                await(nextFrame, "subsequent vsync")
                failure.get()?.let { throw it }
            } finally {
                main { root.removeCallbacks(callback) }
            }
        }
        main(verify)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Missing original screenshot" }
        try {
            main(verify)
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
        return bitmap to JSONObject()
            .put("actual_draw_cycles", 2)
            .put("hardware_submission_callbacks", if (hardwareCommit) 2 else 0)
            .put("subsequent_vsync_callbacks", 2)
            .put("elapsed_ms", SystemClock.uptimeMillis() - started)
            .put("scope", "Unchanged hierarchy before and after original screenshot; draw/submission callbacks do not prove buffer presentation")
    }
}
