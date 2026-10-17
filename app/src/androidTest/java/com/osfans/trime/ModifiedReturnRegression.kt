/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import android.widget.LinearLayout
import com.osfans.trime.core.RimeApi
import com.osfans.trime.ime.core.TrimeInputMethodService
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.UUID

/** Virtual Return travels through the native queue, service and a real editor connection. */
object ModifiedReturnRegression {
    private data class RecordedKey(
        val action: Int,
        val code: Int,
        val modifiers: Int,
        val downTime: Long,
        val eventTime: Long,
    ) {
        constructor(event: KeyEvent) : this(event.action, event.keyCode, event.metaState, event.downTime, event.eventTime)

        fun json() = JSONObject().apply {
            put("action", action)
            put("key_code", code)
            put("meta_state", modifiers)
            put("down_time_ms", downTime)
            put("event_time_ms", eventTime)
        }
    }

    private class RecordingEditor(editor: Activity) : EditText(editor) {
        val actions = Collections.synchronizedList(mutableListOf<Int>())
        val sent = Collections.synchronizedList(mutableListOf<RecordedKey>())
        val received = Collections.synchronizedList(mutableListOf<RecordedKey>())

        override fun isSuggestionsEnabled(): Boolean = false

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val connection = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(connection, false) {
                override fun performEditorAction(actionCode: Int): Boolean {
                    actions.add(actionCode)
                    return super.performEditorAction(actionCode)
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    sent.add(RecordedKey(event))
                    return super.sendKeyEvent(event)
                }
            }
        }
    }

    suspend fun verify(
        instrumentation: Instrumentation,
        service: TrimeInputMethodService,
        editor: Activity,
        container: LinearLayout,
        restoreEditor: EditText,
        focus: suspend (EditText) -> Unit,
        runApi: suspend (suspend RimeApi.() -> Unit) -> Unit,
        dispatch: (Int) -> Unit,
    ) {
        fun <T> main(block: () -> T): T {
            if (Looper.myLooper() == Looper.getMainLooper()) return block()
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(block) }
            return checkNotNull(result).getOrThrow()
        }
        suspend fun until(predicate: () -> Boolean) {
            withTimeout(30_000) { while (!main(predicate)) delay(25) }
        }
        val fixtureText = "modified-return-fixture"
        val customAction = 731
        val records = JSONArray()
        val report = JSONObject().apply {
            put("run_id", UUID.randomUUID().toString())
            put("pid", android.os.Process.myPid())
            put("cases", records)
            put("passed", false)
        }
        val field = main {
            RecordingEditor(editor).apply {
                id = View.generateViewId()
                hint = "modified Return audit"
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setImeActionLabel("Return audit", customAction)
                setText(fixtureText)
                setSelection(text.length)
                setOnEditorActionListener { _, _, _ -> true }
                setOnKeyListener { _, code, event ->
                    received.add(RecordedKey(event))
                    // Consume only the delivered Enter so its default editor behavior cannot
                    // obscure whether the IME used a key chord or performed the action.
                    code == KeyEvent.KEYCODE_ENTER
                }
                container.addView(this, LinearLayout.LayoutParams(-1, 72))
            }
        }
        report.put("field_id", field.id)
        try {
            focus(field)
            runApi {
                clearComposition()
                clearPredictionContext()
                check(getRawInput().isEmpty())
            }
            val cases = listOf(
                Triple("Return", 0, null),
                Triple("Shift+Return", KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SHIFT_LEFT),
                Triple("Control+Return", KeyEvent.META_CTRL_ON, KeyEvent.KEYCODE_CTRL_LEFT),
                Triple("Alt+Return", KeyEvent.META_ALT_ON, KeyEvent.KEYCODE_ALT_LEFT),
                Triple("Meta+Return", KeyEvent.META_META_ON, KeyEvent.KEYCODE_META_LEFT),
            )
            val chordMask = KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON
            for ((name, expectedModifier, modifierKey) in cases) {
                main {
                    check(service.currentInputEditorInfo?.fieldId == field.id && field.hasFocus())
                    field.actions.clear()
                    field.sent.clear()
                    field.received.clear()
                    // The listener queues the virtual key through native Rime and the service;
                    // explicit masks avoid depending on user presets or the key-token parser.
                    dispatch(expectedModifier)
                }
                runApi { check(getRawInput().isEmpty()) }
                if (modifierKey == null) {
                    until { field.actions.size == 1 }
                } else {
                    until { field.received.count { it.code == KeyEvent.KEYCODE_ENTER } == 2 }
                }
                instrumentation.waitForIdleSync()
                main {
                    val actionCalls = synchronized(field.actions) { field.actions.toList() }
                    val sent = synchronized(field.sent) { field.sent.toList() }
                    val received = synchronized(field.received) { field.received.toList() }
                    records.put(
                        JSONObject().apply {
                            put("key", name)
                            put("expected_modifier", expectedModifier)
                            put("actions", JSONArray(actionCalls))
                            put("sent", JSONArray(sent.map { it.json() }))
                            put("received", JSONArray(received.map { it.json() }))
                        },
                    )
                    if (modifierKey == null) {
                        check(actionCalls == listOf(customAction)) { "Plain Return did not perform the editor's custom action exactly once: $actionCalls" }
                        check(sent.isEmpty() && received.isEmpty()) { "Plain Return also sent a key event" }
                    } else {
                        check(actionCalls.isEmpty()) { "$name was incorrectly converted to an editor action: $actionCalls" }
                        check(
                            sent.map { it.action to it.code } == listOf(
                                KeyEvent.ACTION_DOWN to modifierKey,
                                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_ENTER,
                                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_ENTER,
                                KeyEvent.ACTION_UP to modifierKey,
                            ),
                        ) { "$name did not send exactly one bracketed modifier/Enter down-up sequence: $sent" }
                        val enterSent = sent.filter { it.code == KeyEvent.KEYCODE_ENTER }
                        val enterReceived = received.filter { it.code == KeyEvent.KEYCODE_ENTER }
                        check(enterSent.all { it.modifiers and chordMask == expectedModifier }) { "$name lost or added a modifier: $enterSent" }
                        check(enterReceived == enterSent) { "$name was not delivered unchanged to the actual editor: sent=$enterSent received=$enterReceived" }
                    }
                    check(field.text.toString() == fixtureText) { "$name unexpectedly changed the fixture text" }
                    check(service.currentInputEditorInfo?.fieldId == field.id && field.hasFocus()) { "$name changed editor ownership" }
                }
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: virtual $name preserves editor action/key-chord semantics\n") })
            }
            report.put("passed", true)
        } catch (failure: Throwable) {
            report.put("failure", failure.toString())
            main {
                report.put("failure_actions", JSONArray(synchronized(field.actions) { field.actions.toList() }))
                report.put("failure_sent", JSONArray(synchronized(field.sent) { field.sent.map { it.json() } }))
                report.put("failure_received", JSONArray(synchronized(field.received) { field.received.map { it.json() } }))
            }
            throw failure
        } finally {
            try {
                focus(restoreEditor)
            } finally {
                main { container.removeView(field) }
                val output = File(instrumentation.targetContext.filesDir, "runtime-audit/modified-return/report.json")
                check(output.parentFile?.isDirectory == true || output.parentFile?.mkdirs() == true)
                report.put("finished_uptime_ms", SystemClock.uptimeMillis())
                output.writeText(report.toString(2) + "\n")
            }
        }
    }
}
