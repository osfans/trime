/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.KeyboardWindow
import org.json.JSONObject
import org.kodein.di.direct
import org.kodein.di.instance
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * No direct input/native calls; owned session changes may wake the lifecycle worker.
 * The blocked cache read below is test coordination, never UI work.
 */
object RimeCachedReadTeardownAssertions {
    data class CachedKeyboard(val model: Keyboard, val views: List<KeyView>, val windowRoot: View)

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    /** Retain actual production views before Setup's Activity/configuration cleanup. */
    fun capture(input: InputView): CachedKeyboard {
        check(Looper.myLooper() == Looper.getMainLooper())
        val keys = descendants(input).filterIsInstance<KeyView>().filter { it.width > 0 && it.height > 0 }.toList()
        check(keys.isNotEmpty()) { "No measured production KeyView to refresh after session teardown" }
        val windowRoot = input.rootView
        check(windowRoot.isAttachedToWindow && windowRoot.windowToken != null) { "Captured IME window must be attached" }
        return CachedKeyboard(input.di.direct.instance<KeyboardWindow>().currentKeyboard, keys, windowRoot)
    }

    @Suppress("DEPRECATION") // API 21 nodes are pooled; newer releases do not need recycling.
    private fun refresh(instrumentation: Instrumentation, keyboard: CachedKeyboard): JSONObject {
        var outcome: Result<JSONObject>? = null
        instrumentation.runOnMainSync {
            outcome = runCatching {
                keyboard.model.keys.filter { it.click != null }.forEach {
                    checkNotNull(it.getAction(KeyBehavior.CLICK))
                    it.getLabel()
                    it.getPreviewText(KeyBehavior.CLICK)
                    it.symbolLabel
                }
                // Explicit space/toggle/static paths, without changing any runtime option.
                val space = KeyAction(KeyActionToken.Plain("space"), emptyMap())
                space.getLabel(keyboard.model)
                space.getPreview(keyboard.model)
                val toggle = KeyAction(
                    KeyActionToken.Plain("cache_toggle"),
                    mapOf("cache_toggle" to PresetKey(toggle = "ascii_mode", states = listOf("cache-off", "cache-on"))),
                )
                check(toggle.getLabel(keyboard.model) in setOf("cache-off", "cache-on"))
                check(toggle.getPreview(keyboard.model) in setOf("cache-off", "cache-on"))
                val fixed = KeyAction(KeyActionToken.Inline(commit = "x", label = "cache-static"), emptyMap())
                check(fixed.getLabel(keyboard.model) == "cache-static")
                // Schema restoration can detach these cached keys. Their cached
                // presentation must still refresh safely after their owner closes.
                val retainedAttached = keyboard.views.count { it.isAttachedToWindow }
                keyboard.views.forEach { view ->
                    view.refreshAppearance()
                    view.visualContentLayout()
                }
                // API 21 requires AttachInfo when constructing a framework node.
                // Query the current keys in the same real IME window instead of
                // moving stale cached views or inventing an attachment for them.
                val window = keyboard.windowRoot
                check(window.isAttachedToWindow && window.windowToken != null) { "IME node window detached after Setup cleanup" }
                val nodeViews = descendants(window).filterIsInstance<KeyView>().toList()
                check(nodeViews.isNotEmpty()) { "No current production keys in the attached IME window; shown=${window.isShown}" }
                nodeViews.forEach { view ->
                    check(view.isAttachedToWindow && view.windowToken == window.windowToken) { "Node key must belong to the actual IME window" }
                    val node = view.createAccessibilityNodeInfo()
                    try {
                        check(node.className == "android.widget.Button")
                    } finally {
                        if (Build.VERSION.SDK_INT < 33) node.recycle()
                    }
                }
                JSONObject()
                    .put("retained_key_views", keyboard.views.size)
                    .put("retained_attached_views", retainedAttached)
                    .put("retained_detached_views", keyboard.views.size - retainedAttached)
                    .put("current_node_views", nodeViews.size)
                    .put("current_measured_node_views", nodeViews.count { it.width > 0 && it.height > 0 })
                    .put("current_shown_node_views", nodeViews.count { it.isShown })
                    .put("node_window_attached", window.isAttachedToWindow)
                    .put("node_window_shown", window.isShown)
            }
        }
        return checkNotNull(outcome).getOrThrow()
    }

    /** Run on the instrumentation thread, after the original Setup owner has been closed. */
    fun verify(instrumentation: Instrumentation, keyboard: CachedKeyboard, entryHadNoSession: Boolean): Bundle {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Teardown coordination must not block the main thread" }
        val prefix = "cache-teardown-${UUID.randomUUID()}"
        val keeperName = "$prefix-keeper"
        val closingName = "$prefix-closing"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closingAttempt = CountDownLatch(1)
        val workerFailure = AtomicReference<Throwable?>()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val threads = mutableListOf<Thread>()
        var failure: Throwable? = null
        var checks = 0
        val details = JSONObject().put("entry_no_session_observed", entryHadNoSession)
        fun verified(name: String) {
            checks++
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS cached read teardown $checks: $name\n") })
        }
        fun worker(name: String, block: () -> Unit): Thread = Thread({
            try {
                block()
            } catch (error: Throwable) {
                workerFailure.compareAndSet(null, error)
            }
        }, name).apply {
            isDaemon = true
            threads.add(this)
        }
        fun join(thread: Thread) {
            thread.join(5_000)
            check(!thread.isAlive) { "Teardown worker did not exit: ${thread.name}" }
        }
        try {
            val keeper = RimeDaemon.createSession(keeperName)
            val closing = RimeDaemon.createSession(closingName)
            check(RimeDaemon.readCached { statusCached.isAsciiMode } != null)
            closing.run { statusCached.isAsciiMode }
            verified("owned live sessions allow cached reads")
            val reader = worker("cache-teardown-reader") {
                check(
                    RimeDaemon.readCached {
                        val cached = statusCached.isAsciiMode
                        // Deliberately hold the real daemon lock for a bounded test handshake.
                        // Production readCached callers must only do short cache reads.
                        events.add("read-entered")
                        entered.countDown()
                        check(release.await(15, TimeUnit.SECONDS)) { "Cache read release was not signalled" }
                        events.add("read-released")
                        cached
                    } != null,
                )
            }
            val closer = worker("cache-teardown-closer") {
                events.add("close-attempt")
                closingAttempt.countDown()
                RimeDaemon.destroySession(closingName)
                events.add("close-completed")
            }
            reader.start()
            check(entered.await(5, TimeUnit.SECONDS)) { "Cache reader did not acquire the daemon lock" }
            val handshakeDeadline = SystemClock.uptimeMillis() + 5_000
            closer.start()
            val remaining = (handshakeDeadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
            check(remaining > 0 && closingAttempt.await(remaining, TimeUnit.MILLISECONDS)) { "Owned session close did not start" }
            var blocked = false
            while (SystemClock.uptimeMillis() < handshakeDeadline && !blocked) {
                workerFailure.get()?.let { throw it }
                blocked = closer.state == Thread.State.WAITING && closer.stackTrace.any {
                    it.className == RimeDaemon::class.java.name && it.methodName == "destroySession"
                }
                if (!blocked) Thread.yield()
            }
            check(blocked) { "Owned close was not observed waiting inside destroySession: ${closer.state}; $events" }
            check("close-completed" !in events)
            events.add("close-blocked-by-read")
            release.countDown()
            verified("closing the owned session waits for the in-flight cached read")
            join(reader)
            join(closer)
            workerFailure.get()?.let { throw it }
            check(events.toList() == listOf("read-entered", "close-attempt", "close-blocked-by-read", "read-released", "close-completed"))
            verified("cache read finishes before the owned close completes")
            val rejected = runCatching { closing.run { statusCached.isAsciiMode } }.exceptionOrNull()
            check(rejected is IllegalStateException && rejected.message == "Session $closingName is not established")
            verified("closed Session.run remains strictly rejected")
            keeper.run { statusCached.isAsciiMode }
            check(RimeDaemon.readCached { statusCached.isAsciiMode } != null)
            verified("the other owned session remains usable after its peer closes")
            val liveRefresh = refresh(instrumentation, keyboard)
            verified("retained keys refresh visual plans, labels and previews; current attached keys expose Button nodes with a surviving session")
            RimeDaemon.destroySession(keeperName)
            var afterBlockCalled = false
            val after = RimeDaemon.readCached {
                afterBlockCalled = true
                statusCached.isAsciiMode
            }
            val noSession = after == null
            if (noSession) {
                check(!afterBlockCalled)
                verified("after the actual last session closes, readCached returns null without entering the block")
            } else {
                check(afterBlockCalled)
            }
            val afterRefresh = refresh(instrumentation, keyboard)
            verified("retained keys refresh and current attached keys expose Button nodes after both probe-owned sessions close")
            details.put("after_owned_close_no_session_observed", noSession)
                .put("after_owned_close_other_session_present", !noSession)
                .put("no_session_branch_covered", entryHadNoSession || noSession)
                .put("post_close_refresh_followed_no_session_observation", noSession)
                .put("production_key_views", keyboard.views.size)
                .put("surviving_session_refresh", liveRefresh)
                .put("after_owned_close_refresh", afterRefresh)
                .put("events", org.json.JSONArray(events.toList()))
                .put("direct_native_or_input_calls_performed", false)
            return Bundle().apply {
                putInt("cached_read_teardown_checks", checks)
                putString("cached_read_teardown_report", details.toString())
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            release.countDown()
            entered.countDown()
            closingAttempt.countDown()
            val cleanup = runCatching {
                threads.forEach(::join)
                workerFailure.get()?.let { throw it }
            }.exceptionOrNull()
            // Do not wait on the daemon lock if a worker failed to return it.
            val ownedCleanup = if (threads.none { it.isAlive }) {
                runCatching {
                    RimeDaemon.destroySession(closingName)
                    RimeDaemon.destroySession(keeperName)
                }.exceptionOrNull()
            } else {
                IllegalStateException("Owned cleanup could not run while a teardown worker remained alive")
            }
            listOfNotNull(cleanup, ownedCleanup).forEach { error ->
                val original = failure
                if (original != null) {
                    if (original !== error) original.addSuppressed(error)
                } else {
                    failure = error
                }
            }
            if (failure != null && (cleanup != null || ownedCleanup != null)) throw checkNotNull(failure)
        }
    }
}
