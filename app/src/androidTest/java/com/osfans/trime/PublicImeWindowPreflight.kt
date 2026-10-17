/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ui.main.EditorProbeActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** A real empty editor asks IMMS to bind the selected IME after instrumentation replaced its process. */
internal object PublicImeWindowPreflight {
    private class State {
        @Volatile var owner: EditorProbeActivity? = null
        @Volatile var resumed = false
        @Volatile var registered = false
        @Volatile var launchRequested = false
        @Volatile var launchMainStarted = false
        @Volatile var launchAccepted = false
        @Volatile var launchAttemptedOnMain = false
        @Volatile var launchAborted = false
        @Volatile var cleanupRequested = false
        @Volatile var callbackFailure: Throwable? = null
        @Volatile var readyOwners: Set<String>? = null
        // Only the Main thread accesses this collection, including destruction checks.
        val created = mutableListOf<EditorProbeActivity>()
        val destroyed = mutableSetOf<EditorProbeActivity>()
    }

    fun synchronize(instrumentation: Instrumentation, workDeadline: Long, totalDeadline: Long, evidence: JSONObject, sessionOwners: () -> Set<String>) {
        check(Looper.myLooper() != Looper.getMainLooper())
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        val started = SystemClock.uptimeMillis()
        check(started < workDeadline && workDeadline <= totalDeadline)
        val handler = Handler(Looper.getMainLooper())
        var operationDeadline = minOf(workDeadline, started + 30_000L)
        val token = UUID.randomUUID().toString()
        val extra = "com.osfans.trime.PUBLIC_IME_PREFLIGHT"
        val expectedIme = "${context.packageName}/${TrimeInputMethodService::class.java.name}"
        val ownerPrefix = TrimeInputMethodService::class.java.name + "#"
        val state = State()
        var failure: Throwable? = null
        val events = JSONArray()
        evidence.put("scope", "Real existing Debug EditorProbeActivity and empty editor; no input/secure-setting/session creation")
            .put("token", token).put("pid", android.os.Process.myPid()).put("events", events)
        fun event(name: String) {
            events.put(JSONObject().put("name", name).put("elapsed_ms", SystemClock.uptimeMillis() - started))
        }
        fun <T> postedMain(cleanup: Boolean = false, block: () -> T): T {
            val task = FutureTask<T> { block() }
            if (!cleanup) check(SystemClock.uptimeMillis() < operationDeadline)
            check(handler.post(task))
            try {
                val remaining = operationDeadline - SystemClock.uptimeMillis()
                check(remaining > 0) { "IME preflight main-thread budget exhausted" }
                return task.get(remaining, TimeUnit.MILLISECONDS)
            } catch (error: Throwable) {
                // A cleanup request remains queued even after its bounded wait expires.
                // Normal work is cancelled before execution where possible; running Android calls
                // cannot be preempted. An unknown/late operation always fails this probe.
                if (!cleanup) task.cancel(false)
                if (error is ExecutionException) throw (error.cause ?: error)
                throw error
            }
        }
        fun <T> main(block: () -> T): T = postedMain(block = block)
        fun await(label: String, limit: Long, predicate: () -> Boolean) {
            while (!predicate()) {
                check(SystemClock.uptimeMillis() < limit) { label }
                SystemClock.sleep(50)
            }
            check(SystemClock.uptimeMillis() <= limit) { "$label: late observation" }
        }
        fun selected(): String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        fun callbackError(error: Throwable) {
            val primary = state.callbackFailure
            if (primary == null) state.callbackFailure = error else if (primary !== error) primary.addSuppressed(error)
        }
        fun finishKnown(activity: EditorProbeActivity) {
            try {
                if (!activity.isFinishing && !activity.isDestroyed) activity.finish()
            } catch (error: Throwable) {
                callbackError(error)
            }
        }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, saved: Bundle?) {
                if (activity is EditorProbeActivity && activity.intent.getStringExtra(extra) == token) {
                    state.created += activity
                    val first = state.owner
                    if (first == null) state.owner = activity
                    else if (first !== activity) callbackError(IllegalStateException("Duplicate owned preflight Activity"))
                    event("created")
                    if (state.cleanupRequested || first != null) finishKnown(activity)
                }
            }
            override fun onActivityResumed(activity: Activity) { if (activity === state.owner) { state.resumed = true; event("resumed") } }
            override fun onActivityPaused(activity: Activity) { if (activity === state.owner) { state.resumed = false; event("paused") } }
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is EditorProbeActivity && activity in state.created) {
                    state.destroyed += activity
                    event("destroyed")
                    if (state.cleanupRequested && state.registered && state.created.all { it in state.destroyed }) {
                        try {
                            application.unregisterActivityLifecycleCallbacks(this)
                            state.registered = false
                        } catch (error: Throwable) {
                            callbackError(error)
                        }
                    }
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, saved: Bundle) = Unit
        }
        try {
            check(selected() == expectedIme) { "Preflight must preserve the actual selected candidate IME" }
            val limit = operationDeadline
            state.launchRequested = true // Publish before submitting work that can outlive a bounded wait.
            main {
                state.launchMainStarted = true
                application.registerActivityLifecycleCallbacks(callbacks)
                state.registered = true
                if (state.cleanupRequested) {
                    state.launchAborted = true
                } else {
                    state.launchAttemptedOnMain = true
                    context.startActivity(Intent(context, EditorProbeActivity::class.java).putExtra(extra, token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    state.launchAccepted = true
                    event("launch_requested")
                }
            }
            await("Real preflight Activity did not resume with focus", limit) {
                main { state.owner?.let { state.resumed && !it.isFinishing && !it.isDestroyed && it.hasWindowFocus() } == true }
            }
            val activity = main { checkNotNull(state.owner) }
            val editor = main {
                EditText(activity).apply {
                    id = View.generateViewId()
                    setSingleLine(true)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    setText("")
                    setSelection(0)
                    activity.setContentView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    check(requestFocus())
                }
            }
            val manager = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            await("Real empty editor did not receive a visible focused window", limit) {
                main { editor.isAttachedToWindow && editor.isLaidOut && editor.width > 0 && editor.height > 0 && editor.hasWindowFocus() && editor.hasFocus() }
            }
            main {
                evidence.put("activity_class", activity.javaClass.name).put("activity_identity", System.identityHashCode(activity))
                    .put("editor_id", editor.id).put("editor_input_type", editor.inputType).put("editor_ime_options", editor.imeOptions)
                    .put("window_focus", activity.hasWindowFocus()).put("editor_focus", editor.hasFocus())
                    .put("show_soft_input_returned", manager.showSoftInput(editor, 0)).put("show_soft_input_called", true)
                event("show_soft_input")
            }
            await("Public IMMS request did not establish the unique selected READY IME", limit) {
                main {
                    state.callbackFailure?.let { throw it }
                    val owners = sessionOwners()
                    val engine = RimeDaemon.engineState.value
                    evidence.put("owners_observed", JSONArray(owners.sorted())).put("engine_observed", engine.name)
                        .put("manager_active", manager.isActive(editor)).put("selected_ime_observed", selected() ?: JSONObject.NULL)
                    (owners.size == 1 && owners.single().startsWith(ownerPrefix) && engine == RimeLifecycle.State.READY &&
                        manager.isActive(editor) && selected() == expectedIme).also { if (it) state.readyOwners = owners }
                }
            }
            evidence.put("owners_before_finish", JSONArray(checkNotNull(state.readyOwners).sorted()))
            main { check(editor.text.isEmpty()) { "Preflight must never enter or commit input" } }
        } catch (error: Throwable) {
            failure = error
        } finally {
            val workEnded = SystemClock.uptimeMillis()
            evidence.put("work_elapsed_ms", workEnded - started).put("work_deadline_respected", workEnded <= workDeadline)
            if (workEnded > workDeadline) {
                val late = IllegalStateException("IME preflight work exceeded the original work deadline")
                val primary = failure
                if (primary == null) failure = late else primary.addSuppressed(late)
            }
            state.cleanupRequested = true
            operationDeadline = minOf(totalDeadline, workEnded + 10_000L)
            fun clean(block: () -> Unit) {
                try { block() } catch (error: Throwable) {
                    val primary = failure
                    if (primary == null) failure = error else if (primary !== error) primary.addSuppressed(error)
                }
            }
            var windowsDestroyed = false
            clean {
                // Always queue token cleanup, even if a timed-out launch has not published its result.
                postedMain(cleanup = true) {
                    state.created.forEach(::finishKnown)
                    event("finish_requested")
                }
                await("Owned preflight Activity did not reach actual onDestroy", operationDeadline) {
                    main {
                        state.created.forEach(::finishKnown)
                        (!state.launchAttemptedOnMain ||
                            (state.created.isNotEmpty() && state.created.all { it.isDestroyed && it in state.destroyed }))
                    }
                }
                windowsDestroyed = true
                evidence.put("activity_destroyed", main { state.created.isNotEmpty() && state.created.all { it.isDestroyed } })
            }
            clean {
                state.readyOwners?.let { expected ->
                    await("Preflight teardown changed the complete READY IME owner set", operationDeadline) {
                        main {
                            val owners = sessionOwners()
                            val state = RimeDaemon.engineState.value
                            evidence.put("owners_after_finish", JSONArray(owners.sorted())).put("engine_after_finish", state.name)
                            owners == expected && state == RimeLifecycle.State.READY
                        }
                    }
                }
                check(selected() == expectedIme) { "Selected IME changed during preflight" }
            }
            clean {
                // This is also unconditional: a launch task may have registered after its caller timed out.
                postedMain(cleanup = true) {
                    state.created.forEach(::finishKnown)
                    check(!state.launchAttemptedOnMain || state.created.isNotEmpty() && state.created.all { it.isDestroyed && it in state.destroyed }) {
                        "Owned launch has unknown teardown; cleanup callback retained, VM must not be reused"
                    }
                    if (state.registered) {
                        application.unregisterActivityLifecycleCallbacks(callbacks)
                        state.registered = false
                    }
                    state.callbackFailure?.let { throw it }
                }
            }
            if (windowsDestroyed && !state.registered) {
                // Release strong references only after actual destruction and callback removal.
                clean {
                    main {
                        evidence.put("owned_activities", JSONArray(state.created.map { activity ->
                            JSONObject().put("class", activity.javaClass.name).put("identity", System.identityHashCode(activity))
                                .put("destroyed", activity.isDestroyed).put("on_destroy_observed", activity in state.destroyed)
                        }))
                        state.created.clear()
                        state.destroyed.clear()
                        state.owner = null
                    }
                }
            }
            val cleanupEnded = SystemClock.uptimeMillis()
            evidence.put("launch_requested", state.launchRequested).put("callbacks_unregistered", !state.registered)
                .put("cleanup_complete", windowsDestroyed && !state.registered).put("elapsed_ms", cleanupEnded - started)
                .put("launch_main_started", state.launchMainStarted)
                .put("launch_accepted", state.launchAccepted).put("launch_attempted_on_main", state.launchAttemptedOnMain)
                .put("launch_aborted_before_start", state.launchAborted)
                .put("cleanup_callback_retained_for_late_launch", state.registered)
                .put("total_deadline_respected", cleanupEnded <= totalDeadline)
            if (cleanupEnded > totalDeadline) {
                val late = IllegalStateException("IME preflight cleanup exceeded the original total deadline")
                val primary = failure
                if (primary == null) failure = late else primary.addSuppressed(late)
            }
            failure?.let { evidence.put("error", it.stackTraceToString()) }
        }
        failure?.let { throw it }
    }
}
