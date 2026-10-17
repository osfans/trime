/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.autofill.AutofillManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeSchema
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.popup.PagedCandidatesUi
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.CandidatesView
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.composition.T9DisambiguationView
import com.osfans.trime.ime.core.EditorPolicy
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.ManualLanguageMode
import com.osfans.trime.ime.core.PrivacyModeState
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.KeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardView
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.switches.SwitchOptionAdapter
import com.osfans.trime.ime.switches.SwitchOptionEntry
import com.osfans.trime.ime.switches.SwitchOptionWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import com.osfans.trime.ui.main.EditorProbeActivity
import com.osfans.trime.util.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.kodein.di.DI
import org.kodein.di.bindInstance
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Real InputConnections in an unsaved, disposable editor. Never resets learned words. */
object EditorLifecycleProbe {
    @RequiresApi(26)
    private object AutofillServiceApi26 {
        fun isEnabled(context: Context): Boolean? = context.getSystemService(AutofillManager::class.java)?.isEnabled
    }

    /** A callback host changes Android's animation dispatch path; also test without it. */
    private class ImeAnimationProbe(private val requested: Boolean) {
        private val running = mutableSetOf<WindowInsetsAnimationCompat>()
        private var host: View? = null
        private var wasInstalled = false
        var generation = 0
            private set
        var prepares = 0
            private set
        var ends = 0
            private set
        var lastTransitionUptimeMs = 0L
            private set
        val isIdle: Boolean get() = running.isEmpty()

        fun install(view: View) {
            if (!requested || Build.VERSION.SDK_INT < 30) return
            check(host == null)
            host = view
            ViewCompat.setWindowInsetsAnimationCallback(
                view,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                        running.add(animation)
                        prepares++
                        generation++
                        lastTransitionUptimeMs = SystemClock.uptimeMillis()
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: List<WindowInsetsAnimationCompat?>,
                    ): WindowInsetsCompat {
                        runningAnimations.filterNotNull().forEach { animation ->
                            if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                                running.add(animation)
                                lastTransitionUptimeMs = SystemClock.uptimeMillis()
                            }
                        }
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                        running.remove(animation)
                        ends++
                        generation++
                        lastTransitionUptimeMs = SystemClock.uptimeMillis()
                    }
                },
            )
            wasInstalled = true
        }

        fun snapshot(): JSONObject = JSONObject().apply {
            put("callback_requested", requested)
            put("callback_installed", host != null)
            put("callback_ever_installed", wasInstalled)
            put(
                "settle_gate",
                when {
                    wasInstalled -> "callback_end_and_stable_insets"
                    Build.VERSION.SDK_INT >= 30 -> "stable_insets_without_animation_callback"
                    else -> "legacy_service_and_stable_geometry"
                },
            )
            put("ime_prepare_count", prepares)
            put("ime_end_count", ends)
            put("running_ime_animations", running.size)
            put("generation", generation)
            put("last_transition_uptime_ms", lastTransitionUptimeMs)
        }

        fun detach() {
            host?.let { ViewCompat.setWindowInsetsAnimationCallback(it, null) }
            host = null
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    @SuppressLint("PrivateApi")
    private fun roots(): List<View> {
        if (Build.VERSION.SDK_INT >= 29) return WindowInspector.getGlobalWindowViews()
        val type = Class.forName("android.view.WindowManagerGlobal")
        val instance = type.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        return (type.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>).toList()
    }

    /** Check the actual native 64 row, including pixels captured before software drawing. */
    private fun verifyCandidateGlyphs(instrumentation: Instrumentation, service: TrimeInputMethodService, theme: String) {
        fun <T> main(block: () -> T): T {
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        fun colorDistance(first: Int, second: Int) = kotlin.math.abs(Color.red(first) - Color.red(second)) +
            kotlin.math.abs(Color.green(first) - Color.green(second)) +
            kotlin.math.abs(Color.blue(first) - Color.blue(second))

        val directory = File(instrumentation.targetContext.filesDir, "runtime-audit/candidate-glyph/$theme")
        check(directory.isDirectory || directory.mkdirs())
        val ownedNames = listOf("screen.png", "label-0.png", "label-1.png", "report.json")
        ownedNames.forEach { name ->
            val file = File(directory, name)
            check(!file.exists() || (file.isFile && file.delete())) { "Cannot clear owned candidate evidence: $name" }
        }
        val runId = UUID.randomUUID().toString()
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("candidate_glyph_case", theme)
                putString("candidate_glyph_run_id", runId)
            },
        )
        val records = JSONArray()
        val nativeState = JSONObject()
        var passed = false
        var failure = ""
        var screenshot: Bitmap? = null
        try {
            val (delegate, rime) = main {
                val input = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().first { it.isShown }
                input.di.direct.instance<CompactCandidateDelegate>() to input.di.direct.instance<RimeSession>()
            }
            val (holder, snapshot, labelState) = main {
                val holder = checkNotNull(delegate.view.findViewHolderForAdapterPosition(0) as? CandidateViewHolder)
                val snapshot = holder.snapshot
                val showComments = AppPrefs.defaultInstance().candidates.showComments.getValue()
                val expected = listOf(holder.text, holder.comment.takeIf { showComments }.orEmpty()).filter { it.isNotEmpty() }
                check(expected.firstOrNull()?.any { it in '\u3400'..'\u9fff' } == true) { "64 did not produce a native Hanzi candidate" }
                val labels = descendants(holder.itemView).filterIsInstance<TextView>()
                    .filter { it.isShown && it.text.isNotEmpty() }.toList()
                check(labels.size == expected.size && labels.map { it.text.toString() } == expected) {
                    "Live candidate labels do not match their native holder"
                }
                Triple(holder, snapshot, expected to labels)
            }
            val (expected, labels) = labelState
            fun checkCurrentSnapshot() = main {
                check(service.isCurrentEditor(snapshot.editorToken) && delegate.adapter.snapshot == snapshot && holder.snapshot == snapshot) {
                    "Editor or bound candidate changed during the pixel capture"
                }
                rime.run {
                    check(candidateSnapshotCached == snapshot) { "Bound candidate is older than the current native snapshot" }
                    val raw = t9Cached.input
                    val schema = statusCached.schemaId
                    check(t9Cached.enabled && raw == "64" && schema == "luna_pinyin_t9") { "Candidate capture no longer owns native T9 64" }
                    nativeState.put("editor", snapshot.editorToken)
                    nativeState.put("generation", snapshot.generation)
                    nativeState.put("request", snapshot.requestId)
                    nativeState.put("raw_input", raw)
                    nativeState.put("raw_input_source", "t9Cached.input")
                    nativeState.put("schema_id", schema)
                    check(candidateSnapshotCached == snapshot) { "Native snapshot advanced while reading its cached metadata" }
                }
            }
            // A software draw can invalidate cached framework state. Capture hardware pixels first.
            checkCurrentSnapshot()
            nativeState.put("capture_start_ns", SystemClock.elapsedRealtimeNanos())
            val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            nativeState.put("capture_end_ns", SystemClock.elapsedRealtimeNanos())
            screenshot = screen
            File(directory, "screen.png").outputStream().use {
                check(screen.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            checkCurrentSnapshot()
            labels.forEachIndexed { index, view ->
                val (software, record) = main {
                    val node = view.createAccessibilityNodeInfo()
                    val accessibleText = try {
                        node.text?.toString().orEmpty()
                    } finally {
                        @Suppress("DEPRECATION")
                        node.recycle()
                    }
                    val point = IntArray(2)
                    view.getLocationOnScreen(point)
                    val bounds = Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
                    check(
                        view.width > 0 && view.height > 0 && bounds.left >= 0 && bounds.top >= 0 &&
                            bounds.right <= screen.width && bounds.bottom <= screen.height,
                    )
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
                    view.draw(canvas)
                    val color = view.currentTextColor
                    val background = screen.getPixel(bounds.left, bounds.top)
                    var softwareInk = 0
                    var screenInk = 0
                    for (y in 0 until view.height) {
                        for (x in 0 until view.width) {
                            val softwarePixel = bitmap.getPixel(x, y)
                            if (Color.alpha(softwarePixel) >= 128 && colorDistance(softwarePixel, color) <= 54) softwareInk++
                            val pixel = screen.getPixel(bounds.left + x, bounds.top + y)
                            if (colorDistance(pixel, color) <= 54 && colorDistance(pixel, background) > 54) screenInk++
                        }
                    }
                    bitmap to JSONObject().apply {
                        put("text", view.text.toString())
                        put("accessibility_text", accessibleText)
                        put("screen_bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                        put("text_color_argb", color.toLong() and 0xffffffffL)
                        put("text_size_px", view.textSize)
                        put("text_scale_x", view.textScaleX)
                        put("baseline_px", view.baseline)
                        put("scroll_x_px", view.scrollX)
                        put("scroll_y_px", view.scrollY)
                        put("software_ink_pixels", softwareInk)
                        put("screen_ink_pixels", screenInk)
                        put("required_screen_ink_pixels", (softwareInk / 4).coerceAtLeast(1))
                        put("accessibility_passed", accessibleText == expected[index])
                        put("glyph_passed", Color.alpha(color) > 8 && softwareInk > 0 && screenInk >= (softwareInk / 4).coerceAtLeast(1))
                    }
                }
                try {
                    File(directory, "label-$index.png").outputStream().use {
                        check(software.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    software.recycle()
                }
                records.put(record)
                check(record.getBoolean("accessibility_passed")) { "Candidate label has no matching Unicode accessibility text: $record" }
                check(record.getBoolean("glyph_passed")) { "Candidate label has no visible screen glyph: $record" }
            }
            passed = true
        } catch (error: Throwable) {
            failure = error.stackTraceToString()
            throw error
        } finally {
            screenshot?.recycle()
            val attachments = JSONObject()
            ownedNames.filter { it != "report.json" }.forEach { name ->
                File(directory, name).takeIf { it.isFile }?.let { attachments.put(name, sha256(it)) }
            }
            File(directory, "report.json").writeText(
                JSONObject().apply {
                    put("run_id", runId)
                    put("theme", theme)
                    put("fixture", "native T9 64")
                    put("native_state", nativeState)
                    put("hardware_capture_before_software_draw", true)
                    put("labels", records)
                    put("passed", passed)
                    put("error", failure)
                    put("attachments_sha256", attachments)
                }.toString(2) + "\n",
            )
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: $theme native 64 candidate Unicode accessibility and actual screen glyphs\n") })
    }

    private suspend fun verifyQueuedModeRestoreAcrossThemeReload(
        instrumentation: Instrumentation,
        service: TrimeInputMethodService,
        inputManager: InputMethodManager,
        chat: EditText,
        themeId: String,
    ) {
        fun <T> main(block: () -> T): T {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun input(): InputView? = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }
        fun keyboard() = checkNotNull(input()).di.direct.instance<KeyboardWindow>().currentKeyboard
        suspend fun until(label: String, predicate: () -> Boolean) {
            withTimeout(30_000) { while (!main(predicate)) delay(50) }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: $label\n") })
        }
        suspend fun api(block: suspend RimeApi.() -> Unit) {
            var outcome: Result<Unit>? = null
            val job = main { service.postRimeJob { outcome = runCatching { block() } } }
            job.join()
            check(!job.isCancelled) { "Engine/editor job cancelled" }
            checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
            main { }
        }

        // Hold the native queue after the restricted editor has entered ASCII mode. Returning to
        // chat queues its restore while a deployment-style theme rebuild creates another
        // KeyboardWindow from the still-ASCII native cache. The restore snapshot must survive the
        // rebuild so both windows queue the same final Chinese mode.
        val restoreQueueEntered = CompletableDeferred<Unit>()
        val restoreQueueRelease = CompletableDeferred<Unit>()
        val restoreQueueGate = main {
            service.postRimeJob {
                restoreQueueEntered.complete(Unit)
                restoreQueueRelease.await()
            }
        }
        val inputViewBeforeBlockedReload = main { checkNotNull(input()) }
        try {
            withTimeout(10_000) { restoreQueueEntered.await() }
            main {
                chat.requestFocus()
                inputManager.showSoftInput(chat, 0)
            }
            until("editor chat owns connection while mode restore is queued") {
                service.currentInputEditorInfo?.fieldId == chat.id && input() != null
            }
            until("queued chat restore selects T9 layout") {
                input() != null && keyboard().isT9Layout
            }
            ThemeManager.selectTheme(themeId, forceReload = true)
            until("forced theme reload overlaps the queued editor restore") {
                input()?.let { it !== inputViewBeforeBlockedReload && keyboard().isT9Layout } == true
            }
        } finally {
            restoreQueueRelease.complete(Unit)
        }
        withTimeout(10_000) { restoreQueueGate.join() }
        check(!restoreQueueGate.isCancelled) { "Mode-restore queue gate was cancelled" }
        api {
            check(!statusCached.isAsciiMode) { "Theme reload overwrote the queued Chinese-mode restore" }
        }
        check(main { service.editorModeOverride == null }) { "Completed editor-mode restore left a stale snapshot" }
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("stream", "PASS: theme reload preserves a queued restricted-editor mode restore\n") },
        )
    }

    private suspend fun verifyQueuedModeRestoreAcrossPrivacyChange(
        instrumentation: Instrumentation,
        service: TrimeInputMethodService,
        inputManager: InputMethodManager,
        restrictedEditor: EditText,
        chat: EditText,
    ) {
        fun <T> main(block: () -> T): T {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun input(): InputView? = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }
        fun keyboard() = checkNotNull(input()).di.direct.instance<KeyboardWindow>().currentKeyboard
        suspend fun until(label: String, predicate: () -> Boolean) {
            withTimeout(30_000) { while (!main(predicate)) delay(50) }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: $label\n") })
        }
        suspend fun api(block: suspend RimeApi.() -> Unit) {
            var outcome: Result<Unit>? = null
            val job = main { service.postRimeJob { outcome = runCatching { block() } } }
            job.join()
            check(!job.isCancelled) { "Engine/editor job cancelled" }
            checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
            main { }
        }

        val manualPrivacyMode = AppPrefs.defaultInstance().general.manualPrivacyMode
        check(!manualPrivacyMode.getValue()) { "Queued privacy-boundary fixture requires ordinary mode" }
        main {
            restrictedEditor.requestFocus()
            inputManager.showSoftInput(restrictedEditor, 0)
        }
        until("privacy-boundary number editor owns connection") {
            service.currentInputEditorInfo?.fieldId == restrictedEditor.id && input() != null
        }
        until("privacy-boundary number editor applies its ASCII layout") {
            input() != null && !keyboard().isT9Layout
        }
        api { check(statusCached.isAsciiMode) { "Privacy-boundary fixture did not enter ASCII mode" } }

        val restoreQueueEntered = CompletableDeferred<Unit>()
        val restoreQueueRelease = CompletableDeferred<Unit>()
        val restoreQueueGate = main {
            service.postRimeJob {
                restoreQueueEntered.complete(Unit)
                restoreQueueRelease.await()
            }
        }
        var privacyEnabled = false
        try {
            withTimeout(10_000) { restoreQueueEntered.await() }
            main {
                chat.requestFocus()
                inputManager.showSoftInput(chat, 0)
            }
            until("editor chat owns connection before queued privacy change") {
                service.currentInputEditorInfo?.fieldId == chat.id && input() != null
            }
            until("queued privacy-boundary restore selects T9 layout") {
                input() != null && keyboard().isT9Layout
            }
            check(main { service.editorModeOverride != null }) {
                "Queued privacy-boundary restore lost its restricted-editor snapshot before replay"
            }
            main { manualPrivacyMode.setValue(true) }
            privacyEnabled = true
            until("manual privacy supersedes the queued editor generation") {
                service.isPrivacyModeActive && PrivacyModeState.active
            }
            restoreQueueRelease.complete(Unit)
            withTimeout(10_000) { restoreQueueGate.join() }
            check(!restoreQueueGate.isCancelled) { "Privacy-boundary queue gate was cancelled" }
            api {
                check(Rime.isRimePrivacyGuardEnabledForTesting()) {
                    "Queued privacy change did not bind the native privacy policy"
                }
                check(!statusCached.isAsciiMode) {
                    "Privacy change discarded the queued normal-editor mode restore"
                }
            }
            check(main { service.editorModeOverride == null }) {
                "Privacy change left a stale restricted-editor mode snapshot"
            }
        } finally {
            restoreQueueRelease.complete(Unit)
            if (privacyEnabled) main { manualPrivacyMode.setValue(false) }
        }
        until("ordinary mode returns after queued privacy change") {
            !service.isPrivacyModeActive && !PrivacyModeState.active
        }
        api {
            check(!Rime.isRimePrivacyGuardEnabledForTesting())
            check(!statusCached.isAsciiMode) { "Privacy exit lost the normal editor's Chinese mode" }
        }
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("stream", "PASS: privacy changes preserve a queued restricted-editor mode restore\n")
            },
        )
    }

    private suspend fun verifyHardwareEditorPolicies(
        instrumentation: Instrumentation,
        service: TrimeInputMethodService,
        editor: Activity,
        chat: EditText,
        other: EditText,
    ) {
        fun <T> main(block: () -> T): T {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun input(): InputView? = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }
        fun keyboard() = checkNotNull(input()).di.direct.instance<KeyboardWindow>().currentKeyboard
        suspend fun until(label: String, predicate: () -> Boolean) {
            withTimeout(30_000) { while (!main(predicate)) delay(50) }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: $label\n") })
        }
        suspend fun api(block: suspend RimeApi.() -> Unit) {
            var outcome: Result<Unit>? = null
            val job = main { service.postRimeJob { outcome = runCatching { block() } } }
            job.join()
            check(!job.isCancelled) { "Engine/editor job cancelled" }
            checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
            main { }
        }
        fun speechAllowed(): Boolean = InputFeedbackManager::class.java
            .getDeclaredField("speechAllowed")
            .apply { isAccessible = true }
            .getBoolean(InputFeedbackManager)
        val inputManager = editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        suspend fun focusVirtual(field: EditText) {
            main {
                field.requestFocus()
                inputManager.showSoftInput(field, 0)
            }
            until("virtual editor ${field.hint} owns connection") {
                service.currentInputEditorInfo?.fieldId == field.id && input() != null
            }
        }
        suspend fun focusPhysical(field: EditText) {
            main { field.requestFocus() }
            until("physical editor ${field.hint} owns connection") {
                service.currentInputEditorInfo?.fieldId == field.id
            }
            api { }
        }

        val candidatesMode = AppPrefs.defaultInstance().candidates.mode
        val originalCandidatesMode = candidatesMode.getValue()
        val manualPrivacyMode = AppPrefs.defaultInstance().general.manualPrivacyMode
        check(!manualPrivacyMode.getValue()) { "Hardware policy fixture requires ordinary mode" }
        try {
            main {
                other.hint = "soft-number-before-hardware"
                other.inputType = InputType.TYPE_CLASS_NUMBER
                other.setText("")
            }
            focusVirtual(other)
            val keyboardWindow = main { checkNotNull(input()).di.direct.instance<KeyboardWindow>() }
            val restrictedEditorField = KeyboardWindow::class.java.getDeclaredField("restrictedEditor").apply { isAccessible = true }
            until("soft number establishes its restricted layout before hardware input") {
                input() != null && !keyboard().isT9Layout
            }
            check(main { restrictedEditorField.getBoolean(keyboardWindow) }) {
                "Soft number did not establish a restricted keyboard policy"
            }
            api { check(statusCached.isAsciiMode) }
            check(main { service.editorModeOverride != null }) {
                "Soft number did not retain the preceding editor mode"
            }

            main { candidatesMode.setValue(PopupCandidatesMode.INPUT_DEVICE) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_6)
            until("physical keyboard hides the virtual keyboard") {
                service.isInputViewShown && input() == null
            }
            focusPhysical(chat)
            api {
                check(!statusCached.isAsciiMode) {
                    "Hardware chat did not restore the mode saved before soft number input"
                }
                check(getRawInput().isEmpty())
            }
            check(main { service.editorModeOverride == null }) {
                "Hardware chat left a stale soft-number mode snapshot"
            }
            main {
                @Suppress("DEPRECATION")
                service.onViewClicked(false)
            }
            until("same hardware chat restores its virtual T9 keyboard") {
                input() != null && keyboard().isT9Layout
            }
            check(!main { restrictedEditorField.getBoolean(keyboardWindow) }) {
                "Virtual chat retained the hidden number editor policy"
            }
            api {
                check(!statusCached.isAsciiMode) {
                    "Virtual chat returned from hardware input with a stale ASCII mode"
                }
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_6)
            until("physical key hides the restored virtual keyboard") { input() == null }
            api {
                check(getRawInput() == "6")
                clearComposition()
                setRuntimeOption("ascii_mode", true)
            }
            main {
                @Suppress("DEPRECATION")
                service.onViewClicked(false)
            }
            until("same hardware chat returns after an explicit hardware mode change") {
                input() != null && keyboard().isT9Layout
            }
            api {
                check(statusCached.isAsciiMode) {
                    "Virtual chat discarded the mode selected during hardware input"
                }
                setRuntimeOption("ascii_mode", false)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_6)
            until("physical key hides the mode-restored virtual keyboard") { input() == null }
            api {
                check(getRawInput() == "6")
                clearComposition()
            }

            for ((name, inputType) in listOf(
                "hardware-password" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
                "hardware-email" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
                "hardware-number" to InputType.TYPE_CLASS_NUMBER,
            )) {
                main {
                    other.hint = name
                    other.inputType = inputType
                    other.setText("")
                }
                focusPhysical(other)
                val expectedPrivate = "password" in name
                check(service.isPrivacyModeActive == expectedPrivate) { "$name privacy state mismatch" }
                check(PrivacyModeState.active == expectedPrivate) { "$name process privacy state mismatch" }
                check(speechAllowed() != expectedPrivate) { "$name speech privacy state mismatch" }
                api { check(Rime.isRimePrivacyGuardEnabledForTesting() == expectedPrivate) }
                api { check(!statusCached.isAsciiMode) { "Hardware test must exercise an unchanged Chinese engine mode" } }
                if (name == "hardware-number") {
                    main { manualPrivacyMode.setValue(true) }
                    until("manual privacy reaches the hardware number editor") {
                        service.isPrivacyModeActive && PrivacyModeState.active
                    }
                    api {
                        check(Rime.isRimePrivacyGuardEnabledForTesting())
                        check(!statusCached.isAsciiMode) {
                            "Privacy rebind applied a hidden virtual-keyboard policy to hardware input"
                        }
                    }
                    main { manualPrivacyMode.setValue(false) }
                    until("manual privacy leaves the hardware number editor") {
                        !service.isPrivacyModeActive && !PrivacyModeState.active
                    }
                    api {
                        check(!Rime.isRimePrivacyGuardEnabledForTesting())
                        check(!statusCached.isAsciiMode) {
                            "Privacy exit left hardware input in a hidden ASCII policy"
                        }
                        setRuntimeOption("ascii_mode", true)
                    }
                    main { manualPrivacyMode.setValue(true) }
                    until("second manual privacy change reaches the hardware number editor") {
                        service.isPrivacyModeActive && PrivacyModeState.active
                    }
                    api {
                        check(Rime.isRimePrivacyGuardEnabledForTesting())
                        check(statusCached.isAsciiMode) {
                            "Privacy rebind overwrote a hardware editor's explicit ASCII mode"
                        }
                    }
                    main { manualPrivacyMode.setValue(false) }
                    until("second manual privacy change leaves the hardware number editor") {
                        !service.isPrivacyModeActive && !PrivacyModeState.active
                    }
                    api {
                        check(!Rime.isRimePrivacyGuardEnabledForTesting())
                        check(statusCached.isAsciiMode) {
                            "Privacy exit overwrote a hardware editor's explicit ASCII mode"
                        }
                        setRuntimeOption("ascii_mode", false)
                    }
                }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_6)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_4)
                until("$name receives physical digits directly") { other.text.toString() == "64" }
                api { check(getRawInput().isEmpty()) { "$name leaked hardware input to Rime" } }
                focusPhysical(chat)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_6)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_4)
                api {
                    check(!statusCached.isAsciiMode && getRawInput() == "64") {
                        "Physical chat did not retain Chinese input after $name"
                    }
                    clearComposition()
                }
            }
        } finally {
            main {
                manualPrivacyMode.setValue(false)
                candidatesMode.setValue(originalCandidatesMode)
                @Suppress("DEPRECATION")
                service.onViewClicked(false)
                chat.requestFocus()
                inputManager.showSoftInput(chat, 0)
            }
        }
        until("virtual chat returns after hardware policy checks") {
            service.currentInputEditorInfo?.fieldId == chat.id &&
                input() != null &&
                keyboard().isT9Layout
        }
        api { check(!statusCached.isAsciiMode) }
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("stream", "PASS: hardware editors preserve and restore native input modes\n") },
        )
    }

    private suspend fun verifyTransientKeyboardAcrossPrivacyChange(
        instrumentation: Instrumentation,
        service: TrimeInputMethodService,
    ) {
        fun <T> main(block: () -> T): T {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun input(): InputView? = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }
        suspend fun until(label: String, predicate: () -> Boolean) {
            withTimeout(30_000) { while (!main(predicate)) delay(50) }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: $label\n") })
        }
        suspend fun api(block: suspend RimeApi.() -> Unit) {
            var outcome: Result<Unit>? = null
            val job = main { service.postRimeJob { outcome = runCatching { block() } } }
            job.join()
            check(!job.isCancelled) { "Engine/editor job cancelled" }
            checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
            main { }
        }

        val manualPrivacyMode = AppPrefs.defaultInstance().general.manualPrivacyMode
        check(!manualPrivacyMode.getValue()) { "Transient keyboard fixture requires ordinary mode" }
        val originalTheme = ThemeManager.prefs.selectedTheme.getValue()
        val keyboardId = KeyboardWindow::class.java.getDeclaredField("currentKeyboardId").apply { isAccessible = true }
        try {
            // These shipped layouts intentionally use different native modes. The oracle
            // must not depend on whichever theme a preceding test or user selected.
            for ((theme, expectedAscii) in listOf("trime" to true, "tongwenfeng.trime" to false)) {
                check(ThemeManager.selectTheme(theme) == theme) { "Transient keyboard theme fell back" }
                until("$theme transient fixture has the current theme's T9") {
                    input()?.let {
                        it.di.direct.instance<Theme>() === ThemeManager.activeTheme &&
                            it.di.direct.instance<KeyboardWindow>().currentKeyboard.isT9Layout
                    } == true
                }
                api {
                    check(getRawInput().isEmpty() && !statusCached.isComposing && !statusCached.isAsciiMode) {
                        "$theme transient fixture requires an idle native T9 editor"
                    }
                }
                val window = main { checkNotNull(input()).di.direct.instance<KeyboardWindow>() }
                try {
                    main { window.switchKeyboard("symbols") }
                    until("$theme manual privacy fixture selects symbols") {
                        keyboardId.get(window) == "symbols" &&
                            !window.currentKeyboard.isLock && !window.currentKeyboard.isT9Layout
                    }
                    val transientKeyboard = main { window.currentKeyboard }
                    check(transientKeyboard.asciiMode == expectedAscii) { "$theme symbols configuration changed" }
                    api {
                        check(statusCached.isAsciiMode == expectedAscii) {
                            "$theme symbols did not establish its configured native mode"
                        }
                    }
                    main { manualPrivacyMode.setValue(true) }
                    until("$theme manual privacy reaches symbols") {
                        service.isPrivacyModeActive && PrivacyModeState.active
                    }
                    api {
                        check(Rime.isRimePrivacyGuardEnabledForTesting())
                        check(statusCached.isAsciiMode == expectedAscii) {
                            "$theme privacy entry changed symbols' native mode"
                        }
                    }
                    check(
                        main {
                            input()?.di?.direct?.instance<KeyboardWindow>() === window &&
                                window.currentKeyboard === transientKeyboard
                        },
                    ) { "$theme privacy entry replaced symbols" }
                    main { manualPrivacyMode.setValue(false) }
                    until("$theme manual privacy leaves symbols") {
                        !service.isPrivacyModeActive && !PrivacyModeState.active
                    }
                    api {
                        check(!Rime.isRimePrivacyGuardEnabledForTesting())
                        check(statusCached.isAsciiMode == expectedAscii) {
                            "$theme privacy exit changed symbols' native mode"
                        }
                    }
                    check(
                        main {
                            input()?.di?.direct?.instance<KeyboardWindow>() === window &&
                                window.currentKeyboard === transientKeyboard
                        },
                    ) { "$theme privacy exit replaced symbols" }
                } finally {
                    main {
                        manualPrivacyMode.setValue(false)
                        window.switchKeyboard(".last_lock")
                    }
                }
                until("$theme manual privacy fixture restores T9") { window.currentKeyboard.isT9Layout }
                api { check(!statusCached.isAsciiMode) }
            }
        } finally {
            main { manualPrivacyMode.setValue(false) }
            ThemeManager.selectTheme(originalTheme)
        }
        until("manual privacy fixture restores the original theme's T9") {
            input()?.let {
                it.di.direct.instance<Theme>() === ThemeManager.activeTheme &&
                    it.di.direct.instance<KeyboardWindow>().currentKeyboard.isT9Layout
            } == true
        }
        api { check(!statusCached.isAsciiMode) }
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("stream", "PASS: manual privacy preserves the user's transient keyboard\n") },
        )
    }

    /** Exercise the real manager with disposable views and observable window callbacks. */
    private fun verifyResidentWindowIdempotency(instrumentation: Instrumentation, editor: Activity) {
        var outcome: Result<Unit>? = null
        instrumentation.runOnMainSync {
            outcome = runCatching {
                val broadcaster = InputBroadcaster()
                val di = DI {
                    bindInstance<ContextThemeWrapper> { ContextThemeWrapper(editor, android.R.style.Theme_DeviceDefault) }
                    bindInstance<InputBroadcaster> { broadcaster }
                }
                val manager = BoardWindowManager(di)
                var activeColor = Color.RED
                val attached = mutableListOf<BoardWindow>()
                val detached = mutableListOf<BoardWindow>()
                broadcaster.addReceiver(object : InputBroadcastReceiver {
                    override fun onWindowAttached(window: BoardWindow) {
                        attached.add(window)
                    }

                    override fun onWindowDetached(window: BoardWindow) {
                        detached.add(window)
                    }
                })
                class CountedWindow(override val key: ResidentWindow.Key) :
                    BoardWindow.NoBarBoardWindow(di),
                    ResidentWindow,
                    InputBroadcastReceiver {
                    var creates = 0
                    var beforeAttaches = 0
                    var attaches = 0
                    var detaches = 0
                    var colorRefreshes = 0
                    var enterTransitions = 0
                    var exitTransitions = 0
                    var inputStarts = 0
                    lateinit var createdView: FrameLayout

                    override fun onCreateView(): View {
                        creates += 1
                        return FrameLayout(context).also { createdView = it }
                    }

                    override fun beforeAttached() {
                        beforeAttaches += 1
                    }

                    override fun refreshColors() {
                        colorRefreshes += 1
                        createdView.setBackgroundColor(activeColor)
                    }

                    override fun onAttached() {
                        check(createdView.parent === manager.view) { "Window callback ran before actual view attachment" }
                        check((createdView.background as ColorDrawable).color == activeColor) { "Window attached before color refresh" }
                        attaches += 1
                    }

                    override fun onDetached() {
                        check(createdView.parent === manager.view) { "Window callback ran after actual view removal" }
                        detaches += 1
                    }

                    override fun enterAnimation(lastWindow: BoardWindow): Transition? {
                        enterTransitions += 1
                        return null
                    }

                    override fun exitAnimation(nextWindow: BoardWindow): Transition? {
                        exitTransitions += 1
                        return null
                    }

                    override fun onStartInput(info: EditorInfo) {
                        inputStarts += 1
                    }

                    fun lifecycleCounts() = listOf(creates, beforeAttaches, attaches, detaches, colorRefreshes, enterTransitions, exitTransitions)
                }

                val firstKey = object : ResidentWindow.Key {}
                val secondKey = object : ResidentWindow.Key {}
                val first = CountedWindow(firstKey)
                val second = CountedWindow(secondKey)
                try {
                    manager.cacheResidentWindow(first, createView = true)
                    val originalView = first.createdView
                    manager.cacheResidentWindow(first)
                    manager.attachWindow(firstKey)
                    check(first.creates == 1 && first.createdView === originalView) { "Repeated registration discarded the cached resident view" }
                    check(manager.view.childCount == 1 && manager.view.getChildAt(0) === originalView && originalView.parent === manager.view)
                    check(first.lifecycleCounts() == listOf(1, 1, 1, 0, 1, 0, 0)) { "Initial resident lifecycle did not execute exactly once" }
                    broadcaster.onStartInput(EditorInfo())
                    check(first.inputStarts == 1) { "Repeated registration changed the resident broadcast subscription" }

                    val beforeDuplicateAttach = first.lifecycleCounts()
                    manager.attachWindow(first)
                    manager.attachWindow(firstKey)
                    manager.cacheResidentWindow(first, createView = true)
                    check(first.lifecycleCounts() == beforeDuplicateAttach) { "Same resident repeated creation, callbacks, color refresh or transitions" }
                    check(attached == listOf(first) && detached.isEmpty()) { "Same resident emitted duplicate window broadcasts" }
                    check(manager.view.childCount == 1 && manager.view.getChildAt(0) === originalView && originalView.parent === manager.view)

                    manager.cacheResidentWindow(second)
                    check(second.creates == 0) { "Lazy registration created its resident view" }
                    manager.attachWindow(secondKey)
                    check(first.detaches == 1 && first.exitTransitions == 1 && second.lifecycleCounts() == listOf(1, 1, 1, 0, 1, 1, 0)) { "Switching resident windows skipped lifecycle or transition callbacks" }
                    check(originalView.parent == null && manager.view.childCount == 1 && manager.view.getChildAt(0) === second.createdView)
                    check(attached == listOf(first, second) && detached == listOf(first))

                    activeColor = Color.BLUE
                    manager.refreshColors()
                    check(second.colorRefreshes == 2 && (second.createdView.background as ColorDrawable).color == activeColor) { "Explicit active-window color refresh was lost" }
                    check(first.colorRefreshes == 1 && (originalView.background as ColorDrawable).color == Color.RED)
                    manager.attachWindow(firstKey)
                    check(first.creates == 1 && first.createdView === originalView && first.beforeAttaches == 2 && first.attaches == 2 && first.colorRefreshes == 2) { "Reattachment failed to reuse and restyle the inactive resident" }
                    check((originalView.background as ColorDrawable).color == activeColor && second.createdView.parent == null)
                    check(first.enterTransitions == 1 && second.exitTransitions == 1 && second.detaches == 1)
                    check(attached == listOf(first, second, first) && detached == listOf(first, second))

                    val replacement = CountedWindow(firstKey)
                    val beforeRejectedRegistration = first.lifecycleCounts()
                    val failure = runCatching { manager.cacheResidentWindow(replacement, createView = true) }.exceptionOrNull()
                    check(failure is IllegalStateException && replacement.creates == 0) { "Occupied resident key accepted a different window instance" }
                    manager.attachWindow(firstKey)
                    check(first.lifecycleCounts() == beforeRejectedRegistration && manager.view.getChildAt(0) === originalView) { "Rejected registration changed the active cached resident" }
                    broadcaster.onStartInput(EditorInfo())
                    check(first.inputStarts == 2 && second.inputStarts == 1 && replacement.inputStarts == 0) { "Rejected registration changed broadcast ownership" }
                    check(attached == listOf(first, second, first) && detached == listOf(first, second))
                } finally {
                    broadcaster.clear()
                    manager.view.removeAllViews()
                }
            }
        }
        checkNotNull(outcome).getOrThrow()
    }

    /** Measure the actual bar branches after the original editor and visibility assertions. */
    private fun verifyInputBarMeasurement(instrumentation: Instrumentation) {
        var outcome: Result<Int>? = null
        instrumentation.runOnMainSync {
            outcome = runCatching {
                val input = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().first { it.isShown }
                val bar = input.di.direct.instance<com.osfans.trime.ime.bar.InputBarDelegate>().view
                check(bar.isShown && bar.width > 0 && bar.height > 0 && bar.childCount == 3) { "Actual input bar was not laid out" }
                val originalBarChild = bar.displayedChild
                val originalBarBounds = Rect(bar.left, bar.top, bar.right, bar.bottom)
                val originalBarWidth = bar.width
                val originalBarHeight = bar.height
                fun measureAndLayout(animator: android.widget.ViewAnimator, width: Int, height: Int) {
                    descendants(animator).forEach { it.forceLayout() }
                    animator.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                    )
                    animator.layout(animator.left, animator.top, animator.left + width, animator.top + height)
                    check(animator.measuredWidth == width && animator.measuredHeight == height) { "Displayed branch changed the exact bar allocation" }
                }
                var exercised = 0
                try {
                    // Expose the existing AlwaysUi before measuring its two actual child animators.
                    bar.displayedChild = 0
                    measureAndLayout(bar, originalBarWidth, originalBarHeight)
                    val always = bar.getChildAt(0) as ViewGroup
                    val innerAnimators = always.children.filterIsInstance<android.widget.ViewAnimator>().toList()
                    check(innerAnimators.size == 2) { "AlwaysUi did not retain both production animators" }
                    val animators = listOf(bar) + innerAnimators
                    check(animators.toSet().size == 3)
                    animators.forEach { animator ->
                        val shouldSkipGone = animator === bar || animator.childCount == 3 ||
                            (animator.layoutParams.width > 0 && animator.layoutParams.height > 0)
                        check(animator.measureAllChildren == !shouldSkipGone) { "Production animator changed its fixed versus flexible size policy" }
                        val originalChild = animator.displayedChild
                        val originalCount = animator.childCount
                        val originalChildren = animator.children.toList()
                        val originalParams = originalChildren.map { it.layoutParams.width to it.layoutParams.height }
                        val width = animator.width
                        val height = animator.height
                        val originalBounds = Rect(animator.left, animator.top, animator.right, animator.bottom)
                        check(width > 0 && height > 0 && originalChild in 0 until originalCount)
                        val innerWidth = width - animator.paddingLeft - animator.paddingRight
                        val innerHeight = height - animator.paddingTop - animator.paddingBottom
                        check(innerWidth > 0 && innerHeight > 0)
                        val witness = object : View(animator.context) {
                            var measures = 0
                            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                                measures += 1
                                setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec))
                            }
                        }.apply {
                            tag = "input-bar-measurement-${UUID.randomUUID()}"
                            visibility = View.GONE
                            isClickable = false
                            isFocusable = false
                        }
                        try {
                            animator.addView(witness, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            check(animator.getChildAt(originalCount) === witness && witness.visibility == View.GONE)
                            repeat(originalCount) { index ->
                                animator.displayedChild = index
                                val beforeGoneMeasure = witness.measures
                                measureAndLayout(animator, width, height)
                                check(if (shouldSkipGone) witness.measures == beforeGoneMeasure else witness.measures > beforeGoneMeasure) { "GONE witness did not follow the production fixed versus flexible size policy" }
                                val selected = originalChildren[index]
                                check(animator.getChildAt(index) === selected && selected.visibility == View.VISIBLE)
                                check(originalChildren.withIndex().all { (otherIndex, child) -> otherIndex == index || child.visibility == View.GONE })
                                check(selected.measuredWidth == innerWidth && selected.measuredHeight == innerHeight) { "Original displayed branch did not fill its existing bar allocation" }
                                check(selected.width == innerWidth && selected.height == innerHeight) { "Original displayed branch lost its laid-out bounds" }
                                check(originalChildren.map { it.layoutParams.width to it.layoutParams.height } == originalParams) { "Displayed branch switching rewrote production layout parameters" }
                                exercised += 1
                            }
                            animator.displayedChild = originalCount
                            measureAndLayout(animator, width, height)
                            check(witness.visibility == View.VISIBLE && witness.measures > 0) { "Visible witness was incorrectly skipped" }
                            check(witness.measuredWidth == innerWidth && witness.measuredHeight == innerHeight && witness.width == innerWidth && witness.height == innerHeight)
                            val visibleMeasures = witness.measures
                            animator.displayedChild = originalChild
                            measureAndLayout(animator, width, height)
                            check(witness.visibility == View.GONE)
                            check(if (shouldSkipGone) witness.measures == visibleMeasures else witness.measures > visibleMeasures) { "Restored production branch changed its GONE measurement policy" }
                        } finally {
                            animator.displayedChild = originalChild
                            animator.removeView(witness)
                            measureAndLayout(animator, width, height)
                            check(animator.childCount == originalCount && animator.children.toList() == originalChildren && witness.parent == null)
                            check(animator.displayedChild == originalChild && Rect(animator.left, animator.top, animator.right, animator.bottom) == originalBounds)
                        }
                    }
                    check(exercised in 7..8) { "Original input-bar branch coverage changed" }
                } finally {
                    bar.displayedChild = originalBarChild
                    measureAndLayout(bar, originalBarWidth, originalBarHeight)
                    check(bar.childCount == 3 && bar.displayedChild == originalBarChild && Rect(bar.left, bar.top, bar.right, bar.bottom) == originalBarBounds)
                    bar.requestLayout()
                }
                exercised
            }
        }
        val branches = checkNotNull(outcome).getOrThrow()
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: actual input-bar animators preserve flexible sizing, skip fixed GONE measurement and retain $branches original displayed branches\n") })
    }

    /** Keep real third-party WRAP_CONTENT sizing without activating a copied theme. */
    private fun verifyFlexibleToolbarMeasurement(instrumentation: Instrumentation) {
        var outcome: Result<Unit>? = null
        instrumentation.runOnMainSync {
            outcome = runCatching {
                val input = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().first { it.isShown }
                val liveTheme = input.di.direct.instance<Theme>()
                val liveScope = input.di.direct.instance<com.osfans.trime.data.theme.ThemeScope>()
                val keyboard = input.di.direct.instance<KeyboardWindow>().currentKeyboard
                val context = input.di.direct.instance<ContextThemeWrapper>()
                val fixedButton = com.osfans.trime.data.theme.model.ToolBar.Button(
                    action = "a",
                    foreground = com.osfans.trime.data.theme.model.ToolBar.Button.Foreground(style = ".", fontSize = 8f, padding = 0),
                    size = listOf(24, 24),
                )
                listOf(listOf(24, 24), listOf(0, -1), listOf(-2, -2)).forEach { size ->
                    val theme = liveTheme.copy(
                        name = "toolbar-measurement-${UUID.randomUUID()}",
                        toolBar = com.osfans.trime.data.theme.model.ToolBar(primaryButton = fixedButton, buttons = listOf(fixedButton.copy(size = size))),
                    )
                    val scope = com.osfans.trime.data.theme.ThemeScope(theme).apply {
                        updateScheme(checkNotNull(liveScope.activeColorScheme))
                    }
                    val ui = com.osfans.trime.ime.bar.ui.AlwaysUi(context, scope, { keyboard })
                    val animator = ui.root.children.filterIsInstance<android.widget.ViewAnimator>().single { it.childCount == 2 }
                    val fixed = animator.layoutParams.width > 0 && animator.layoutParams.height > 0
                    check(animator.measureAllChildren == !fixed) { "Third-party toolbar sizing did not follow its resolved dimensions" }
                    val content = ui.root.children.filterIsInstance<android.widget.ViewAnimator>().single { it.childCount == 3 }
                    check(!content.measureAllChildren)
                    try {
                        if (size == listOf(-2, -2)) {
                            check(animator.layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT && animator.layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT)
                            val width = input.width
                            val height = input.di.direct.instance<com.osfans.trime.ime.bar.InputBarDelegate>().view.height
                            check(width > 0 && height > 0)
                            fun measure() {
                                descendants(ui.root).forEach { it.forceLayout() }
                                ui.root.measure(
                                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                                )
                                ui.root.layout(0, 0, width, height)
                            }
                            measure()
                            val baselineWidth = animator.measuredWidth
                            val originalChild = animator.displayedChild
                            val originalChildren = animator.children.toList()
                            val desiredWidth = minOf(width / 2, baselineWidth + 64)
                            check(desiredWidth > baselineWidth) { "Isolated WRAP_CONTENT fixture lacked measurable spare width" }
                            val witness = object : View(context) {
                                var measures = 0
                                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                                    measures += 1
                                    setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec), resolveSize(1, heightMeasureSpec))
                                }
                            }.apply { tag = "toolbar-wrap-measurement-${UUID.randomUUID()}" }
                            try {
                                animator.addView(witness, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                                witness.visibility = View.GONE
                                var retainedWidth = -1
                                originalChildren.indices.forEach { index ->
                                    animator.displayedChild = index
                                    val before = witness.measures
                                    measure()
                                    check(witness.visibility == View.GONE && witness.measures > before) { "Flexible toolbar stopped measuring its hidden sizing child" }
                                    check(animator.measuredWidth >= desiredWidth && animator.measuredWidth > baselineWidth) { "WRAP_CONTENT toolbar lost the hidden child's size contribution" }
                                    if (retainedWidth < 0) retainedWidth = animator.measuredWidth
                                    check(animator.measuredWidth == retainedWidth) { "Flexible toolbar footprint changed with its displayed button" }
                                }
                            } finally {
                                animator.displayedChild = originalChild
                                animator.removeView(witness)
                                measure()
                                check(witness.parent == null && animator.children.toList() == originalChildren && animator.measuredWidth == baselineWidth)
                            }
                        }
                    } finally {
                        ui.root.removeAllViews()
                    }
                }
                check(input.di.direct.instance<Theme>() === liveTheme && input.di.direct.instance<com.osfans.trime.data.theme.ThemeScope>() === liveScope) { "Isolated toolbar replaced the live theme scope" }
            }
        }
        checkNotNull(outcome).getOrThrow()
    }

    private fun verifyThemeFontDirectoryCache(instrumentation: android.app.Instrumentation) {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
        val context = instrumentation.targetContext
        val selected = com.osfans.trime.data.theme.ThemeManager.prefs.selectedTheme.getValue()
        val originalTheme = com.osfans.trime.data.theme.ThemeManager.activeTheme
        val originalFonts = originalTheme.fonts
        val originalScope = com.osfans.trime.data.theme.ColorManager.currentScope()
        val root = com.osfans.trime.core.data.DataManager.userDataDir.canonicalFile
        val directory = java.io.File(root, "fonts")
        val createdDirectory = !directory.exists()
        if (createdDirectory) check(directory.mkdir())
        check(directory.isDirectory && directory.canonicalFile.parentFile == root)
        val name = "runtime-font-cache-${java.util.UUID.randomUUID()}.ttf"
        val font = java.io.File(directory, name)
        check(!font.exists())
        val isolatedTheme = originalTheme.copy(
            style = originalTheme.style.copy(
                keyFont = com.osfans.trime.data.theme.model.MaybeStringList.Scalar(name),
                symbolFont = com.osfans.trime.data.theme.model.MaybeStringList.Scalar(name),
                latinFont = com.osfans.trime.data.theme.model.MaybeStringList.Empty,
                hanbFont = com.osfans.trime.data.theme.model.MaybeStringList.Empty,
            ),
        )
        val fonts = isolatedTheme.fonts
        try {
            check(fonts.key === android.graphics.Typeface.DEFAULT)
            check(fonts.symbol === android.graphics.Typeface.DEFAULT)
            val resource = com.mikepenz.iconics.typeface.library.community.R.font.community_material_design_icons_font_v7_0_96
            context.resources.openRawResource(resource).use { source ->
                font.outputStream().use { target -> source.copyTo(target) }
            }
            check(font.isFile && font.length() > 0)
            val added = fonts.key
            check(added !== android.graphics.Typeface.DEFAULT)
            check(fonts.key === added && fonts.symbol === added)
            val oldModified = font.lastModified()
            check(font.setLastModified(oldModified + 60_000L))
            check(font.lastModified() != oldModified)
            val changed = fonts.key
            check(changed !== added && changed !== android.graphics.Typeface.DEFAULT)
            check(fonts.key === changed && fonts.symbol === changed)
            check(font.delete())
            check(fonts.key === android.graphics.Typeface.DEFAULT)
            check(fonts.symbol === android.graphics.Typeface.DEFAULT)
            kotlinx.coroutines.runBlocking {
                check(com.osfans.trime.data.theme.ThemeManager.selectTheme(selected) == selected)
                check(com.osfans.trime.data.theme.ThemeManager.activeTheme === originalTheme)
                check(com.osfans.trime.data.theme.ThemeManager.activeTheme.fonts === originalFonts)
                check(com.osfans.trime.data.theme.ColorManager.currentScope() === originalScope)
                check(com.osfans.trime.data.theme.ThemeManager.selectTheme(selected, forceReload = true) == selected)
                val reloadedTheme = com.osfans.trime.data.theme.ThemeManager.activeTheme
                check(reloadedTheme == originalTheme && reloadedTheme !== originalTheme)
                check(reloadedTheme.fonts !== originalFonts)
                check(com.osfans.trime.data.theme.ColorManager.currentScope() !== originalScope)
            }
            instrumentation.sendStatus(
                0,
                android.os.Bundle().apply {
                    putString("stream", "PASS: font directory cache preserves missing/add/change/delete and actual equal/forced theme lifecycle\n")
                },
            )
        } finally {
            // Only the new UUID file may be removed. Never remove pre-existing user fonts.
            if (font.exists()) check(font.isFile && font.delete())
            if (createdDirectory && directory.listFiles()?.isEmpty() == true) check(directory.delete())
        }
    }

    /** Record actual KeyView dispatch with a disposable third-party layout; do not send native input. */
    private fun verifyThirdPartyModifiers(instrumentation: Instrumentation, editor: Activity) {
        var outcome: Result<Unit>? = null
        instrumentation.runOnMainSync {
            outcome = runCatching {
                val input = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().first { it.isShown }
                val service = input.di.direct.instance<TrimeInputMethodService>()
                data class ModifierCase(val token: String, val send: String, val code: Int, val mask: Int)
                val modifiers = listOf(
                    ModifierCase("probe_shift", "Shift_L", KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON),
                    ModifierCase("probe_alt", "Alt_L", KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON),
                    ModifierCase("probe_ctrl", "Control_L", KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON),
                    ModifierCase("probe_meta", "Meta_L", KeyEvent.KEYCODE_META_LEFT, KeyEvent.META_META_ON),
                    ModifierCase("probe_sym", "SYM", KeyEvent.KEYCODE_SYM, KeyEvent.META_SYM_ON),
                )
                val theme = input.di.direct.instance<Theme>().copy(
                    name = "third-party-modifier-probe",
                    presetKeys = modifiers.associate { it.token to PresetKey(send = it.send, label = it.send, functional = true) },
                )
                val layout = TextKeyboard(
                    width = 12.5f,
                    height = 44f,
                    keyboardHeight = 44,
                    keyboardHeightLand = 44,
                    keys = modifiers.map {
                        TextKeyboard.TextKey(click = KeyActionToken.Plain(it.token), longClick = KeyActionToken.Plain(it.token))
                    } + listOf("a", "b").map { TextKeyboard.TextKey(click = KeyActionToken.Plain(it)) },
                )
                val context = ContextThemeWrapper(editor, android.R.style.Theme_DeviceDefault)
                val keyboard = Keyboard(context, theme, 800, layout)
                val broadcaster = InputBroadcaster()
                val di = DI {
                    bindInstance<ContextThemeWrapper> { context }
                    bindInstance<Theme> { theme }
                    bindInstance<TrimeInputMethodService> { service }
                    bindInstance<InputBroadcaster> { broadcaster }
                }
                val delivered = mutableListOf<Pair<Int, Int>>()
                val listener = object : KeyboardActionListener {
                    override fun onPress(keyEventCode: Int) = Unit

                    override fun onAction(action: KeyAction) {
                        check(!action.isModifierKey && action.modifier == 0) { "Modifier key escaped local KeyView handling" }
                        delivered.add(action.code to keyboard.modifier)
                    }

                    override fun onKey(keyEventCode: Int, metaState: Int) = error("Unexpected direct key dispatch")

                    override fun onText(input: String) = error("Unexpected text dispatch")
                }
                val view = KeyboardView(context, theme, keyboard, PopupDelegate(di), service, listener, EnterKeyDisplayDelegate(di))
                val modifierKeys = listOf(keyboard.mShiftKey, keyboard.mAltKey, keyboard.mCtrlKey, keyboard.mMetaKey, keyboard.mSymKey).map(::checkNotNull)
                modifiers.forEachIndexed { index, entry ->
                    check(modifierKeys[index] === keyboard.keys[index]) { "Third-party layout did not register ${entry.send}" }
                    val action = checkNotNull(keyboard.keys[index].click)
                    check(action.code == entry.code && action.isModifierKey && action.modifierKeyOnMask == entry.mask) { "Unexpected platform modifier mapping for ${entry.send}" }
                }
                fun keyView(index: Int) = (view.getChildAt(index) as KeyView).also {
                    check(it.parent === view && it.isEnabled) { "Third-party key lost its actual KeyboardView" }
                }
                fun click(index: Int) {
                    keyView(index).onRelease?.invoke(KeyBehavior.CLICK, false) ?: error("Actual KeyView release callback absent")
                }
                fun lock(index: Int) {
                    val keyView = keyView(index)
                    check(keyView.hasLongPress && !keyView.hasPopup && keyView.performLongClick()) { "Actual modifier long action was not handled" }
                    keyView.onRelease?.invoke(KeyBehavior.LONG_CLICK, true) ?: error("Actual KeyView release callback absent")
                }
                fun input(index: Int, expectedMask: Int) {
                    val before = delivered.size
                    click(index)
                    val expectedCode = if (index == modifiers.size) KeyEvent.KEYCODE_A else KeyEvent.KEYCODE_B
                    check(delivered.size == before + 1 && delivered.last() == (expectedCode to expectedMask)) { "Ordinary KeyView dispatch lost or leaked its modifier state" }
                }
                val allMasks = modifiers.fold(0) { mask, entry -> mask or entry.mask }
                try {
                    modifiers.indices.forEach(::click)
                    check(delivered.isEmpty() && keyboard.modifier == allMasks && modifierKeys.none { it.isOn }) { "Temporary modifier clicks changed lock state or dispatched input" }
                    check(!keyboard.isOnlyShiftOn) { "Combined modifiers were classified as Shift alone" }
                    input(modifiers.size, allMasks)
                    check(keyboard.modifier == 0 && modifierKeys.none { it.isOn }) { "A single ordinary key did not consume every temporary modifier" }
                    input(modifiers.size + 1, 0)

                    modifiers.forEachIndexed { index, entry ->
                        click(index)
                        check(keyboard.modifier == entry.mask && !modifierKeys[index].isOn) { "Temporary ${entry.send} acquired a lock" }
                        input(modifiers.size, entry.mask)
                        check(keyboard.modifier == 0) { "Temporary ${entry.send} leaked to subsequent input" }
                        input(modifiers.size + 1, 0)

                        val beforeLock = delivered.size
                        lock(index)
                        check(delivered.size == beforeLock && keyboard.modifier == entry.mask && modifierKeys[index].isOn) { "Long action did not lock actual ${entry.send}" }
                        input(modifiers.size, entry.mask)
                        input(modifiers.size + 1, entry.mask)
                        check(keyboard.modifier == entry.mask && modifierKeys[index].isOn) { "Ordinary input consumed locked ${entry.send}" }
                        lock(index)
                        check(delivered.size == beforeLock + 2 && keyboard.modifier == 0 && !modifierKeys[index].isOn) { "Repeating the long action did not unlock ${entry.send}" }
                        input(modifiers.size, 0)
                    }

                    modifiers.indices.drop(1).forEach { index ->
                        click(0)
                        check(keyboard.isOnlyShiftOn) { "Shift alone lost its special handling" }
                        click(index)
                        check(!keyboard.isOnlyShiftOn) { "Shift plus ${modifiers[index].send} was classified as Shift alone" }
                        input(modifiers.size, KeyEvent.META_SHIFT_ON or modifiers[index].mask)
                        check(keyboard.modifier == 0) { "A two-modifier chord retained a temporary modifier" }
                        input(modifiers.size + 1, 0)
                    }

                    lock(0)
                    modifiers.indices.drop(1).forEach(::click)
                    input(modifiers.size, allMasks)
                    check(keyboard.modifier == KeyEvent.META_SHIFT_ON && modifierKeys[0].isOn && modifierKeys.drop(1).none { it.isOn }) { "Clearing temporary modifiers changed the existing Shift lock" }
                    check(keyboard.isOnlyShiftOn)
                    input(modifiers.size + 1, KeyEvent.META_SHIFT_ON)
                    lock(0)
                    input(modifiers.size, 0)
                    check(delivered.size == 38 && keyboard.modifier == 0 && modifierKeys.none { it.isOn }) { "Third-party modifier exercise left persistent state" }
                    check(!keyboard.refreshModifier()) { "Unchanged modifiers requested a redundant refresh" }

                    // Exercise the actual gesture detector separately; retain the original 38 deliveries.
                    val shiftPrefs = AppPrefs.defaultInstance().keyboard
                    val temporaryPrefs = listOf(shiftPrefs.hookShiftLock, shiftPrefs.doubleTapTimeout, shiftPrefs.popupOnKeyPress)
                    val preferenceStore = shiftPrefs.hookShiftLock.sharedPreferences
                    check(temporaryPrefs.all { it.sharedPreferences === preferenceStore })
                    val savedPreferences = temporaryPrefs.associate { it.key to (preferenceStore.contains(it.key) to preferenceStore.all[it.key]) }
                    val originalToken = service.editorToken
                    val originalPrivacy = service.isPrivacyModeActive
                    val languagePreference = shiftPrefs.manualLanguageMode
                    val originalLanguage = languagePreference.sharedPreferences.contains(languagePreference.key) to languagePreference.sharedPreferences.all[languagePreference.key]
                    val actualEditors = descendants(editor.window.decorView).filterIsInstance<EditText>()
                        .map { it to Triple(it.text.toString(), it.selectionStart, it.selectionEnd) }.toList()
                    try {
                        shiftPrefs.doubleTapTimeout.setValue(1000)
                        shiftPrefs.popupOnKeyPress.setValue(false)
                        fun gestureCase(
                            shiftLock: String = "",
                            doubleAction: String? = null,
                            lazyAction: String? = null,
                            exercise: (Keyboard, KeyboardView, MutableList<Pair<Int, Int>>) -> Unit,
                        ) {
                            val gestureTheme = theme.copy(
                                presetKeys = theme.presetKeys + mapOf(
                                    "probe_shift" to PresetKey(send = "Shift_L", label = "Shift", shiftLock = shiftLock, functional = true),
                                    "probe_explicit_shift" to PresetKey(send = "Shift_L", label = "Shift", shiftLock = "click", functional = true),
                                    "probe_explicit_alt" to PresetKey(send = "Alt_L", label = "Alt", functional = true),
                                ),
                            )
                            val gestureLayout = layout.copy(
                                keys = listOf(
                                    TextKeyboard.TextKey(
                                        click = KeyActionToken.Plain("probe_shift"),
                                        longClick = KeyActionToken.Plain("probe_shift"),
                                        doubleClick = doubleAction?.let { KeyActionToken.Plain(it) },
                                        lazyDoubleClick = lazyAction?.let { KeyActionToken.Plain(it) },
                                    ),
                                ) + listOf("a", "b").map { TextKeyboard.TextKey(click = KeyActionToken.Plain(it)) } +
                                    TextKeyboard.TextKey(click = KeyActionToken.Plain("probe_alt")),
                            )
                            val gestureKeyboard = Keyboard(context, gestureTheme, 800, gestureLayout)
                            val gestureBroadcaster = InputBroadcaster()
                            val gestureDi = DI {
                                bindInstance<ContextThemeWrapper> { context }
                                bindInstance<Theme> { gestureTheme }
                                bindInstance<TrimeInputMethodService> { service }
                                bindInstance<InputBroadcaster> { gestureBroadcaster }
                            }
                            val gestures = mutableListOf<Pair<Int, Int>>()
                            val gestureListener = object : KeyboardActionListener {
                                override fun onPress(keyEventCode: Int) = Unit
                                override fun onAction(action: KeyAction) {
                                    check(!action.isModifierKey && action.modifier == 0)
                                    gestures.add(action.code to gestureKeyboard.modifier)
                                }
                                override fun onKey(keyEventCode: Int, metaState: Int) = error("Gesture probe sent native key input")
                                override fun onText(input: String) = error("Gesture probe sent native text input")
                            }
                            val gestureView = KeyboardView(context, gestureTheme, gestureKeyboard, PopupDelegate(gestureDi), service, gestureListener, EnterKeyDisplayDelegate(gestureDi))
                            gestureView.setViewTreeLifecycleOwner(checkNotNull(input.findViewTreeLifecycleOwner()))
                            gestureView.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            gestureView.layout(0, 0, gestureView.measuredWidth, gestureView.measuredHeight)
                            try {
                                exercise(gestureKeyboard, gestureView, gestures)
                            } finally {
                                gestureView.clearTransientContent()
                                gestureView.removeAllViews()
                                gestureBroadcaster.clear()
                            }
                        }
                        fun tap(gestureView: KeyboardView, index: Int) {
                            val target = gestureView.getChildAt(index) as KeyView
                            check(target.width > 0 && target.height > 0 && target.isEnabled)
                            val down = SystemClock.uptimeMillis()
                            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, target.width / 2f, target.height / 2f, 0)
                                event.source = InputDevice.SOURCE_TOUCHSCREEN
                                try {
                                    check(target.dispatchTouchEvent(event)) { "Actual Shift gesture was not accepted" }
                                } finally {
                                    event.recycle()
                                }
                            }
                        }
                        shiftPrefs.hookShiftLock.setValue(true)
                        for (lockMode in listOf("", "click", "ascii_long")) {
                            gestureCase(shiftLock = lockMode) { actualKeyboard, actualView, gestures ->
                                val firstLocks = checkNotNull(actualKeyboard.keys[0].click).isShiftLock
                                tap(actualView, 0)
                                check(actualKeyboard.modifier == KeyEvent.META_SHIFT_ON && actualKeyboard.mShiftKey?.isOn == firstLocks)
                                tap(actualView, 0)
                                check(actualKeyboard.modifier == KeyEvent.META_SHIFT_ON && actualKeyboard.mShiftKey?.isOn == true) { "Double Shift did not retain a lock, including an already locked first tap" }
                                tap(actualView, 1)
                                tap(actualView, 2)
                                check(gestures == listOf(KeyEvent.KEYCODE_A to KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_B to KeyEvent.META_SHIFT_ON))
                                check(actualKeyboard.mShiftKey?.isOn == true && actualKeyboard.modifier == KeyEvent.META_SHIFT_ON)
                                tap(actualView, 0)
                                check(actualKeyboard.mShiftKey?.isOn == false && actualKeyboard.modifier == 0) { "A single Shift did not release the lock" }
                                tap(actualView, 1)
                                check(gestures.last() == (KeyEvent.KEYCODE_A to 0))
                            }
                        }
                        // The same cached View must read the live setting; neither disabled tap is swallowed.
                        gestureCase { actualKeyboard, actualView, gestures ->
                            shiftPrefs.hookShiftLock.setValue(false)
                            tap(actualView, 0)
                            check(actualKeyboard.modifier == KeyEvent.META_SHIFT_ON && actualKeyboard.mShiftKey?.isOn == false)
                            tap(actualView, 0)
                            check(actualKeyboard.modifier == 0 && actualKeyboard.mShiftKey?.isOn == false)
                            shiftPrefs.hookShiftLock.setValue(true)
                            tap(actualView, 0)
                            tap(actualView, 0)
                            check(actualKeyboard.modifier == KeyEvent.META_SHIFT_ON && actualKeyboard.mShiftKey?.isOn == true)
                            shiftPrefs.hookShiftLock.setValue(false)
                            tap(actualView, 0)
                            tap(actualView, 0)
                            check(actualKeyboard.modifier == KeyEvent.META_SHIFT_ON && actualKeyboard.mShiftKey?.isOn == false)
                            tap(actualView, 1)
                            check(gestures == listOf(KeyEvent.KEYCODE_A to KeyEvent.META_SHIFT_ON) && actualKeyboard.modifier == 0)
                        }
                        shiftPrefs.hookShiftLock.setValue(true)
                        gestureCase(shiftLock = "click", doubleAction = "probe_explicit_shift") { actualKeyboard, actualView, gestures ->
                            tap(actualView, 0)
                            check(actualKeyboard.mShiftKey?.isOn == true)
                            tap(actualView, 0)
                            check(actualKeyboard.mShiftKey?.isOn == false && actualKeyboard.modifier == 0 && gestures.isEmpty()) { "Built-in Shift lock overrode the explicit theme double toggle" }
                        }
                        gestureCase(doubleAction = "probe_explicit_alt") { actualKeyboard, actualView, gestures ->
                            tap(actualView, 0)
                            tap(actualView, 0)
                            check(actualKeyboard.modifier == (KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON) && actualKeyboard.mShiftKey?.isOn == false)
                            tap(actualView, 1)
                            check(gestures == listOf(KeyEvent.KEYCODE_A to (KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON)) && actualKeyboard.modifier == 0)
                        }
                        gestureCase(lazyAction = "b") { actualKeyboard, actualView, gestures ->
                            val shiftView = actualView.getChildAt(0) as KeyView
                            check(!shiftView.hasDouble && shiftView.hasLazyDouble)
                            tap(actualView, 0)
                            check(actualKeyboard.modifier == 0 && gestures.isEmpty())
                            tap(actualView, 0)
                            check(actualKeyboard.modifier == 0 && actualKeyboard.mShiftKey?.isOn == false && gestures == listOf(KeyEvent.KEYCODE_B to 0)) { "Built-in Shift lock overrode explicit lazy double" }
                        }
                    } finally {
                        val restore = preferenceStore.edit()
                        for ((key, saved) in savedPreferences) {
                            if (!saved.first) {
                                restore.remove(key)
                            } else {
                                when (val raw = saved.second) {
                                    is Boolean -> restore.putBoolean(key, raw)

                                    is Int -> restore.putInt(key, raw)

                                    is Long -> restore.putLong(key, raw)

                                    is Float -> restore.putFloat(key, raw)

                                    is String -> restore.putString(key, raw)

                                    is Set<*> -> {
                                        check(raw.all { it is String })
                                        restore.putStringSet(key, raw.map { it as String }.toSet())
                                    }

                                    else -> error("Unexpected stored Shift preference type")
                                }
                            }
                        }
                        check(restore.commit()) { "Shift gesture probe could not restore preferences" }
                        savedPreferences.forEach { (key, saved) -> check((preferenceStore.contains(key) to preferenceStore.all[key]) == saved) }
                        check(service.editorToken == originalToken && service.isPrivacyModeActive == originalPrivacy)
                        check((languagePreference.sharedPreferences.contains(languagePreference.key) to languagePreference.sharedPreferences.all[languagePreference.key]) == originalLanguage)
                        actualEditors.forEach { (field, saved) -> check(Triple(field.text.toString(), field.selectionStart, field.selectionEnd) == saved) }
                    }
                    check(delivered.size == 38 && keyboard.modifier == 0 && modifierKeys.none { it.isOn })
                } finally {
                    view.clearTransientContent()
                    view.removeAllViews()
                    broadcaster.clear()
                }
            }
        }
        checkNotNull(outcome).getOrThrow()
    }

    fun run(instrumentation: Instrumentation, observeImeAnimations: Boolean = true) {
        val context = instrumentation.targetContext
        val result = Bundle()
        var activity: Activity? = null
        var session: RimeSession? = null
        var web: WebView? = null
        var passed = false
        val originalTheme = ThemeManager.prefs.selectedTheme.getValue()
        val candidatesMode = AppPrefs.defaultInstance().candidates.mode
        val originalCandidatesMode = candidatesMode.getValue()
        val manualPrivacyMode = AppPrefs.defaultInstance().general.manualPrivacyMode
        val originalManualPrivacyMode = manualPrivacyMode.getValue()
        val manualLanguageMode = AppPrefs.defaultInstance().keyboard.manualLanguageMode
        val manualLanguageStore = manualLanguageMode.sharedPreferences
        val manualLanguageKey = manualLanguageMode.key
        val originalManualLanguagePresent = manualLanguageStore.contains(manualLanguageKey)
        val originalManualLanguageRaw = if (originalManualLanguagePresent) manualLanguageStore.getString(manualLanguageKey, null) else null
        val originalManualLanguageMode = manualLanguageMode.getValue()
        var manualLanguageBaselineStarted = false
        var originalSchema = ""
        var originalAscii = false
        var fixture: File? = null
        val touchRegionRunId = UUID.randomUUID().toString()
        val touchRegionMeasurements = JSONArray()
        val touchRegionLifecycleChecks = JSONArray()
        val imeVisibilityWaits = JSONArray()
        val imeAnimations = ImeAnimationProbe(observeImeAnimations)
        val webFixtureEnvironment = JSONObject().apply { put("captured", false) }
        fun touchStage(stage: String, theme: String, round: Int = -1, elapsedMs: Long = -1) {
            val marker = "TOUCH_REGION run=$touchRegionRunId stage=$stage theme=$theme round=$round elapsed_ms=$elapsedMs uptime_ms=${SystemClock.uptimeMillis()}"
            Log.i("TrimeProbe", marker)
            instrumentation.sendStatus(0, Bundle().apply { putString("touch_region_phase", marker) })
        }
        fun <T> main(block: () -> T): T {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun destroyWebFixture(required: Boolean = false) = main {
            if (required) checkNotNull(web)
            val view = web ?: return@main
            val cleanupStarted = SystemClock.elapsedRealtimeNanos()
            val cleanupIdentity = System.identityHashCode(view)
            Log.i(
                "TrimeProbe",
                "WEBVIEW_CLEANUP run=$touchRegionRunId stage=remove_destroy_start monotonic_ns=$cleanupStarted " +
                    "pid=${android.os.Process.myPid()} web_identity=$cleanupIdentity",
            )
            // WebView requires detaching before destroy, including cleanup after
            // an assertion or timeout, when the normal teardown was never reached.
            (view.parent as? ViewGroup)?.removeView(view)
            check(view.parent == null) { "WebView fixture must be detached before destroy" }
            view.destroy()
            web = null
            val cleanupFinished = SystemClock.elapsedRealtimeNanos()
            Log.i(
                "TrimeProbe",
                "WEBVIEW_CLEANUP run=$touchRegionRunId stage=remove_destroy_end monotonic_ns=$cleanupFinished " +
                    "duration_ns=${cleanupFinished - cleanupStarted} pid=${android.os.Process.myPid()} web_identity=$cleanupIdentity",
            )
        }
        fun phase(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$text\n") })
        fun restoreManualLanguage() {
            if (!manualLanguageBaselineStarted) return
            val edit = manualLanguageStore.edit()
            if (originalManualLanguagePresent) {
                edit.putString(manualLanguageKey, checkNotNull(originalManualLanguageRaw))
            } else {
                edit.remove(manualLanguageKey)
            }
            check(edit.commit()) { "Manual language restoration did not persist" }
            check(
                manualLanguageStore.contains(manualLanguageKey) == originalManualLanguagePresent &&
                    (!originalManualLanguagePresent || manualLanguageStore.getString(manualLanguageKey, null) == originalManualLanguageRaw) &&
                    manualLanguageMode.getValue() == originalManualLanguageMode,
            ) { "Manual language original presence, raw value or typed mode did not restore" }
        }
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            check(File(context.getExternalFilesDir(null), "runtime-audit-dedicated").isFile) { "Requires a dedicated runtime-audit installation" }
            main {
                check(!originalManualLanguagePresent || originalManualLanguageRaw in setOf("unset", "native", "ascii")) { "Unexpected manual language wire value" }
                // This legacy matrix changes native ASCII directly; persistent user choices are
                // covered by MixedInputProbe. Each new editor reloads this isolated UNSET mode.
                manualLanguageBaselineStarted = true
                check(manualLanguageStore.edit().remove(manualLanguageKey).commit()) { "Manual language baseline did not persist" }
                check(!manualLanguageStore.contains(manualLanguageKey) && manualLanguageMode.getValue() == ManualLanguageMode.UNSET)
                manualPrivacyMode.setValue(false)
            }
            session = main { RimeDaemon.createSession(javaClass.name) }
            runBlocking {
                withTimeout(300_000) {
                    checkNotNull(session).runOnReady {
                        originalSchema = selectedSchemaId()
                        originalAscii = statusCached.isAsciiMode
                    }
                }
                ThemeManager.init(context.resources.configuration)
            }
            val editor = instrumentation.startActivitySync(Intent(context, EditorProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = editor
            main { imeAnimations.install(editor.window.decorView) }
            val actions = mutableListOf<Int>()
            val fields = main {
                val row = LinearLayout(editor).apply { orientation = LinearLayout.VERTICAL }
                // The fixture owns these editors in the same PID as the IME. Keep Android's
                // platform SpellChecker out of the runtime log while preserving the exact
                // EditorInfo inputType values under test.
                val chat = object : EditText(editor) {
                    override fun isSuggestionsEnabled(): Boolean = false
                }.apply {
                    id = View.generateViewId()
                    hint = "chat"
                    inputType = InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_SEND
                }
                val other = object : EditText(editor) {
                    override fun isSuggestionsEnabled(): Boolean = false
                }.apply {
                    id = View.generateViewId()
                    hint = "target"
                }
                chat.setOnEditorActionListener { _, id, _ ->
                    actions.add(id)
                    false
                }
                other.setOnEditorActionListener { _, id, _ ->
                    actions.add(id)
                    id != 0
                }
                row.addView(chat, LinearLayout.LayoutParams(-1, 72))
                row.addView(other, LinearLayout.LayoutParams(-1, 72))
                editor.setContentView(row)
                // The debug-only, non-floating host keeps a real application surface beside
                // the preedit. adjustNothing leaves that surface available beneath the IME.
                editor.window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                chat.requestFocus()
                Triple(chat, other, row)
            }
            val (chat, other, container) = fields
            runBlocking {
                withTimeout(900_000) {
                    suspend fun until(label: String, predicate: () -> Boolean) {
                        withTimeout(30_000) { while (!main(predicate)) delay(50) }
                        phase("PASS: $label")
                    }
                    fun input(): InputView? = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }
                    val inputManager = editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    // Window focus and the served editor are established after setContentView.
                    // On API 21 this first shows LatinIME, before the runner switches to Trime.
                    until("initial editor attached, laid out and served") {
                        chat.isAttachedToWindow && chat.isLaidOut && chat.width > 0 && chat.height > 0 &&
                            chat.hasWindowFocus() && chat.hasFocus() && inputManager.isActive(chat)
                    }
                    if (Build.VERSION.SDK_INT >= 35) {
                        until("initial editor is physically outside the system bars") {
                            val decor = editor.window.decorView
                            val insets = ViewCompat.getRootWindowInsets(decor)
                            if (insets == null) {
                                false
                            } else {
                                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                                val origin = IntArray(2).also(decor::getLocationOnScreen)
                                val position = IntArray(2).also(chat::getLocationOnScreen)
                                position[0] >= origin[0] + bars.left && position[1] >= origin[1] + bars.top &&
                                    position[0] + chat.width <= origin[0] + decor.width - bars.right &&
                                    position[1] + chat.height <= origin[1] + decor.height - bars.bottom
                            }
                        }
                    }
                    main { inputManager.showSoftInput(chat, 0) }
                    until("initial keyboard visible") { input() != null }
                    val service = main { checkNotNull(input()).di.direct.instance<TrimeInputMethodService>() }
                    suspend fun api(block: suspend RimeApi.() -> Unit) {
                        var outcome: Result<Unit>? = null
                        val job = main { service.postRimeJob { outcome = runCatching { block() } } }
                        job.join()
                        check(!job.isCancelled) { "Engine/editor job cancelled" }
                        checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
                        main { }
                    }
                    fun keyboard() = checkNotNull(input()).di.direct.instance<KeyboardWindow>().currentKeyboard
                    fun type(value: String) = main { checkNotNull(input()).di.direct.instance<CommonKeyboardActionListener>().listener.onText(value) }
                    fun enter() = main { checkNotNull(input()).di.direct.instance<CommonKeyboardActionListener>().listener.onAction(KeyAction(KeyActionToken.Plain("Return"), ThemeManager.activeTheme.presetKeys)) }
                    fun speechAllowed(): Boolean = InputFeedbackManager::class.java
                        .getDeclaredField("speechAllowed")
                        .apply { isAccessible = true }
                        .getBoolean(InputFeedbackManager)
                    suspend fun focus(field: EditText) {
                        main {
                            field.requestFocus()
                            (editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, 0)
                        }
                        until("editor ${field.hint} owns connection") { service.currentInputEditorInfo?.fieldId == field.id && input() != null }
                        api { }
                    }
                    api { clearComposition() }
                    EngineUnavailableGlyphProbe.verify(instrumentation, service, chat)
                    PopupColorRegression.verify(instrumentation, main { checkNotNull(input()) }, chat)
                    api {
                        check(selectSchema("luna_pinyin_t9"))
                        setRuntimeOption("ascii_mode", false)
                    }
                    until("selected T9 is actually a nine-key layout") { input() != null && keyboard().isT9Layout }
                    fun touchInsets() = InputMethodService.Insets().also(service::onComputeInsets)
                    fun visibleWindowBounds(view: View): Rect {
                        val bounds = Rect()
                        if (!view.isAttachedToWindow || !view.isShown || !view.getLocalVisibleRect(bounds)) return bounds.apply { setEmpty() }
                        val location = IntArray(2)
                        view.getLocationInWindow(location)
                        bounds.offset(location[0] - view.scrollX, location[1] - view.scrollY)
                        return bounds
                    }
                    fun regionCovers(view: View): Boolean {
                        val bounds = visibleWindowBounds(view)
                        return !bounds.isEmpty && Region(bounds).apply {
                            op(touchInsets().touchableRegion, Region.Op.DIFFERENCE)
                        }.isEmpty
                    }
                    fun checkNoTouchPopup() {
                        check(
                            roots().none { root ->
                                (root.layoutParams as? WindowManager.LayoutParams)?.title?.toString()?.startsWith("PopupWindow:") == true
                            },
                        ) { "Composition must not create a separate PopupWindow surface" }
                    }
                    fun fingerEvent(down: Long, action: Int, x: Float, y: Float): MotionEvent {
                        val pointer = MotionEvent.PointerProperties().apply {
                            id = 0
                            toolType = MotionEvent.TOOL_TYPE_FINGER
                        }
                        val coordinates = MotionEvent.PointerCoords().apply {
                            this.x = x
                            this.y = y
                            pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                            size = 1f
                        }
                        // setSource alone leaves the simplified obtain overload's tool type
                        // UNKNOWN. Android's editor-tool callback needs an actual finger tap.
                        return MotionEvent.obtain(
                            down, SystemClock.uptimeMillis(), action, 1,
                            arrayOf(pointer), arrayOf(coordinates), 0, 0,
                            1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
                        )
                    }
                    fun tapActualView(view: View, leadingText: Boolean = false, imeTarget: Boolean = true) {
                        val point = main {
                            if (imeTarget) check(regionCovers(view))
                            val location = IntArray(2)
                            val bounds = Rect()
                            check(view.getLocalVisibleRect(bounds))
                            bounds.offset(-view.scrollX, -view.scrollY)
                            view.getLocationOnScreen(location)
                            val x = if (leadingText) view.paddingLeft + 1 else bounds.centerX()
                            val y = bounds.centerY()
                            check(bounds.contains(x, y))
                            if (!imeTarget) {
                                val decor = checkNotNull(service.window.window).decorView
                                val decorScreen = IntArray(2).also(decor::getLocationOnScreen)
                                val decorWindow = IntArray(2).also(decor::getLocationInWindow)
                                check(
                                    !touchInsets().touchableRegion.contains(
                                        location[0] + x - decorScreen[0] + decorWindow[0],
                                        location[1] + y - decorScreen[1] + decorWindow[1],
                                    ),
                                ) { "Host pointer target overlaps the IME touch region" }
                            }
                            (location[0] + x).toFloat() to (location[1] + y).toFloat()
                        }
                        val down = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event = fingerEvent(down, action, point.first, point.second)
                            try {
                                check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "Actual pointer injection was rejected" }
                            } finally {
                                event.recycle()
                            }
                        }
                    }
                    suspend fun verifyTouchRegion(theme: String, committedBefore: String) {
                        val iv = main { checkNotNull(input()) }
                        val preedit = main { iv.di.direct.instance<PreeditDelegate>().ui }
                        val root = preedit.root
                        val pendingBefore = main { chat.text.toString() }
                        val before = committedBefore
                        touchStage("before_region_lifecycle", theme)
                        main {
                            val insets = touchInsets()
                            val keyboardPosition = IntArray(2).also(iv.keyboardView::getLocationInWindow)
                            check(insets.contentTopInsets == keyboardPosition[1] && insets.visibleTopInsets == keyboardPosition[1])
                            check(insets.touchableInsets == InputMethodService.Insets.TOUCHABLE_INSETS_REGION)
                            check(regionCovers(root))
                            val bounds = visibleWindowBounds(root)
                            check(visibleWindowBounds(iv).contains(bounds)) { "Preedit must be reachable through its actual InputView ancestor" }
                            val decor = checkNotNull(service.window.window).decorView
                            check(bounds.right < decor.width - 1)
                            check(!insets.touchableRegion.contains(decor.width - 1, bounds.centerY())) { "Blank app space beside preedit must remain touch-through" }
                            checkNoTouchPopup()
                        }
                        suspend fun verifyHostGapPointer() {
                            val hostPointerActions = mutableListOf<Int>()
                            val hostContent = main { editor.findViewById<FrameLayout>(android.R.id.content) }
                            fun hostGapGeometry(): Pair<Rect, String> {
                                val decor = checkNotNull(service.window.window).decorView
                                val preeditBounds = visibleWindowBounds(root)
                                val decorScreen = IntArray(2).also(decor::getLocationOnScreen)
                                val decorWindow = IntArray(2).also(decor::getLocationInWindow)
                                val hostScreen = IntArray(2).also(hostContent::getLocationOnScreen)
                                val hostVisible = Rect()
                                val hostIsVisible = hostContent.isShown && hostContent.getLocalVisibleRect(hostVisible)
                                hostVisible.offset(hostScreen[0] - hostContent.scrollX, hostScreen[1] - hostContent.scrollY)
                                val windowScreenX = decorScreen[0] - decorWindow[0]
                                val windowScreenY = decorScreen[1] - decorWindow[1]
                                val preeditScreen = Rect(preeditBounds).apply { offset(windowScreenX, windowScreenY) }
                                val available = Rect(preeditScreen.right, preeditScreen.top, windowScreenX + decor.width, preeditScreen.bottom)
                                val targetScreen = Rect()
                                val targetWindow = Rect()
                                val insets = touchInsets()
                                if (!preeditBounds.isEmpty && hostIsVisible && available.intersect(hostVisible) && available.width() >= 32 && available.height() >= 32) {
                                    targetScreen.set(available.centerX() - 16, available.centerY() - 16, available.centerX() + 16, available.centerY() + 16)
                                    targetWindow.set(targetScreen)
                                    targetWindow.offset(-windowScreenX, -windowScreenY)
                                    if (!Region(targetWindow).apply { op(insets.touchableRegion, Region.Op.INTERSECT) }.isEmpty) targetScreen.setEmpty()
                                }
                                return targetScreen to "hostVisible=$hostVisible hostScreen=${hostScreen.contentToString()} " +
                                    "hostSize=${hostContent.width}x${hostContent.height} hostScroll=${hostContent.scrollX},${hostContent.scrollY} " +
                                    "hostPadding=${hostContent.paddingLeft},${hostContent.paddingTop},${hostContent.paddingRight},${hostContent.paddingBottom} " +
                                    "decorScreen=${decorScreen.contentToString()} decorWindow=${decorWindow.contentToString()} decorSize=${decor.width}x${decor.height} " +
                                    "preeditWindow=$preeditBounds preeditScreen=$preeditScreen intersection=$available targetScreen=$targetScreen targetWindow=$targetWindow " +
                                    "touchRegion=${insets.touchableRegion}"
                            }
                            var hostGeometryDiagnostic = "Host geometry has not been sampled"
                            try {
                                until("$theme actual host content can fit a pointer target beside preedit") {
                                    val (target, diagnostic) = hostGapGeometry()
                                    hostGeometryDiagnostic = diagnostic
                                    !target.isEmpty
                                }
                            } catch (failure: TimeoutCancellationException) {
                                throw IllegalStateException("$theme host pointer target geometry did not become reachable: $hostGeometryDiagnostic", failure)
                            }
                            var expectedHostTargetScreen = Rect()
                            val hostTarget = main {
                                val (targetScreen, diagnostic) = hostGapGeometry()
                                check(!targetScreen.isEmpty) { "$theme host pointer target geometry changed: $diagnostic" }
                                expectedHostTargetScreen = Rect(targetScreen)
                                val hostScreen = IntArray(2).also(hostContent::getLocationOnScreen)
                                val left = targetScreen.left - hostScreen[0] + hostContent.scrollX - hostContent.paddingLeft
                                val top = targetScreen.top - hostScreen[1] + hostContent.scrollY - hostContent.paddingTop
                                check(left >= 0 && top >= 0 && left + 32 <= hostContent.width && top + 32 <= hostContent.height) {
                                    "$theme host pointer local placement is outside content: left=$left top=$top $diagnostic"
                                }
                                phase("HOST_POINTER_GEOMETRY: $theme left=$left top=$top $diagnostic")
                                object : EditText(editor) {
                                    @SuppressLint("ClickableViewAccessibility")
                                    override fun onTouchEvent(event: MotionEvent): Boolean {
                                        hostPointerActions.add(event.actionMasked)
                                        return true
                                    }
                                }.apply {
                                    minimumHeight = 0
                                    minimumWidth = 0
                                    setPadding(0, 0, 0, 0)
                                    isFocusable = false
                                    hostContent.addView(
                                        this,
                                        FrameLayout.LayoutParams(32, 32).apply {
                                            leftMargin = left
                                            topMargin = top
                                        },
                                    )
                                }
                            }
                            try {
                                until("$theme host editor target is visible in the empty preedit gap") { hostTarget.isShown && hostTarget.isLaidOut }
                                delay(150)
                                touchStage("before_host_gap_pointer", theme)
                                main {
                                    val actualScreen = Rect()
                                    check(hostTarget.getLocalVisibleRect(actualScreen)) { "$theme host pointer target is clipped: ${hostGapGeometry().second}" }
                                    val targetLocalVisible = Rect(actualScreen)
                                    val targetLocation = IntArray(2).also(hostTarget::getLocationOnScreen)
                                    actualScreen.offset(targetLocation[0] - hostTarget.scrollX, targetLocation[1] - hostTarget.scrollY)
                                    check(actualScreen == expectedHostTargetScreen) {
                                        "$theme host pointer target moved or was clipped: expected=$expectedHostTargetScreen actual=$actualScreen " +
                                            "localVisible=$targetLocalVisible location=${targetLocation.contentToString()} scroll=${hostTarget.scrollX},${hostTarget.scrollY} ${hostGapGeometry().second}"
                                    }
                                    phase("HOST_POINTER_ACTUAL: $theme screen=$actualScreen localVisible=$targetLocalVisible location=${targetLocation.contentToString()} scroll=${hostTarget.scrollX},${hostTarget.scrollY}")
                                }
                                tapActualView(hostTarget, imeTarget = false)
                                check(
                                    main {
                                        hostPointerActions.filter { it == MotionEvent.ACTION_DOWN || it == MotionEvent.ACTION_UP } ==
                                            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)
                                    },
                                ) { "Empty space beside preedit intercepted the host editor pointer" }
                                touchStage("after_host_gap_pointer", theme)
                                phase("PASS: $theme empty preedit gap routes a real pointer to the host editor")
                            } finally {
                                main { (hostTarget.parent as? ViewGroup)?.removeView(hostTarget) }
                            }
                        }
                        verifyHostGapPointer()

                        suspend fun verifyPreeditLifecycleAndPointer() {
                            fun verifyHiddenTouchRegion(target: View, label: String) {
                                main {
                                    val originalVisibility = target.visibility
                                    val bounds = visibleWindowBounds(root)
                                    val beforeInsets = touchInsets()
                                    val decor = checkNotNull(service.window.window).decorView
                                    val keyboardRegion = Region(0, beforeInsets.visibleTopInsets.coerceIn(0, decor.height), decor.width, decor.height)
                                    val exposedRegion = Region(bounds).apply { op(keyboardRegion, Region.Op.DIFFERENCE) }
                                    val diagnostic = JSONObject().apply {
                                        put("theme", theme)
                                        put("check", label)
                                        put("target", if (target === root) "preedit" else "input_view")
                                        put("original_visibility", originalVisibility)
                                        put("requested_visibility", View.INVISIBLE)
                                        put("visible_bounds_before", bounds.toShortString())
                                        put("keyboard_region_before", keyboardRegion.toString())
                                        put("exposed_region_before", exposedRegion.toString())
                                        put("touch_region_before", beforeInsets.touchableRegion.toString())
                                        put("passed", false)
                                    }
                                    touchRegionLifecycleChecks.put(diagnostic)
                                    check(originalVisibility == View.VISIBLE && root.isShown && regionCovers(root)) { "$label needs a visible, touchable preedit: $diagnostic" }
                                    check(!exposedRegion.isEmpty) { "$label needs preedit pixels outside the keyboard baseline: $diagnostic" }
                                    roots().asSequence().flatMap(::descendants).filterIsInstance<CandidatesView>().forEach { candidates ->
                                        if (candidates.windowToken == root.windowToken) {
                                            val overlap = Region(visibleWindowBounds(candidates)).apply { op(exposedRegion, Region.Op.INTERSECT) }
                                            check(overlap.isEmpty) { "$label overlaps another visible candidate surface: $overlap $diagnostic" }
                                        }
                                    }
                                    // Composition updates may legitimately show the preedit again. Keep
                                    // this artificial hidden state and its inset computation in one main
                                    // callback, and check the previous physical region as well as visibility.
                                    try {
                                        target.visibility = View.INVISIBLE
                                        val afterInsets = touchInsets()
                                        val residual = Region(exposedRegion).apply { op(afterInsets.touchableRegion, Region.Op.INTERSECT) }
                                        diagnostic.apply {
                                            put("observed_visibility", target.visibility)
                                            put("root_visibility", root.visibility)
                                            put("root_shown", root.isShown)
                                            put("root_attached", root.isAttachedToWindow)
                                            put("root_window_visibility", root.windowVisibility)
                                            put("touch_region_hidden", afterInsets.touchableRegion.toString())
                                            put("residual_region", residual.toString())
                                        }
                                        check(target.visibility == View.INVISIBLE && !root.isShown) { "$label did not establish its hidden-state precondition: $diagnostic" }
                                        check(!regionCovers(root)) { "$label still covers the hidden preedit: $diagnostic" }
                                        check(afterInsets.visibleTopInsets == beforeInsets.visibleTopInsets) { "$label unexpectedly moved the keyboard baseline: $diagnostic" }
                                        check(residual.isEmpty) { "$label retained the previous preedit touch region: $diagnostic" }
                                        diagnostic.put("passed", true)
                                    } finally {
                                        target.visibility = originalVisibility
                                        diagnostic.put("restored_visibility", target.visibility)
                                    }
                                }
                                phase("PASS: $label")
                            }
                            verifyHiddenTouchRegion(root, "$theme invisible preedit leaves the IME touch region")
                            until("$theme visible preedit resumes the IME touch region") { regionCovers(root) }
                            verifyHiddenTouchRegion(iv, "$theme invisible preedit ancestor leaves the IME touch region")
                            until("$theme visible preedit ancestor resumes the IME touch region") { regionCovers(root) }
                            val originalScroll = main { root.scrollX to root.scrollY }
                            val unscrolledBounds = main { visibleWindowBounds(root) }
                            try {
                                main { root.scrollTo(originalScroll.first + 11, originalScroll.second + 7) }
                                until("$theme own content scrolling preserves the physical preedit touch region") {
                                    visibleWindowBounds(root) == unscrolledBounds && regionCovers(root) &&
                                        !touchInsets().touchableRegion.contains(checkNotNull(service.window.window).decorView.width - 1, unscrolledBounds.centerY())
                                }
                            } finally {
                                main { root.scrollTo(originalScroll.first, originalScroll.second) }
                            }
                            until("$theme restored preedit scroll preserves its physical touch region") { visibleWindowBounds(root) == unscrolledBounds && regionCovers(root) }
                            main {
                                val parent = root.parent as ViewGroup
                                val params = root.layoutParams
                                parent.removeView(root)
                                try {
                                    check(!regionCovers(root)) { "Detached preedit must not retain a touch region" }
                                } finally {
                                    parent.addView(root, params)
                                }
                            }
                            until("$theme reattached preedit restores only its visible touch region") { regionCovers(root) }
                            // Preserve the historical host protocol label for the replaced overlay path.
                            phase("PASS: detached touch overlay cannot crash or leak across reattachment")
                            delay(150)
                            check(main { iv.rime.run { compositionCached.cursorPos > 0 } }) { "Preedit pointer needs a nonzero native caret before the tap" }
                            touchStage("before_preedit_pointer", theme)
                            tapActualView(preedit.preedit, leadingText = true)
                            until("$theme real preedit pointer changes the native caret without committing") {
                                iv.rime.run { compositionCached.cursorPos == 0 && compositionCached.length > 0 } && chat.text.toString() == pendingBefore
                            }
                            api {
                                check(getRawInput() == "ni")
                                clearComposition()
                            }
                            touchStage("after_preedit_pointer", theme)
                        }
                        verifyPreeditLifecycleAndPointer()

                        suspend fun verifyRapidPreeditRounds() {
                            repeat(20) { round ->
                                touchStage("before_rapid_show", theme, round)
                                val showStart = SystemClock.elapsedRealtimeNanos()
                                type("ni")
                                api { check(getRawInput() == "ni") }
                                until("$theme rapid $round preedit included") { preedit.preedit.isShown && regionCovers(preedit.preedit) }
                                val showMs = (SystemClock.elapsedRealtimeNanos() - showStart) / 1_000_000
                                touchStage("before_rapid_clear", theme, round)
                                val clearStart = SystemClock.elapsedRealtimeNanos()
                                api { clearComposition() }
                                until("$theme rapid $round cleared preedit excluded") { !preedit.preedit.isShown && !regionCovers(preedit.preedit) }
                                val clearMs = (SystemClock.elapsedRealtimeNanos() - clearStart) / 1_000_000
                                main { checkNoTouchPopup() }
                                touchRegionMeasurements.put(
                                    JSONObject().apply {
                                        put("theme", theme)
                                        put("round", round)
                                        put("show_ms", showMs)
                                        put("clear_ms", clearMs)
                                    },
                                )
                                touchStage("after_rapid_clear", theme, round, clearMs)
                                check(showMs < 2_000 && clearMs < 2_000) { "Rapid preedit operation exceeded the 2s stall regression limit: show=$showMs clear=$clearMs" }
                                check(main { chat.text.toString() == before }) { "Showing/clearing preedit unexpectedly committed editor text" }
                            }
                        }
                        verifyRapidPreeditRounds()

                        suspend fun verifyFloatingCandidatePointer() {
                            val previousMode = main { candidatesMode.getValue() }
                            try {
                                main { candidatesMode.setValue(PopupCandidatesMode.INPUT_DEVICE) }
                                touchStage("before_hardware_candidate", theme)
                                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_N)
                                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_I)
                                fun floatingCandidates() = roots().asSequence().flatMap(::descendants).filterIsInstance<CandidatesView>().firstOrNull { it.isShown }
                                until("$theme hardware candidates own their main-window touch region") {
                                    service.isInputViewShown && input() == null && floatingCandidates()?.let(::regionCovers) == true
                                }
                                val cv = main { checkNotNull(floatingCandidates()) }
                                val recycler = main { descendants(cv).filterIsInstance<RecyclerView>().first() }
                                until("$theme hardware first candidate has a current native snapshot") {
                                    val holder = recycler.findViewHolderForAdapterPosition(0) as? PagedCandidatesUi.UiHolder.Candidate
                                    holder != null && holder.ui.root.isShown && holder.snapshot == iv.rime.run { candidateSnapshotCached }
                                }
                                val holder = main { recycler.findViewHolderForAdapterPosition(0) as PagedCandidatesUi.UiHolder.Candidate }
                                var expected = ""
                                api {
                                    check(getRawInput() == "ni")
                                    expected = getCandidates(0, 1, holder.snapshot).single().text
                                }
                                check(expected.isNotEmpty() && main { descendants(holder.ui.root).filterIsInstance<TextView>().any { it.isShown && it.text.toString().contains(expected) } })
                                delay(150)
                                tapActualView(holder.ui.root)
                                until("$theme real floating candidate pointer commits its native text exactly once") { chat.text.toString() == before + expected }
                                api { check(getRawInput().isEmpty()) }
                                delay(400)
                                check(main { chat.text.toString() == before + expected }) { "Floating candidate commit repeated after its snapshot changed" }
                                main { checkNoTouchPopup() }
                                touchStage("after_hardware_candidate", theme)
                            } finally {
                                // Return through Android's real editor click while its connection
                                // is still active. setText can invalidate it and queue restartInput;
                                // calling the service directly after that races the old monitor.
                                main { candidatesMode.setValue(previousMode) }
                                touchStage("before_host_virtual_return", theme)
                                val hostTouches = mutableListOf<Pair<Int, Int>>()

                                @SuppressLint("ClickableViewAccessibility")
                                fun observeHostTouches() {
                                    // Return false so the real EditText handles focus, click and IME
                                    // callbacks. Record the delivered events, not just injected bytes.
                                    chat.setOnTouchListener { _, event ->
                                        hostTouches.add(event.actionMasked to event.getToolType(0))
                                        false
                                    }
                                }
                                main { observeHostTouches() }
                                try {
                                    tapActualView(chat, imeTarget = false)
                                    until("$theme real host editor receives finger down/up") {
                                        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).all { action ->
                                            action to MotionEvent.TOOL_TYPE_FINGER in hostTouches
                                        }
                                    }
                                } finally {
                                    main { chat.setOnTouchListener(null) }
                                }
                                until("$theme real host editor pointer restores the virtual keyboard before resetting text") {
                                    inputManager.isActive(chat) && input() != null && !keyboard().isT9Layout
                                }
                                touchStage("after_host_virtual_return", theme)
                                val resetEditorToken = main {
                                    val token = service.editorToken
                                    chat.setText(before)
                                    chat.setSelection(chat.text.length)
                                    inputManager.showSoftInput(chat, 0)
                                    token
                                }
                                until("$theme reset text belongs to the restarted host editor connection") {
                                    service.editorToken != resetEditorToken && service.isCurrentEditor(service.editorToken) &&
                                        service.currentInputEditorInfo?.fieldId == chat.id && inputManager.isActive(chat) &&
                                        input() != null && !keyboard().isT9Layout
                                }
                            }
                        }
                        verifyFloatingCandidatePointer()

                        until("$theme virtual full-pinyin returns after real floating candidate touch") { input() != null && !keyboard().isT9Layout }
                        type("ni")
                        api { check(getRawInput() == "ni") }
                        until("$theme preedit restored before visibility regression") { regionCovers(preedit.preedit) }
                        phase("PASS: $theme main IME touch region, real preedit/candidate pointers and 20 rapid show-clear rounds")
                    }
                    suspend fun verifyT9Accessibility(theme: String) {
                        fun currentViews(): Map<Int, KeyView> {
                            val activeInput = checkNotNull(input())
                            check(keyboard().isT9Layout) { "$theme accessibility fixture is not actual T9" }
                            val visible = descendants(activeInput).filterIsInstance<KeyView>().filter { it.isShown }.toList()
                            return keyboard().keys.indices.associateWith { index -> visible.single { it.id == index } }
                        }
                        var lastDescriptionMismatch: String? = null
                        fun descriptionsMatch(ascii: Boolean, expectedViews: Map<Int, KeyView>?): Boolean {
                            val active = keyboard()
                            val views = currentViews()
                            fun mismatch(reason: String): Boolean {
                                val actual = views.mapValues { (_, view) -> view.contentDescription?.toString() }
                                val detail = "theme=$theme; ascii=$ascii; $reason; actual=$actual"
                                if (detail != lastDescriptionMismatch) {
                                    lastDescriptionMismatch = detail
                                    instrumentation.sendStatus(0, Bundle().apply { putString("stream", "T13 T9 accessibility mismatch: $detail\n") })
                                }
                                return false
                            }
                            if (expectedViews != null && views.any { (index, view) -> expectedViews[index] !== view }) return mismatch("Cached key view identity changed")
                            val preferences = AppPrefs.defaultInstance().keyboard
                            val hintsVisible = !preferences.hideKeyHint.getValue()
                            val symbolsVisible = !preferences.hideKeySymbol.getValue()
                            val labels = listOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
                            for (digit in 2..9) {
                                val key = active.keys.single { it.code == KeyEvent.KEYCODE_0 + digit }
                                val description = if (ascii) digit.toString() else labels[digit - 2] + if (hintsVisible || symbolsVisible) " $digit" else ""
                                if (views.getValue(key.index).contentDescription?.toString() != description) return mismatch("Key ${key.index} expected=$description")
                            }
                            val symbols = active.keys.single { it.click?.select == "symbols" }
                            val symbolCaption = if (ascii) {
                                if (theme == "trime") "符號" else "更多"
                            } else {
                                "符号"
                            }
                            val symbolDescription = symbolCaption + if (symbolsVisible) {
                                if (theme == "trime") " 方案" else " 菜单"
                            } else {
                                ""
                            }
                            if (views.getValue(symbols.index).contentDescription?.toString() != symbolDescription) return mismatch("Key ${symbols.index} expected=$symbolDescription")
                            for ((code, nativeLabel, asciiLabel) in listOf(
                                Triple(KeyEvent.KEYCODE_COMMA, "，", ","),
                                Triple(KeyEvent.KEYCODE_PERIOD, "。", "."),
                            )) {
                                val key = active.keys.single { it.code == code }
                                val description = if (ascii) asciiLabel else nativeLabel
                                if (views.getValue(key.index).contentDescription?.toString() != description) return mismatch("Key ${key.index} expected=$description")
                            }
                            return true
                        }
                        val cached = main { currentViews() }
                        api {
                            setRuntimeOption("ascii_mode", true)
                            check(statusCached.isAsciiMode)
                        }
                        until("$theme cached T9 accessibility changes to actual ASCII labels") { descriptionsMatch(true, cached) }
                        api {
                            setRuntimeOption("ascii_mode", false)
                            check(!statusCached.isAsciiMode)
                        }
                        until("$theme cached T9 accessibility changes back to ABC and Chinese punctuation") { descriptionsMatch(false, cached) }
                        // Freeze the next queued native mode until a fresh themed T9 view has
                        // actually been constructed from the preceding ASCII native cache.
                        api { setRuntimeOption("ascii_mode", true) }
                        until("$theme cached T9 is ASCII before a theme rebuild") { descriptionsMatch(true, cached) }
                        val entered = CompletableDeferred<Unit>()
                        val release = CompletableDeferred<Unit>()
                        val held = main {
                            service.postRimeJob {
                                entered.complete(Unit)
                                release.await()
                            }
                        }
                        val rebuilt = try {
                            withTimeout(30_000) { entered.await() }
                            ThemeManager.selectTheme(theme, forceReload = true)
                            until("$theme rebuild creates new real T9 views while ASCII mode is queued") {
                                input() != null && keyboard().isT9Layout && currentViews().all { (index, view) -> cached[index] !== view }
                            }
                            main {
                                currentViews().also { rebuilt ->
                                    check(descriptionsMatch(true, rebuilt)) { "$theme rebuilt T9 did not describe its actual initial ASCII labels" }
                                }
                            }
                        } finally {
                            release.complete(Unit)
                            held.join()
                        }
                        api {
                            setRuntimeOption("ascii_mode", false)
                            check(!statusCached.isAsciiMode)
                        }
                        until("$theme rebuilt T9 accessibility refreshes native labels without replacing views") { descriptionsMatch(false, rebuilt) }
                    }
                    suspend fun verifyThemeLayoutAndCandidates() {
                        for (theme in listOf("trime", "tongwenfeng.trime")) {
                            ThemeManager.selectTheme(theme)
                            until("$theme T9 before schema switch") { input() != null && keyboard().isT9Layout }
                            verifyT9Accessibility(theme)
                            if (theme == "trime") {
                                ResidentWindowColorRegression.verify(instrumentation, main { checkNotNull(input()) })
                            }
                            api {
                                check(selectSchema("luna_pinyin_simp"))
                                setRuntimeOption("ascii_mode", false)
                            }
                            until("$theme full pinyin replaces locked T9") { input() != null && !keyboard().isT9Layout }
                            val touchCommittedBefore = main { chat.text.toString() }
                            type("ni")
                            api { check(getRawInput() == "ni") }
                            val preeditView = main { checkNotNull(input()).di.direct.instance<PreeditDelegate>().ui.preedit }
                            until("$theme full-pinyin preedit overlay is visible") { preeditView.isShown && regionCovers(preeditView) }
                            verifyTouchRegion(theme, touchCommittedBefore)
                            val imm = editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                            touchStage("before_hide", theme)
                            main { imm.hideSoftInputFromWindow(chat.windowToken, 0) }
                            until("$theme hiding input dismisses the preedit overlay") { !service.isInputViewShown && !regionCovers(preeditView) }
                            touchStage("before_show", theme)
                            main { imm.showSoftInput(chat, 0) }
                            until("$theme showing input restores the preedit overlay") { service.isInputViewShown && regionCovers(preeditView) }
                            api {
                                check(getRawInput() == "ni")
                                clearComposition()
                            }
                            touchStage("after_hide_show_clear", theme)
                            main {
                                other.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            }
                            focus(other)
                            focus(chat)
                            api {
                                check(selectedSchemaId() == "luna_pinyin_simp" && !statusCached.isAsciiMode)
                            }
                            check(main { !keyboard().isT9Layout }) { "$theme restored an old schema's T9 lock after email" }
                            api {
                                check(selectSchema("luna_pinyin_t9"))
                                setRuntimeOption("ascii_mode", false)
                            }
                            until("$theme T9 returns after full pinyin") { input() != null && keyboard().isT9Layout }
                            type("64")
                            api { check(getRawInput() == "64") }
                            until("$theme native Hanzi row is laid out") {
                                input()?.di?.direct?.instance<CompactCandidateDelegate>()?.view
                                    ?.findViewHolderForAdapterPosition(0)?.itemView
                                    ?.let { it.isShown && it.isLaidOut && !it.isLayoutRequested } == true
                            }
                            instrumentation.waitForIdleSync()
                            delay(150)
                            verifyCandidateGlyphs(instrumentation, service, theme)
                            val expandedChecks = ExpandedCandidateViewportAssertions.verify(instrumentation, main { checkNotNull(input()) }, service, chat)
                            phase("PASS: $theme $expandedChecks actual expanded-window recreation/relayout checks preserve the global index and commit its native text exactly once")
                            api { clearComposition() }
                            // A narrow numeric SPACE key must show its purpose without clipping
                            // the longer current schema name. Check both shipped themes, not
                            // arbitrary user-defined labels or typography.
                            for (numericType in listOf(InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_NUMBER)) {
                                main {
                                    other.hint = "$theme numeric space"
                                    other.inputType = numericType
                                    other.imeOptions = EditorInfo.IME_ACTION_DONE
                                    other.setImeActionLabel(null, 0)
                                    other.setText("")
                                }
                                focus(other)
                                until("$theme numeric space key is laid out") {
                                    val activeInput = input() ?: return@until false
                                    val window = activeInput.di.direct.instance<KeyboardWindow>()
                                    val selectedId = KeyboardWindow::class.java.getDeclaredField("currentKeyboardId").apply { isAccessible = true }.get(window)
                                    if (selectedId != "number" || window.currentKeyboard.isT9Layout) return@until false
                                    val keys = window.currentKeyboard.keys
                                    val space = keys.singleOrNull { it.code == KeyEvent.KEYCODE_SPACE } ?: return@until false
                                    descendants(activeInput).filterIsInstance<KeyView>()
                                        .singleOrNull { it.id == keys.indexOf(space) }
                                        ?.let { it.isShown && it.width > 0 && it.height > 0 } == true
                                }
                                main {
                                    val keys = keyboard().keys
                                    val space = keys.single { it.code == KeyEvent.KEYCODE_SPACE }
                                    check(space.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_SPACE) { "$theme space lost its action" }
                                    val view = descendants(checkNotNull(input())).filterIsInstance<KeyView>()
                                        .single { it.id == keys.indexOf(space) }
                                    val visible = Rect()
                                    check(view.getLocalVisibleRect(visible) && visible == Rect(0, 0, view.width, view.height)) {
                                        "$theme numeric space key is clipped: $visible"
                                    }
                                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                    try {
                                        // Render the actual KeyView before reading its final text paint;
                                        // nominal theme size alone would miss drawing-time fitting.
                                        view.draw(Canvas(bitmap))
                                        val paint = KeyView::class.java.getDeclaredField("textPaint").apply { isAccessible = true }.get(view) as Paint
                                        check(paint.textAlign == Paint.Align.CENTER) { "Numeric space bounds require the actual centered text paint" }
                                        val label = space.getLabel()
                                        val glyphs = Rect()
                                        paint.getTextBounds(label, 0, label.length, glyphs)
                                        val centerX = (view.width - view.paddingLeft - view.paddingRight) / 2f + view.paddingLeft + view.sp(space.keyTextOffsetX)
                                        val left = centerX - paint.measureText(label) / 2f + glyphs.left
                                        val right = centerX - paint.measureText(label) / 2f + glyphs.right
                                        check(!glyphs.isEmpty && left >= view.paddingLeft && right <= view.width - view.paddingRight) {
                                            "$theme numeric space label '$label' is horizontally clipped: $left..$right in ${view.paddingLeft}..${view.width - view.paddingRight}"
                                        }
                                        val metrics = paint.fontMetrics
                                        val baseline = (view.height - view.paddingTop - view.paddingBottom) / 2f + view.paddingTop -
                                            (metrics.ascent + metrics.descent) / 2f + view.sp(space.keyTextOffsetY)
                                        check(baseline + glyphs.top >= view.paddingTop && baseline + glyphs.bottom <= view.height - view.paddingBottom) {
                                            "$theme numeric space label '$label' is vertically clipped"
                                        }
                                        check(label == "空格") { "$theme numeric space must identify the space action" }
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                                phase("PASS: $theme numeric space action and rendered label fit its visible key")
                                focus(chat)
                                until("$theme T9 returns after numeric space check") { input() != null && keyboard().isT9Layout }
                            }
                        }
                        ThemeManager.selectTheme(originalTheme)
                        until("original theme retains selected T9") { input() != null && keyboard().isT9Layout }
                    }
                    verifyThemeLayoutAndCandidates()

                    suspend fun verifyEditorInputTypes() {
                        val cases = listOf(
                            Triple("email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT),
                            Triple("web-email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT),
                            Triple("password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE),
                            Triple("web-password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, EditorInfo.IME_ACTION_DONE),
                            Triple("visible-password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, EditorInfo.IME_ACTION_DONE),
                            Triple("address", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_ACTION_GO),
                            Triple("phone", InputType.TYPE_CLASS_PHONE, EditorInfo.IME_ACTION_DONE),
                            Triple("integer", InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_DONE),
                            Triple("numeric-password", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE),
                            Triple("decimal", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, EditorInfo.IME_ACTION_DONE),
                            Triple("search", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH),
                            Triple("multiline", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION),
                        )
                        for ((name, inputType, options) in cases) {
                            main {
                                other.hint = name
                                other.inputType = inputType
                                other.imeOptions = options
                                other.setText("")
                                actions.clear()
                            }
                            focus(other)
                            val expectedPrivate = "password" in name
                            check(service.isPrivacyModeActive == expectedPrivate) { "$name privacy state mismatch" }
                            check(PrivacyModeState.active == expectedPrivate) { "$name process privacy state mismatch" }
                            check(speechAllowed() != expectedPrivate) { "$name speech privacy state mismatch" }
                            api {
                                check(Rime.isRimePrivacyGuardEnabledForTesting() == expectedPrivate) {
                                    "$name native privacy state mismatch"
                                }
                            }
                            val restricted = name !in setOf("search", "multiline")
                            api { check(statusCached.isAsciiMode == restricted) { "$name ASCII state mismatch" } }
                            check(main { keyboard().isT9Layout } != restricted) { "$name layout mismatch" }
                            if (restricted) {
                                type(if (name == "decimal") "12.3" else "12")
                                api { }
                                until("$name direct text") { other.text.toString() == if (name == "decimal") "12.3" else "12" }
                            }
                            val label = main { checkNotNull(input()).di.direct.instance<EnterKeyDisplayDelegate>().keyLabel }
                            check(label.isNotEmpty() && label != "null")
                            enter()
                            api { }
                            if (name != "multiline") {
                                until("$name editor action matches label '$label'") { actions.lastOrNull() == options and EditorInfo.IME_MASK_ACTION }
                            } else {
                                until("multiline gets newline") { other.text.toString().endsWith('\n') }
                            }
                            focus(chat)
                            api {
                                check(!statusCached.isAsciiMode)
                                check(selectedSchemaId() == "luna_pinyin_t9")
                            }
                            check(main { keyboard().isT9Layout }) { "$name did not restore T9" }
                        }
                        main {
                            other.hint = "no-personalized-learning"
                            other.inputType = InputType.TYPE_CLASS_TEXT
                            other.imeOptions = EditorInfo.IME_ACTION_DONE or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG
                        }
                        focus(other)
                        check(service.isPrivacyModeActive && PrivacyModeState.active)
                        check(!speechAllowed())
                        check(main { keyboard().isT9Layout }) { "No-learning flag must not force an unrelated keyboard layout" }
                        api { check(Rime.isRimePrivacyGuardEnabledForTesting()) }
                        focus(chat)
                        check(!service.isPrivacyModeActive && !PrivacyModeState.active)
                        api { check(!Rime.isRimePrivacyGuardEnabledForTesting()) }
                        phase("PASS: no-personalized-learning flag controls privacy without replacing T9")
                    }
                    verifyEditorInputTypes()

                    suspend fun verifyPrivacyAndEditorActions() {
                        verifyTransientKeyboardAcrossPrivacyChange(instrumentation, service)

                        main { manualPrivacyMode.setValue(true) }
                        until("manual privacy reaches the active editor") {
                            service.isPrivacyModeActive && PrivacyModeState.active && !speechAllowed()
                        }
                        api { check(Rime.isRimePrivacyGuardEnabledForTesting()) }
                        val privateUndoSuffix = "x"
                        val privateUndoPrefix = "virtual-password-"
                        val slideDelete = main {
                            descendants(checkNotNull(input()))
                                .filterIsInstance<KeyView>()
                                .first { it.isSlideDelete }
                        }
                        val deletedTextBuffer = KeyView::class.java
                            .getDeclaredField("deletedTextBuffer")
                            .apply { isAccessible = true }
                        api { clearComposition() }
                        main {
                            check(chat.text.isEmpty()) { "Private slide-delete fixture requires an empty editor" }
                            check(checkNotNull(service.currentInputConnection).commitText(privateUndoPrefix + privateUndoSuffix, 1))
                        }
                        until("private slide-delete fixture reaches the active connection") {
                            chat.text.toString() == privateUndoPrefix + privateUndoSuffix
                        }
                        main { checkNotNull(slideDelete.onSlide).invoke(-1, 0f, 0f) }
                        until("private slide-delete stages the virtual suffix") {
                            chat.text.toString() == privateUndoPrefix
                        }
                        main {
                            @Suppress("UNCHECKED_CAST")
                            val buffered = deletedTextBuffer.get(slideDelete) as Collection<String>
                            check(buffered.toList() == listOf(privateUndoSuffix)) {
                                "Private slide-delete buffer did not retain only the virtual suffix"
                            }
                        }
                        main { manualPrivacyMode.setValue(false) }
                        until("manual privacy restores the active editor") {
                            !service.isPrivacyModeActive && !PrivacyModeState.active && speechAllowed()
                        }
                        api { check(!Rime.isRimePrivacyGuardEnabledForTesting()) }
                        main {
                            @Suppress("UNCHECKED_CAST")
                            val buffered = deletedTextBuffer.get(slideDelete) as Collection<String>
                            check(buffered.isEmpty()) { "Private slide-delete buffer survived the privacy boundary" }
                            checkNotNull(slideDelete.onSlide).invoke(1, 0f, 0f)
                        }
                        instrumentation.waitForIdleSync()
                        delay(250)
                        main {
                            check(chat.text.toString() == privateUndoPrefix) {
                                "Private slide-delete undo survived the privacy boundary"
                            }
                            check(checkNotNull(service.currentInputConnection).deleteSurroundingText(privateUndoPrefix.length, 0))
                        }
                        until("private slide-delete fixture is cleared") { chat.text.isEmpty() }
                        phase("PASS: manual privacy atomically gates speech and native learning, then restores normal input")
                        PrivacyTriggerSinkProbe.verify(
                            instrumentation,
                            PrivacyTriggerSinkProbe.Harness(
                                service = service,
                                chat = chat,
                                target = other,
                                focus = ::focus,
                                runApi = ::api,
                                inputTextContains = { token ->
                                    input()?.let { activeInput ->
                                        descendants(activeInput)
                                            .filterIsInstance<TextView>()
                                            .any { it.text.toString().contains(token) }
                                    } == true
                                },
                                speechAllowed = ::speechAllowed,
                                phase = ::phase,
                            ),
                        )
                        main {
                            other.inputType = InputType.TYPE_CLASS_TEXT
                            other.imeOptions = EditorInfo.IME_ACTION_SEARCH
                            other.setImeActionLabel("Continue", 731)
                            actions.clear()
                        }
                        focus(other)
                        check(main { checkNotNull(input()).di.direct.instance<EnterKeyDisplayDelegate>().keyLabel } == "Continue")
                        enter()
                        api { }
                        until("custom action uses ID 731") { actions.lastOrNull() == 731 }
                        focus(chat)

                        ModifiedReturnRegression.verify(
                            instrumentation,
                            service,
                            editor,
                            container,
                            chat,
                            focus = ::focus,
                            runApi = ::api,
                            dispatch = { modifiers -> checkNotNull(input()).di.direct.instance<CommonKeyboardActionListener>().listener.onKey(KeyEvent.KEYCODE_ENTER, modifiers) },
                        )

                        api { setRuntimeOption("ascii_mode", true) }
                        main {
                            other.inputType = InputType.TYPE_CLASS_NUMBER
                            other.setImeActionLabel(null, 0)
                        }
                        focus(other)
                        focus(chat)
                        api { check(statusCached.isAsciiMode) { "Temporary number mode lost the preceding native ASCII mode" } }
                        api { setRuntimeOption("ascii_mode", false) }

                        main {
                            other.inputType = InputType.TYPE_CLASS_TEXT
                            other.imeOptions = EditorInfo.IME_ACTION_SEARCH
                            other.setImeActionLabel("Unusable action", 0)
                        }
                        focus(other)
                        check(main { checkNotNull(input()).di.direct.instance<EnterKeyDisplayDelegate>().keyLabel } != "Unusable action")
                        focus(chat)

                        verifyHardwareEditorPolicies(instrumentation, service, editor, chat, other)
                    }
                    verifyPrivacyAndEditorActions()

                    suspend fun verifyQueuedEngineJoin() {
                        val queueEntered = CompletableDeferred<Unit>()
                        val queueRelease = CompletableDeferred<Unit>()
                        var overtook = false
                        main {
                            service.postRimeJob {
                                queueEntered.complete(Unit)
                                queueRelease.await()
                            }
                        }
                        queueEntered.await()
                        val following = main { service.postRimeJob { overtook = true } }
                        val waiting = async { following.join() }
                        delay(150)
                        check(!overtook) { "Joining a queued job bypassed the serial executor" }
                        queueRelease.complete(Unit)
                        waiting.await()
                        check(overtook)
                        phase("PASS: joining an engine job preserves queue order")
                    }
                    verifyQueuedEngineJoin()

                    suspend fun verifyDelayedSwitchCallbacks() {
                        // A real suspend builder must retain the editor that requested its dialog.
                        // Reflection keeps this deterministic fault gate out of the production API.
                        main {
                            other.inputType = InputType.TYPE_CLASS_TEXT
                            other.imeOptions = EditorInfo.IME_ACTION_SEARCH
                            other.setImeActionLabel(null, 0)
                        }
                        val switchWindow = main { SwitchOptionWindow(checkNotNull(input()).di) }
                        val dialogEntered = CompletableDeferred<Unit>()
                        val dialogRelease = CompletableDeferred<Unit>()
                        val dialogReturned = CompletableDeferred<Unit>()
                        val delayedDialog = main { AlertDialog.Builder(service).setTitle("Editor-owned dialog audit").create() }
                        val delayedBuilder: suspend (RimeApi) -> Dialog = {
                            dialogEntered.complete(Unit)
                            dialogRelease.await()
                            dialogReturned.complete(Unit)
                            delayedDialog
                        }
                        main {
                            SwitchOptionWindow::class.java.declaredMethods.single { it.name == "showDialog" }.apply {
                                isAccessible = true
                            }.invoke(switchWindow, delayedBuilder)
                        }
                        try {
                            withTimeout(5000) { dialogEntered.await() }
                            main {
                                other.setText("fresh target")
                                other.requestFocus()
                            }
                            until("new editor replaces a pending switch-window dialog") { service.currentInputEditorInfo?.fieldId == other.id }
                            dialogRelease.complete(Unit)
                            withTimeout(5000) { dialogReturned.await() }
                            api { }
                            main {
                                check(!delayedDialog.isShowing) { "Old editor dialog appeared over the new editor" }
                                check(other.text.toString() == "fresh target") { "Old dialog changed the new editor text" }
                            }
                        } finally {
                            dialogRelease.complete(Unit)
                            main { delayedDialog.dismiss() }
                        }
                        focus(chat)

                        val switchAdapter = main {
                            // setAdapter invokes onAttachedToRecyclerView, which supplies the
                            // adapter's context for its real popup-menu action below.
                            switchWindow.onCreateView()
                            checkNotNull(switchWindow.view.adapter) as SwitchOptionAdapter
                        }
                        val queuedOption = "_editor_audit_queued_option"
                        api { setRuntimeOption(queuedOption, false) }
                        val optionEntered = CompletableDeferred<Unit>()
                        val optionRelease = CompletableDeferred<Unit>()
                        main {
                            service.postRimeJob {
                                optionEntered.complete(Unit)
                                optionRelease.await()
                            }
                        }
                        withTimeout(5000) { optionEntered.await() }
                        try {
                            main {
                                switchAdapter.onItemClick(
                                    checkNotNull(input()),
                                    SwitchOptionEntry.Custom(RimeSchema.Switch(name = queuedOption, states = listOf("Off", "On")), "Audit option", 0),
                                )
                            }
                            delay(150)
                            checkNotNull(session).runOnReady {
                                check(!getRuntimeOption(queuedOption)) { "Switch-window option bypassed the editor queue" }
                            }
                            main {
                                other.setText("")
                                other.requestFocus()
                            }
                            until("editor changes with a queued switch-window option") { service.currentInputEditorInfo?.fieldId == other.id }
                        } finally {
                            optionRelease.complete(Unit)
                        }
                        api {
                            check(!getRuntimeOption(queuedOption)) { "Old switch-window option changed the new editor" }
                            check(getRawInput().isEmpty())
                        }
                        check(main { other.text.isEmpty() }) { "Queued switch-window option committed into the new editor" }
                        focus(chat)

                        // A dismissed popup's retained callback must use the menu's original token,
                        // even when Android delivers its click after the next editor has bound.
                        val menuOptions = listOf("_editor_audit_menu_first", "_editor_audit_menu_second")
                        api { menuOptions.forEach { setRuntimeOption(it, false) } }
                        val oldMenu = main {
                            switchAdapter.onItemClick(
                                checkNotNull(input()),
                                SwitchOptionEntry.Custom(RimeSchema.Switch(options = menuOptions, states = listOf("First", "Second")), "Audit menu", 0),
                            )
                            checkNotNull(switchWindow.popupMenu).menu.also { switchWindow.onDetached() }
                        }
                        focus(other)
                        main { check(oldMenu.performIdentifierAction(0, 0)) { "Retained option menu callback did not execute" } }
                        delay(150)
                        api {
                            check(menuOptions.none { getRuntimeOption(it) }) { "Old option popup changed the new editor" }
                            check(getRawInput().isEmpty())
                        }
                        check(main { other.text.isEmpty() }) { "Old option popup committed into the new editor" }
                        focus(chat)
                        phase("PASS: delayed switch dialogs, queued options and stale popup callbacks remain editor-owned")
                    }
                    verifyDelayedSwitchCallbacks()

                    suspend fun verifyStaleEditorInputs() {
                        type("64")
                        api { check(getRawInput() == "64") }
                        until("compact Hanzi candidate is clickable") {
                            input()?.di?.direct?.instance<CompactCandidateDelegate>()?.view?.findViewHolderForAdapterPosition(0)?.itemView?.isShown == true
                        }
                        val beforeCandidate = main { chat.text.toString() }
                        val candidateEntered = CompletableDeferred<Unit>()
                        val candidateRelease = CompletableDeferred<Unit>()
                        main {
                            service.postRimeJob {
                                candidateEntered.complete(Unit)
                                candidateRelease.await()
                            }
                        }
                        candidateEntered.await()
                        try {
                            main {
                                checkNotNull(input()).di.direct.instance<CompactCandidateDelegate>().view
                                    .findViewHolderForAdapterPosition(0)!!.itemView.performClick()
                            }
                            delay(150)
                            check(main { chat.text.toString() } == beforeCandidate) { "Candidate selection bypassed the serial queue" }
                            main {
                                other.setText("")
                                other.requestFocus()
                            }
                            until("focus changes while a candidate click is queued") { service.currentInputEditorInfo?.fieldId == other.id }
                        } finally {
                            candidateRelease.complete(Unit)
                        }
                        api { check(getRawInput().isEmpty()) }
                        check(main { other.text.isEmpty() && chat.text.toString() == beforeCandidate }) { "Queued candidate leaked across editors" }
                        focus(chat)

                        val entered = CompletableDeferred<Unit>()
                        val release = CompletableDeferred<Unit>()
                        main {
                            service.postRimeJob {
                                entered.complete(Unit)
                                release.await()
                                simulateKeySequence("STALE{Return}")
                            }
                        }
                        entered.await()
                        main {
                            other.setText("")
                            other.requestFocus()
                        }
                        until("focus changes while an old engine operation is suspended") { service.currentInputEditorInfo?.fieldId == other.id }
                        release.complete(Unit)
                        api { }
                        check(main { other.text.isEmpty() }) { "Old editor output leaked to the new editor" }
                        focus(chat)
                        api { check(getRawInput().isEmpty()) }

                        val heldKey = main { descendants(checkNotNull(input())).filterIsInstance<KeyView>().first() }
                        val down = SystemClock.uptimeMillis()
                        main {
                            MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, heldKey.width / 2f, heldKey.height / 2f, 0).let {
                                it.source = InputDevice.SOURCE_TOUCHSCREEN
                                heldKey.dispatchTouchEvent(it)
                                it.recycle()
                            }
                            other.setText("")
                        }
                        focus(other)
                        main {
                            MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, heldKey.width / 2f, heldKey.height / 2f, 0).let {
                                it.source = InputDevice.SOURCE_TOUCHSCREEN
                                heldKey.dispatchTouchEvent(it)
                                it.recycle()
                            }
                        }
                        api { }
                        check(main { other.text.isEmpty() }) { "Cancelled gesture clicked into the next editor" }
                        focus(chat)
                    }
                    verifyStaleEditorInputs()

                    suspend fun verifyThemeFallbackAndTipOwnership() {
                        // Rime's own include/patch resolver creates a real third-party theme without T9.
                        val id = "editor-audit-${android.os.Process.myPid()}.trime"
                        fixture = File(DataManager.userDataDir, "$id.yaml").also {
                            check(it.createNewFile())
                            it.writeText(
                                """
                                config_version: '1'
                                __include: trime:/
                                __patch:
                                  name: Editor audit without nine-key layout
                                  preset_keyboards/luna_pinyin_t9: null
                                  preset_keyboards/number: null
                                  preset_keyboards/letter: null
                                  preset_keyboards/bopomofo/ascii_keyboard: default
                                """.trimIndent(),
                            )
                        }
                        ThemeManager.selectTheme(id)
                        check(ThemeManager.prefs.selectedTheme.getValue() == id)
                        check("luna_pinyin_t9" !in ThemeManager.activeTheme.presetKeyboards)
                        until("third-party theme uses compatible T9") { input() != null && keyboard().isT9Layout }
                        val beforeRestart = main {
                            val token = service.editorToken
                            chat.setText("")
                            (editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(chat)
                            token
                        }
                        until("editor-owned reset has restarted the connection") { service.editorToken != beforeRestart && service.currentInputEditorInfo?.fieldId == chat.id }
                        api { }
                        type("64")
                        api {
                            check(getRawInput() == "64") { "Compatible T9 raw='${getRawInput()}', ASCII=${statusCached.isAsciiMode}, schema=${selectedSchemaId()}" }
                            check(t9Cached.enabled)
                            selectCandidate(0, false)
                        }
                        until("compatible T9 produces Hanzi") { chat.text.any { it.code > 127 } }
                        val beforeRedeploy = main { chat.text.toString() }
                        val inputViewBeforeRedeploy = main { checkNotNull(input()) }
                        val tipsPreference = AppPrefs.defaultInstance().general.asciiSwitchTips
                        val previousTipsPreference = tipsPreference.getValue()
                        // Observe the real timer without exposing a production test API.
                        val realRime = RimeDaemon::class.java.getDeclaredMethod("getRealRime").apply {
                            isAccessible = true
                        }.invoke(RimeDaemon) as Rime
                        val tipJobField = Rime::class.java.getDeclaredField("asciiSwitchTipsJob").apply {
                            isAccessible = true
                        }
                        try {
                            main { tipsPreference.setValue(true) }
                            var editorTip: Job? = null
                            val previousTipOwner = main { service.editorToken }
                            api {
                                setRuntimeOption("ascii_mode", true)
                                setRuntimeOption("ascii_mode", false)
                                editorTip = checkNotNull(tipJobField.get(realRime) as Job?)
                                check(checkNotNull(editorTip).isActive) { "No pending ASCII tip before real editor change" }
                            }
                            focus(other)
                            until("ASCII-tip owner changes with real focus") {
                                service.editorToken != previousTipOwner && service.currentInputEditorInfo?.fieldId == other.id
                            }
                            api {
                                check(checkNotNull(editorTip).isCancelled) { "Old editor's ASCII tip survived focus change" }
                                check(tipJobField.get(realRime) !== editorTip) { "Next editor retained the old ASCII-tip job" }
                            }
                            focus(chat)
                            phase("PASS: real editor focus change cancels the previous editor's ASCII tip")
                            api {
                                setRuntimeOption("ascii_mode", true)
                                setRuntimeOption("ascii_mode", false)
                                val previousTip = checkNotNull(tipJobField.get(realRime) as Job?)
                                check(previousTip.isActive) { "No pending ASCII tip before redeploy" }
                                updateConfig()
                                check(previousTip.isCancelled) { "Old native context's ASCII tip survived redeploy" }
                                val currentTip = checkNotNull(tipJobField.get(realRime) as Job?)
                                check(currentTip !== previousTip) { "Redeploy reused the old ASCII-tip timer" }
                                withTimeout(5000) { currentTip.join() }
                                check(currentTip.isCompleted && !currentTip.isCancelled) { "New engine's ASCII tip did not clear normally" }
                                // startRime() publishes a fresh tip for the active editor's empty
                                // composition. Assert the complete transient-state boundary after that new
                                // timer restores the native engine's empty composition.
                                checkTransientEditorStateCleared("successful native restart after ASCII tip")
                            }
                            phase("PASS: redeploy cancels the old ASCII tip and the new engine clears its own tip")
                        } finally {
                            main { tipsPreference.setValue(previousTipsPreference) }
                        }
                        until("deployment reloads the active theme and preserves compatible T9") {
                            input()?.let { it !== inputViewBeforeRedeploy && keyboard().isT9Layout } == true
                        }
                        api {
                            check(!statusCached.isAsciiMode) { "Theme reload lost the active editor's Chinese mode" }
                        }
                        type("64")
                        api {
                            check(getRawInput() == "64")
                            selectCandidate(0, false)
                        }
                        until("in-place redeploy retains editor ownership") { chat.text.length > beforeRedeploy.length }
                        main {
                            other.inputType = InputType.TYPE_CLASS_NUMBER
                            other.setImeActionLabel(null, 0)
                        }
                        focus(other)
                        // currentInputEditorInfo is published by onStartInput before the separate
                        // onStartInputView callback applies KeyboardWindow's editor policy. The
                        // visible layout change is the main-thread completion boundary for that
                        // callback; the following queued API read then observes its mode update.
                        until("redeployed number editor applies its ASCII layout") {
                            input() != null && !keyboard().isT9Layout
                        }
                        api { check(statusCached.isAsciiMode) { "Number editor did not enter ASCII mode" } }

                        verifyQueuedModeRestoreAcrossThemeReload(instrumentation, service, inputManager, chat, id)
                        verifyQueuedModeRestoreAcrossPrivacyChange(
                            instrumentation,
                            service,
                            inputManager,
                            other,
                            chat,
                        )
                    }
                    verifyThemeFallbackAndTipOwnership()

                    suspend fun verifyWebEditor() {
                        val webReady = CompletableDeferred<Unit>()
                        web = main {
                            WebView(editor).apply webFixture@{
                                fun describeContext(value: Context): JSONObject = JSONObject().apply {
                                    put("class", value.javaClass.name)
                                    put("identity", System.identityHashCode(value))
                                    put("is_activity", value is Activity)
                                    put("application_context_class", value.applicationContext?.javaClass?.name ?: JSONObject.NULL)
                                    put("autofill_feature", value.packageManager.hasSystemFeature("android.software.autofill"))
                                    put("autofill_service_class", value.getSystemService("autofill")?.javaClass?.name ?: JSONObject.NULL)
                                    put("autofill_enabled_api_supported", Build.VERSION.SDK_INT >= 26)
                                    put("autofill_enabled", if (Build.VERSION.SDK_INT >= 26) AutofillServiceApi26.isEnabled(value) ?: JSONObject.NULL else JSONObject.NULL)
                                }
                                // Observe the actual View context and platform service without
                                // changing Autofill, media permissions, or provider configuration.
                                webFixtureEnvironment.apply {
                                    put("captured", true)
                                    put("api", Build.VERSION.SDK_INT)
                                    put("pid", android.os.Process.myPid())
                                    put("web_identity", System.identityHashCode(this@webFixture))
                                    put("constructor_context", describeContext(editor))
                                    put("actual_view_context", describeContext(this@webFixture.context))
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String) {
                                        webReady.complete(Unit)
                                    }
                                }
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                container.addView(this, LinearLayout.LayoutParams(-1, 150))
                                loadDataWithBaseURL("https://example.invalid/", "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body><input id='field' type='email' style='width:200px;height:32px'></body></html>", "text/html", "UTF-8", null)
                            }
                        }
                        webReady.await()
                        main {
                            checkNotNull(web).requestFocus()
                            checkNotNull(web).evaluateJavascript("document.getElementById('field').focus()", null)
                        }
                        val webPoint = main {
                            val location = IntArray(2)
                            checkNotNull(web).getLocationOnScreen(location)
                            val density = editor.resources.displayMetrics.density
                            (location[0] + 30 * density) to (location[1] + 20 * density)
                        }
                        val webDown = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            fingerEvent(webDown, action, webPoint.first, webPoint.second).let {
                                instrumentation.sendPointerSync(it)
                                it.recycle()
                            }
                        }
                        until("real WebView email InputConnection") { service.currentInputEditorInfo?.inputType?.and(InputType.TYPE_MASK_VARIATION) in setOf(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) }
                        api { check(statusCached.isAsciiMode) }
                        type("12")
                        api { }
                        val webValue = CompletableDeferred<String>()
                        main { checkNotNull(web).evaluateJavascript("document.getElementById('field').value") { webValue.complete(it) } }
                        check(webValue.await() == "\"12\"")
                        focus(chat)
                        api { check(!statusCached.isAsciiMode) }
                        destroyWebFixture(required = true)
                    }
                    verifyWebEditor()

                    suspend fun verifyRepeatedImeVisibility() {
                        val baseline = main { chat.text.toString() }
                        val imm = editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                        suspend fun awaitPlatformImeSettled(index: Int, visible: Boolean) {
                            val startedAt = SystemClock.uptimeMillis()
                            var finalState = JSONObject()
                            val completed = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                                var stableSince = 0L
                                var previousGeometry: Pair<Int, Int>? = null
                                while (true) {
                                    val settled = main {
                                        val insets = ViewCompat.getRootWindowInsets(chat)
                                        val platformVisible = insets?.isVisible(WindowInsetsCompat.Type.ime())
                                        val bottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom
                                        val expectedService = service.isInputViewShown == visible
                                        val expectedLayout = !visible || (input() != null && keyboard().isT9Layout && chat.hasFocus())
                                        val expectedPlatform = if (Build.VERSION.SDK_INT < 30) {
                                            true
                                        } else {
                                            platformVisible == visible && bottom != null &&
                                                (if (visible) bottom > 0 else bottom == 0) &&
                                                imeAnimations.isIdle
                                        }
                                        val geometry = (bottom ?: -1) to imeAnimations.generation
                                        val unchanged = previousGeometry == geometry
                                        previousGeometry = geometry
                                        finalState = imeAnimations.snapshot().apply {
                                            put("service_visible", service.isInputViewShown)
                                            put("insets_visible", platformVisible ?: JSONObject.NULL)
                                            put("insets_bottom", bottom ?: JSONObject.NULL)
                                            put("editor_focused", chat.hasFocus())
                                            put("geometry_unchanged", unchanged)
                                        }
                                        expectedService && expectedLayout && expectedPlatform && unchanged
                                    }
                                    val now = SystemClock.uptimeMillis()
                                    if (settled) {
                                        if (stableSince == 0L) stableSince = now
                                        if (now - stableSince >= 700L) break
                                    } else {
                                        stableSince = 0L
                                    }
                                    delay(40)
                                }
                                true
                            } == true
                            imeVisibilityWaits.put(
                                finalState.apply {
                                    put("cycle", index)
                                    put("expected_visible", visible)
                                    put("passed", completed)
                                    put("elapsed_ms", SystemClock.uptimeMillis() - startedAt)
                                    put("stable_window_ms", 700)
                                },
                            )
                            check(completed) {
                                "IME ${if (visible) "show" else "hide"} did not settle at cycle $index: $finalState"
                            }
                        }
                        // Wait in both directions. Callback hosts additionally observe onEnd;
                        // ordinary hosts preserve Android's default animation path and require
                        // stable real insets. Stable geometry alone does not prove an onEnd event.
                        awaitPlatformImeSettled(0, visible = true)
                        repeat(100) { index ->
                            main { imm.hideSoftInputFromWindow(chat.windowToken, 0) }
                            awaitPlatformImeSettled(index + 1, visible = false)
                            main {
                                chat.requestFocus()
                                check(chat.hasFocus()) { "Editor lost focus before show cycle ${index + 1}" }
                                check(imm.showSoftInput(chat, 0)) { "IME show request rejected at cycle ${index + 1}" }
                            }
                            awaitPlatformImeSettled(index + 1, visible = true)
                            api {
                                check(!statusCached.isAsciiMode)
                                check(getRawInput().isEmpty())
                            }
                            check(main { chat.text.toString() } == baseline)
                            if ((index + 1) % 10 == 0) phase("PASS: ${index + 1}/100 show-hide cycles, T9 and editor text retained")
                        }
                        type("64426")
                        api { check(getRawInput() == "64426") }
                        until("pinyin choices still update after 100 visibility cycles") {
                            input()?.let { view ->
                                descendants(view).filterIsInstance<T9DisambiguationView>().flatMap(::descendants)
                                    .filterIsInstance<TextView>().any { it.text.toString() == "ni" && it.isShown }
                            } == true
                        }
                        main {
                            descendants(checkNotNull(input())).filterIsInstance<T9DisambiguationView>().flatMap(::descendants)
                                .filterIsInstance<TextView>().first { it.text.toString() == "ni" && it.isShown }.performClick()
                        }
                        api {
                            check(getRawInput() == "64426")
                            check(t9Cached.segments.any { it.locked && it.spelling == "ni" })
                        }
                        main { imm.hideSoftInputFromWindow(chat.windowToken, 0) }
                        awaitPlatformImeSettled(101, visible = false)
                        main {
                            chat.requestFocus()
                            check(imm.showSoftInput(chat, 0)) { "IME show request rejected after locked composition" }
                        }
                        until("locked composition survives hide and show") { service.isInputViewShown && input() != null }
                        awaitPlatformImeSettled(101, visible = true)
                        api {
                            check(getRawInput() == "64426")
                            check(t9Cached.segments.any { it.locked && it.spelling == "ni" })
                            clearComposition()
                        }
                        until("cancelling after 100 cycles preserves committed text") { chat.text.toString() == baseline }
                        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                        try {
                            File(context.cacheDir, "editor-lifecycle.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } finally {
                            screenshot.recycle()
                        }
                    }
                    verifyRepeatedImeVisibility()
                }
            }
            verifyResidentWindowIdempotency(instrumentation, editor)
            phase("PASS: resident windows preserve cached views and skip duplicate attachment lifecycles")
            verifyThirdPartyModifiers(instrumentation, editor)
            phase("PASS: isolated third-party KeyView dispatch consumes temporary modifiers and preserves actual locks")
            verifyInputBarMeasurement(instrumentation)
            verifyFlexibleToolbarMeasurement(instrumentation)
            phase("PASS: isolated production toolbar preserves adaptive third-party sizing")
            verifyThemeFontDirectoryCache(instrumentation)
            result.putString("stream", "PASS: input-field matrix, editor actions, stale output rejection, third-party T9 fallback, real WebView and 100 show/hide cycles\n")
            passed = true
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            try {
                main {
                    candidatesMode.setValue(originalCandidatesMode)
                    manualPrivacyMode.setValue(originalManualPrivacyMode)
                    destroyWebFixture()
                    imeAnimations.detach()
                    activity?.finish()
                }
                runBlocking {
                    runCatching {
                        ThemeManager.selectTheme(originalTheme)
                        session?.runOnReady {
                            clearComposition()
                            if (originalSchema.isNotEmpty()) selectSchema(originalSchema)
                            setRuntimeOption("ascii_mode", originalAscii)
                        }
                    }
                }
                fixture?.let { file ->
                    file.delete()
                    File(DataManager.stagingDir, file.name).delete()
                }
                main { RimeDaemon.destroySession(javaClass.name) }
                check(ThemeManager.prefs.selectedTheme.sharedPreferences.edit().commit())
            } finally {
                // Restore even if other cleanup fails; the next editor reloads the original mode.
                main { restoreManualLanguage() }
            }
        }
        val touchReport = JSONObject().apply {
            put("run_id", touchRegionRunId)
            put("api", Build.VERSION.SDK_INT)
            put("main_pid", android.os.Process.myPid())
            put("measurement", "instrumentation wall time including Rime queue, main-thread dispatch and visible-state polling; not candidate latency/P95")
            put("rapid_stall_limit_ms", 2_000)
            put("rapid_rounds", touchRegionMeasurements)
            put("touch_region_lifecycle_checks", touchRegionLifecycleChecks)
            put("ime_visibility_waits", imeVisibilityWaits)
            put("ime_animation_callbacks", main { imeAnimations.snapshot() })
            put("web_fixture_environment", webFixtureEnvironment)
            put("editor_probe_passed", passed)
        }
        result.putString("touch_region_metrics", touchReport.toString())
        val touchDirectory = File(context.filesDir, "runtime-audit/touch-region")
        check(touchDirectory.isDirectory || touchDirectory.mkdirs())
        File(touchDirectory, "report.json").writeText(touchReport.toString(2) + "\n")
        result.putBoolean("passed", passed)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
