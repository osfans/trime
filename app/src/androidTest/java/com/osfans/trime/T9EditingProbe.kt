/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.children
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.T9Action
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.composition.T9DisambiguationView
import com.osfans.trime.ime.core.InlinePreeditMode
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyboardView
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ui.main.ClipEditActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File

/** Real editor/IME integration; the temporary clip is never saved. */
object T9EditingProbe {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    fun run(instrumentation: Instrumentation, layoutOnly: Boolean = false, geometryOnly: Boolean = false, assistOnly: Boolean = false) {
        val result = Bundle()
        // Instrumentation can start before Application.onCreate has initialized preferences.
        instrumentation.waitForIdleSync()
        val preference = AppPrefs.defaultInstance().general.inlinePreeditMode
        val originalMode = preference.getValue()
        val undoPreference = AppPrefs.defaultInstance().keyboard.hookCtrlZY
        val originalUndoHook = undoPreference.getValue()
        var activity: Activity? = null
        var session: RimeSession? = null
        var passed = false
        fun <T> main(block: () -> T): T {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun phase(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$text\n") })
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish") && Build.VERSION.SDK_INT >= 29) { "API 29+ emulator only" }
            if (assistOnly) check(UserDataManager.isStorageAvailable()) { "Complete Trime storage setup before the T05 probe" }
            preference.setValue(InlinePreeditMode.COMMIT_TEXT_PREVIEW)
            undoPreference.setValue(true)
            session = main { RimeDaemon.createSession(javaClass.name) }
            runBlocking {
                // Debug builds deploy on every launch; keep this separate from editing deadlines.
                withTimeout(300_000) { checkNotNull(session).runOnReady { } }
            }
            phase("Engine ready; starting editor interaction checks")
            val editor = instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ClipEditActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            activity = editor
            val field = main {
                editor.findViewById<EditText>(R.id.clip_edit_text).apply {
                    setText("P:")
                    setSelection(2)
                }
            }
            runBlocking {
                withTimeout(if (assistOnly) 1_200_000 else 480_000) {
                    var input: InputView? = null
                    while (input == null) {
                        input = main {
                            WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
                                .filterIsInstance<InputView>().firstOrNull { it.isShown }
                        }
                        if (input == null) delay(100)
                    }
                    val view = checkNotNull(input)
                    val service = main { view.di.direct.instance<TrimeInputMethodService>() }
                    while (!main {
                            field.hasWindowFocus() && service.currentInputEditorInfo?.let {
                                it.initialSelStart == 2 && it.initialSelEnd == 2
                            } == true
                        }
                    ) {
                        delay(50)
                    }
                    val listener = main { view.di.direct.instance<CommonKeyboardActionListener>().listener }
                    fun switchKeyboard(action: String) {
                        main { listener.onAction(KeyAction(KeyActionToken.Plain(action), ThemeManager.activeTheme.presetKeys)) }
                        // KeyboardWindow posts attachment to the main queue before posting its Rime job.
                        main { }
                    }
                    suspend fun api(block: suspend RimeApi.() -> Unit) {
                        var outcome: Result<Unit>? = null
                        val job = main { service.postRimeJob { outcome = runCatching { block() } } }
                        job.join()
                        check(!job.isCancelled) { "Engine/editor job cancelled" }
                        checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
                        delay(200)
                    }
                    suspend fun text(expected: String) {
                        val matched = withTimeoutOrNull(10_000) {
                            while (main { field.text.toString() } != expected) delay(50)
                            true
                        }
                        check(matched == true) { "Expected editor text <$expected>, got <${main { field.text.toString() }}>" }
                    }
                    suspend fun key(value: Int) = api { processKey(value) }
                    suspend fun type(value: String) {
                        value.forEach { key(it.code) }
                    }
                    suspend fun lock(spelling: String) = api {
                        val choice = t9Cached.choices.first { it.spelling == spelling && !it.completion }
                        check(t9Action(t9Cached.revision, T9Action.Lock, choice.start, choice.end, spelling))
                    }
                    suspend fun action(kind: T9Action) = api { check(t9Action(t9Cached.revision, kind)) }
                    var originalSchema = ""
                    var originalAscii = false
                    api {
                        originalSchema = selectedSchemaId()
                        originalAscii = getRuntimeOption("ascii_mode")
                        clearComposition()
                        check(selectSchema("luna_pinyin_t9"))
                        setRuntimeOption("ascii_mode", false)
                    }
                    try {
                        withTimeout(
                            if (assistOnly) {
                                900_000
                            } else if (layoutOnly) {
                                480_000
                            } else {
                                180_000
                            },
                        ) editing@{
                            if (assistOnly) {
                                T9AssistProbe(instrumentation, service, field).run(!geometryOnly)
                                return@editing
                            }
                            if (layoutOnly) {
                                T9LayoutProbe(instrumentation, service, field).run(geometryOnly)
                                return@editing
                            }
                            phase("Editor and T9 ready; starting the editing deadline")
                            delay(200)
                            type("64")
                            api { moveCursorPos(0, "6") }
                            type("4")
                            text("P:644")
                            action(T9Action.Cancel)
                            text("P:")
                            phase("Stale preedit taps cannot move a newer composition")
                            type("64426")
                            text("P:64426")
                            main { listener.onKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON) }
                            text("P:6442")
                            main { listener.onKey(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON) }
                            delay(300)
                            text("P:6442")
                            type("6")
                            lock("ni")
                            text("P:ni 426")
                            main { field.setSelection(2) }
                            delay(300)
                            main { field.setSelection(3) }
                            withTimeout(10_000) {
                                while (main { field.selectionStart } != 2) delay(50)
                            }
                            main {
                                val bar = descendants(view).filterIsInstance<T9DisambiguationView>().single()
                                descendants(bar).filterIsInstance<TextView>().first { it.text.toString() == "ni" }.performClick()
                            }
                            delay(300)
                            api { check(main { field.selectionStart } == 2 + compositionCached.cursorPos) }
                            key(0xff08)
                            text("P:64426")
                            key(0xff08)
                            text("P:6426")
                            action(T9Action.Undo)
                            text("P:64426")
                            action(T9Action.Undo)
                            text("P:ni 426")
                            phase("Syllable tap, caret, unlock boundary and editing undo")
                            main { field.setSelection(6) }
                            delay(300)
                            key('2'.code)
                            api { check(getRawInput() == "644226") }
                            key(0xff08)
                            text("P:ni 426")
                            main { field.setSelection(field.length()) }
                            delay(300)
                            key('7'.code)
                            api { check(getRawInput() == "644267") }
                            key(0xff08)
                            main { field.setSelection(5, 6) }
                            delay(300)
                            check(main { field.selectionStart == field.selectionEnd })
                            key(0xff57)
                            val beforeSymbols = main { field.text.toString() }
                            val nineKeys = main { view.di.direct.instance<KeyboardWindow>().currentKeyboard.keys.map { it.click?.code } }
                            check((KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9).all { it in nineKeys } && KeyEvent.KEYCODE_A !in nineKeys)
                            switchKeyboard("Keyboard_symbols")
                            api {
                                check(getRawInput() == "64426" && t9Cached.segments.any { it.locked }) {
                                    "Opening symbols committed or changed the T9 composition"
                                }
                            }
                            text(beforeSymbols)
                            switchKeyboard("Keyboard_default")
                            delay(300)
                            check(main { view.di.direct.instance<KeyboardWindow>().currentKeyboard.keys.map { it.click?.code } } == nineKeys)
                            api {
                                check(getRawInput() == "64426" && t9Cached.segments.any { it.locked }) {
                                    "Returning from symbols lost the T9 composition"
                                }
                            }
                            phase("Editor middle/end/range selection and symbol-page round trip")
                            api {
                                val index = getCandidates(0, 100).indexOfFirst { it.text == "你好" }
                                check(index >= 0 && selectCandidate(index, true))
                            }
                            text("P:你好")
                            delay(300)
                            text("P:你好")
                            switchKeyboard("Keyboard_symbols")
                            api { check(getRuntimeOption("ascii_mode")) { "Symbols remembered the temporary T9 mode" } }
                            switchKeyboard("Keyboard_default")
                            api { check(!getRuntimeOption("ascii_mode")) }
                            text("P:你好")
                            phase("Empty symbol page restores its own mode after the T9 round trip")
                            type("64")
                            action(T9Action.Cancel)
                            text("P:你好")
                            type("64")
                            main { field.setSelection(0) }
                            text("P:你好")
                            check(main { field.selectionStart == 0 }) { "Cancelling outside composition moved the editor caret" }
                            main { field.setSelection(field.length()) }
                            delay(200)
                            key(0xff08)
                            text("P:你")
                            phase("Hanzi commits once; cancel differs from editor backspace")
                            type("64")
                            text("P:你64")
                            main { field.setSelection(field.length() - 1) }
                            withTimeout(10_000) {
                                var cursor = -1
                                while (cursor != 1) api { cursor = compositionCached.cursorPos }
                            }
                            key(0xff0d)
                            text("P:你64")
                            withTimeout(10_000) {
                                while (!main { field.selectionStart == field.length() }) delay(50)
                            }
                            type("6")
                            text("P:你646")
                            action(T9Action.Cancel)
                            text("P:你64")
                            phase("Raw commit after middle correction moves to the committed text end")
                            type("6442664426")
                            val keyboard = main { descendants(view).filterIsInstance<KeyboardView>().first { it.isShown } }
                            val delete = main {
                                val window = view.di.direct.instance<KeyboardWindow>()
                                val index = window.currentKeyboard.keys.indexOfFirst { it.click?.code == KeyEvent.KEYCODE_DEL }
                                check(index >= 0)
                                keyboard.getChildAt(index)
                            }
                            fun touch(action: Int, down: Long) = main {
                                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, delete.width / 2f, delete.height / 2f, 0)
                                event.source = InputDevice.SOURCE_TOUCHSCREEN
                                try {
                                    delete.dispatchTouchEvent(event)
                                } finally {
                                    event.recycle()
                                }
                            }
                            val barrier = CompletableDeferred<Unit>()
                            val entered = CompletableDeferred<Unit>()
                            val blocked = service.postRimeJob {
                                entered.complete(Unit)
                                barrier.await()
                            }
                            entered.await()
                            val beforeRepeat = main { field.text.toString() }
                            val down = SystemClock.uptimeMillis()
                            try {
                                touch(MotionEvent.ACTION_DOWN, down)
                                delay(AppPrefs.defaultInstance().keyboard.longPressTimeout.getValue().toLong() + 300)
                                touch(MotionEvent.ACTION_UP, down)
                            } finally {
                                barrier.complete(Unit)
                                blocked.join()
                            }
                            api { }
                            check(main { field.text.toString() } == beforeRepeat) { "Queued repeat survived release" }
                            val nextDown = SystemClock.uptimeMillis()
                            touch(MotionEvent.ACTION_DOWN, nextDown)
                            delay(AppPrefs.defaultInstance().keyboard.longPressTimeout.getValue().toLong() + 300)
                            touch(MotionEvent.ACTION_UP, nextDown)
                            // Let a native deletion already running at release finish before measuring stability.
                            api { }
                            val stopped = main { field.text.toString() }
                            check(stopped != beforeRepeat) { "Held backspace did not repeat" }
                            delay(600)
                            check(main { field.text.toString() } == stopped) { "Backspace continued after release" }
                            phase("Repeat works and pending backspace stops on release")
                            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                                try {
                                    File(instrumentation.targetContext.cacheDir, "t02-editing.png").outputStream().use {
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            withTimeout(30_000) {
                                api {
                                    clearComposition()
                                    selectSchema(originalSchema)
                                    setRuntimeOption("ascii_mode", originalAscii)
                                }
                            }
                        }
                    }
                }
            }
            passed = true
            result.putString(
                "stream",
                when {
                    assistOnly -> "PASS: T05 settings, source labels, exact priority, repair locking and editor commit\n"
                    layoutOnly && geometryOnly -> "PASS: T03 real keyboard geometry\n"
                    layoutOnly -> "PASS: T03 real keyboard layout and gestures\n"
                    else -> "PASS: T02 real editor, selection, locks, undo, cancel, commit, symbol pages and repeat cancellation\n"
                },
            )
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            preference.setValue(originalMode)
            undoPreference.setValue(originalUndoHook)
            activity?.let { main { it.finish() } }
            if (session != null) RimeDaemon.destroySession(javaClass.name)
            check(preference.sharedPreferences.edit().commit())
        }
        result.putBoolean("passed", passed)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
