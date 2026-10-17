/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

// This file has not been compiled or executed; baseline paths need device verification.
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.composition.T9DisambiguationView
import com.osfans.trime.ime.core.EditorPolicy
import com.osfans.trime.ime.core.InlinePreeditMode
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.keyboard.KeyCode
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.KeyboardView
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.virtualKeyCharacterMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

/** Actual visible logical key actions, real native state and forwarding editor connections. */
object MixedInputProbe {
    private class Unsupported(message: String, val code: String = "visible_route_unavailable") : IllegalStateException(message)

    private data class Task(val id: String, val target: String, val inputType: Int)

    private val tasks = listOf(
        Task("android16", "Android 16", InputType.TYPE_CLASS_TEXT),
        Task("wifi", "Wi-Fi", InputType.TYPE_CLASS_TEXT),
        Task("b_site", "B站", InputType.TYPE_CLASS_TEXT),
        Task("email", "trime.test+16@example.org", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
        Task("url", "https://example.org/a-b?x=16&lang=zh", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI),
        Task("phone", "+8613800138000", InputType.TYPE_CLASS_PHONE),
        Task("amount", "-12.50", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED),
    )

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

    private fun json(vararg values: Pair<String, Any?>) = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }

    /** The wrapper forwards every call to the actual EditText connection. No fake commits. */
    private class RecordingEditor(context: Context) : EditText(context) {
        val calls = Collections.synchronizedList(mutableListOf<JSONObject>())
        val keyRecipients = Collections.synchronizedList(mutableListOf<JSONObject>())

        private fun recordRecipient(method: String, event: KeyEvent, operation: () -> Boolean): Boolean {
            val item = json(
                "method" to method,
                "action" to event.action,
                "key_code" to event.keyCode,
                "down_time_ms" to event.downTime,
                "event_time_ms" to event.eventTime,
                "field_id" to id,
                "editor_object" to System.identityHashCode(this),
                "t_ns" to SystemClock.elapsedRealtimeNanos(),
                "on_main_thread" to (Looper.myLooper() == Looper.getMainLooper()),
            )
            keyRecipients.add(item)
            return operation().also { item.put("returned", it).put("completed_ns", SystemClock.elapsedRealtimeNanos()) }
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = recordRecipient("onKeyDown", event) { super.onKeyDown(keyCode, event) }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = recordRecipient("onKeyUp", event) { super.onKeyUp(keyCode, event) }

        override fun isSuggestionsEnabled(): Boolean = false

        private fun composingText(): String? {
            val content = text
            val start = BaseInputConnection.getComposingSpanStart(content)
            val end = BaseInputConnection.getComposingSpanEnd(content)
            return if (start >= 0 && end >= start && end <= content.length) content.subSequence(start, end).toString() else null
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val delegate = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(delegate, false) {
                private fun record(method: String, text: CharSequence? = null, event: KeyEvent? = null, operation: () -> Boolean): Boolean {
                    val item = json(
                        "method" to method,
                        "text" to text?.toString(),
                        "t_ns" to SystemClock.elapsedRealtimeNanos(),
                        "composing_before" to composingText(),
                        "field_id" to id,
                        "editor_object" to System.identityHashCode(this@RecordingEditor),
                        "on_main_thread" to (Looper.myLooper() == Looper.getMainLooper()),
                    )
                    if (event != null) {
                        item.put("down_time_ms", event.downTime).put("event_time_ms", event.eventTime)
                            .put("field_id", id).put("editor_object", System.identityHashCode(this@RecordingEditor))
                    }
                    calls.add(item)
                    return operation().also {
                        item.put("returned", it).put("completed_ns", SystemClock.elapsedRealtimeNanos())
                            .put("composing_after", composingText() ?: JSONObject.NULL)
                    }
                }

                override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean = record("commitText", text) { super.commitText(text, newCursorPosition) }

                override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean = record("setComposingText", text) { super.setComposingText(text, newCursorPosition) }

                override fun finishComposingText(): Boolean = record("finishComposingText") { super.finishComposingText() }

                override fun sendKeyEvent(event: KeyEvent): Boolean = record("sendKeyEvent", "${event.action}:${event.keyCode}", event) { super.sendKeyEvent(event) }
            }
        }

        fun snapshotCalls(): List<JSONObject> = synchronized(calls) { calls.toList() }

        fun snapshotKeyRecipients(): List<JSONObject> = synchronized(keyRecipients) { keyRecipients.toList() }

        fun checkAsciiKeyDelivery() {
            val sent = snapshotCalls().filter { it.optString("method") == "sendKeyEvent" && it.optString("text") in setOf("0:${KeyEvent.KEYCODE_A}", "1:${KeyEvent.KEYCODE_A}") }
            val received = snapshotKeyRecipients().filter { it.optInt("key_code", -1) == KeyEvent.KEYCODE_A }
            check(sent.size == 2 && received.size == 2) { "ASCII a needs exactly one IC and View down/up pair" }
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val call = sent.single { it.getString("text") == "$action:${KeyEvent.KEYCODE_A}" }
                val recipient = received.single { it.getInt("action") == action }
                check(call.opt("returned") is Boolean && call.getLong("completed_ns") >= call.getLong("t_ns")) {
                    "ASCII IC call did not complete with its real Boolean return value"
                }
                check(
                    recipient.opt("returned") is Boolean && recipient.getBoolean("on_main_thread") &&
                        recipient.getString("method") == (if (action == KeyEvent.ACTION_DOWN) "onKeyDown" else "onKeyUp") &&
                        recipient.getLong("completed_ns") >= recipient.getLong("t_ns") &&
                        recipient.getLong("t_ns") >= call.getLong("t_ns") &&
                        recipient.getInt("field_id") == id && call.getInt("field_id") == id &&
                        recipient.getInt("editor_object") == System.identityHashCode(this) && call.getInt("editor_object") == System.identityHashCode(this) &&
                        recipient.getLong("down_time_ms") == call.getLong("down_time_ms") &&
                        recipient.getLong("event_time_ms") == call.getLong("event_time_ms"),
                ) { "ASCII IC event did not reach this real editor View once" }
            }
            check(sent[0].getString("text") == "0:${KeyEvent.KEYCODE_A}" && received[0].getInt("action") == KeyEvent.ACTION_DOWN) {
                "ASCII down/up delivery was reordered"
            }
        }

        fun successfulFinalizations(value: String): List<JSONObject> = snapshotCalls().filter {
            it.optBoolean("returned") && (
                (it.optString("method") == "commitText" && it.optString("text") == value) ||
                    (it.optString("method") == "finishComposingText" && it.optString("composing_before") == value && it.isNull("composing_after"))
                )
        }

        fun checkVirtualAsciiTextDelivery(releasedNs: Long, withChinese: Boolean = false) {
            val recorded = snapshotCalls()
            val ascii = recorded.filter { it.optString("method") == "commitText" && it.optString("text") == "a" }
            check(ascii.size == 1 && successfulFinalizations("a").size == 1) { "Virtual ASCII a needs exactly one real text finalization" }
            val call = ascii.single()
            check(
                call.opt("returned") == true && call.getBoolean("on_main_thread") &&
                    call.getInt("field_id") == id && call.getInt("editor_object") == System.identityHashCode(this) &&
                    releasedNs <= call.getLong("t_ns") && call.getLong("t_ns") <= call.getLong("completed_ns") &&
                    call.isNull("composing_before") && call.isNull("composing_after"),
            ) { "Virtual ASCII text did not complete once in this actual editor" }
            check(recorded.none { it.optString("method") == "sendKeyEvent" })
            check(snapshotKeyRecipients().isEmpty())
            val preedit = recorded.filter { it.optString("method") == "setComposingText" }
            val native = successfulFinalizations("你")
            check(preedit.map { it.getString("text") } == if (withChinese) listOf("6", "64") else emptyList())
            check(native.size == if (withChinese) 1 else 0)
            val textCalls = recorded.filter {
                it.optString("method") == "commitText" || (it.optString("method") == "finishComposingText" && !it.isNull("composing_before"))
            }
            check(textCalls == ascii + native) { "Virtual ASCII sequence contains an extra text finalization" }
            var completed = call.getLong("completed_ns")
            (preedit + native).forEach { next ->
                check(
                    next.opt("returned") == true && next.getBoolean("on_main_thread") &&
                        next.getInt("field_id") == id && next.getInt("editor_object") == System.identityHashCode(this) &&
                        completed <= next.getLong("t_ns") && next.getLong("t_ns") <= next.getLong("completed_ns"),
                ) { "Chinese preedit/finalization preceded virtual ASCII completion" }
                completed = next.getLong("completed_ns")
            }
        }
    }

    /** Uses the existing registry so the unchanged T08 baseline need not import a future enum. */
    private class ManualLanguagePreference {
        private val key = "manual_language_mode"
        private val delegate = AppPrefs.defaultInstance().keyboard.preferenceDelegates[key]
        val implemented: Boolean = delegate != null
        private val store = delegate?.sharedPreferences
        private val originallyPresent = store?.contains(key) == true
        private val originalRaw = if (originallyPresent) checkNotNull(store).getString(key, null) else null

        init {
            check(delegate == null || delegate is PreferenceDelegate.SerializableDelegate<*>) { "Manual language preference contract changed" }
            check(!originallyPresent || originalRaw in setOf("unset", "native", "ascii")) { "Unexpected manual language wire value" }
        }

        fun state(): JSONObject = json(
            "status" to if (implemented) "registered" else "NOT_APPLICABLE_T09_PREF_NOT_IMPLEMENTED",
            "key" to key,
            "exists" to (store?.contains(key) == true),
            "raw" to if (store?.contains(key) == true) store.getString(key, null) else null,
        )

        fun original(): JSONObject = json(
            "status" to if (implemented) "registered" else "NOT_APPLICABLE_T09_PREF_NOT_IMPLEMENTED",
            "key" to key,
            "exists" to originallyPresent,
            "raw" to originalRaw,
        )

        fun resetUnset() {
            store?.let {
                check(it.edit().remove(key).commit()) { "Manual language reset did not persist" }
                check(!it.contains(key))
            }
        }

        fun verifyRestored() {
            store?.let {
                check(it.contains(key) == originallyPresent && (!originallyPresent || it.getString(key, null) == originalRaw)) {
                    "Manual language original key presence/raw bytes changed during cleanup"
                }
            }
        }

        fun restore() {
            store?.let {
                val edit = it.edit()
                if (originallyPresent) edit.putString(key, checkNotNull(originalRaw)) else edit.remove(key)
                check(edit.commit()) { "Manual language restoration did not persist" }
                check(it.contains(key) == originallyPresent && (!originallyPresent || it.getString(key, null) == originalRaw)) {
                    "Manual language original key presence/raw bytes did not restore"
                }
            }
        }
    }

    private class Runner(val instrumentation: Instrumentation, val baseline: Boolean, val schemaId: String) {
        val runId = UUID.randomUUID().toString()
        var collectionComplete = false
        val output: File by lazy {
            File(instrumentation.targetContext.cacheDir, "t09-mixed-input").apply {
                check(!exists() || deleteRecursively()) { "Could not clear previous test-only report" }
                check(mkdirs())
            }
        }
        val results = JSONArray()
        val regressions = JSONArray()
        val rapidRegressions = JSONArray()
        val macroRegressions = JSONArray()
        lateinit var rapidSession: RimeSession
        val themeHashes = JSONObject()
        val effectiveThemes = JSONObject()
        var manualPreference: ManualLanguagePreference? = null
        val manualPreferenceCleanup = JSONObject()
        var theme = ""
        var task = ""
        var ledger = JSONArray()
        var prepared = JSONObject()
        lateinit var activity: Activity
        lateinit var chat: RecordingEditor
        lateinit var restricted: RecordingEditor
        lateinit var current: RecordingEditor
        lateinit var manager: InputMethodManager
        lateinit var service: TrimeInputMethodService
        val events = Collections.synchronizedList(mutableListOf<JSONObject>())

        fun <T> main(block: () -> T): T {
            if (Looper.myLooper() == Looper.getMainLooper()) return block()
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }

        fun phase(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$value\n") })
        fun input() = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }
        fun hasService() = this::service.isInitialized
        fun window(): KeyboardWindow = checkNotNull(input()).di.direct.instance()
        fun keyboard(): Keyboard = window().currentKeyboard
        fun keyboardId(): String = KeyboardWindow::class.java.getDeclaredField("currentKeyboardId")
            .apply { isAccessible = true }.get(window()) as String

        suspend fun until(predicate: () -> Boolean) = withTimeout(30_000) { while (!main(predicate)) delay(50) }

        suspend fun <T> api(block: suspend RimeApi.() -> T): T {
            var outcome: Result<T>? = null
            val job = main { service.postRimeJob { outcome = runCatching { block() } } }
            withTimeout(15_000) { job.join() }
            check(!job.isCancelled) { "Engine/editor query cancelled" }
            return checkNotNull(outcome) { "Editor changed before queued operation" }.getOrThrow()
        }

        fun effectiveThemeIdentity(): JSONObject {
            val configFiles = JSONArray()
            val packaged = JSONObject()
            val sourceIdentity = json("requested_config_id" to theme, "packaged_sha256" to packaged, "source_files" to configFiles)
            val sourceIdentityFile = File(output, "$theme-source-identity.json")
            fun preserveSources() {
                sourceIdentityFile.writeText(sourceIdentity.toString(2) + "\n")
            }
            val dataRoots = listOf("user" to DataManager.userDataDir, "shared" to DataManager.sharedDataDir)
            // The shipped two themes have only same-file YAML anchors and no DSL
            // resource directives. Exact source bytes and absent custom patches close
            // the theme dependency graph, including packaged Trime fallback layouts.
            for (id in setOf(theme, "trime").sorted()) {
                val asset = instrumentation.targetContext.assets.open("shared/$id.yaml").use { it.readBytes() }
                check(asset.size <= 10 * 1024 * 1024)
                check(!Regex("(?m)^[ \t]*__(?:include|patch|merge|append):").containsMatchIn(asset.toString(Charsets.UTF_8))) {
                    "Theme acquired external DSL dependencies; extend explicit identity capture before running"
                }
                val assetHash = digest(asset)
                packaged.put(id, assetHash)
                preserveSources()
                var selected: File? = null
                for ((origin, directory) in dataRoots) {
                    val source = File(directory, "$id.yaml")
                    val custom = File(directory, "$id.custom.yaml")
                    val record = json(
                        "resource_id" to id,
                        "root" to origin,
                        "source_path" to source.absolutePath,
                        "source_exists" to source.exists(),
                        "custom_path" to custom.absolutePath,
                        "custom_exists" to custom.exists(),
                    )
                    configFiles.put(record)
                    preserveSources()
                    check(!custom.exists()) { "Dedicated theme fixture has a custom override: $custom" }
                    if (source.exists()) {
                        check(
                            source.isFile && source.length() <= 10 * 1024 * 1024 &&
                                source.canonicalPath.startsWith(directory.canonicalPath.trimEnd(File.separatorChar) + File.separator),
                        ) {
                            "Theme source is not a bounded regular file inside its data root"
                        }
                        record.put("sha256", digest(source.readBytes())).put("bytes", source.length())
                        preserveSources()
                        if (selected == null) selected = source
                    }
                }
                val resolved = checkNotNull(selected) { "Actual theme source is unavailable: $id" }
                check(digest(resolved.readBytes()) == assetHash) { "Actual first-priority theme source differs from packaged bytes: $resolved" }
            }
            val active = main {
                check(ThemeManager.prefs.selectedTheme.getValue() == theme)
                check(checkNotNull(input()).theme == ThemeManager.activeTheme) { "InputView retained an old effective theme" }
                ThemeManager.activeTheme
            }
            val serialized = main { diagnosticThemeSnapshot(active).toString() }
            val snapshot = File(output, "$theme-effective-theme.json")
            if (!snapshot.exists()) {
                snapshot.writeText(serialized + "\n")
            } else {
                check(snapshot.readText() == serialized + "\n") { "Effective theme changed during tasks" }
            }
            return json(
                "requested_config_id" to theme, "selected_config_id" to theme,
                "status" to "verified_packaged_source_no_custom_overrides",
                "dependency_scope" to "active canonical theme plus packaged Trime compatible fallback; source YAML has no external DSL references",
                "packaged_sha256" to packaged, "source_files" to configFiles,
                "effective_theme_file" to snapshot.name, "effective_theme_sha256" to digest(snapshot.readBytes()),
                "effective_type_coverage_explicit" to true,
                "source_identity_file" to sourceIdentityFile.name, "source_identity_sha256" to digest(sourceIdentityFile.readBytes()),
                "fallback_keyboards" to JSONObject(active.fallbackKeyboards.toSortedMap()),
            )
        }

        // Theme serializers are deliberately read-only. Read actual immutable model
        // fields without invoking serializers, fonts/resources or the action cache.
        fun diagnosticThemeSnapshot(active: Theme): JSONObject {
            check(Looper.myLooper() == Looper.getMainLooper())
            var visited = 0
            fun project(value: Any?, depth: Int): Any {
                check(depth <= 32 && ++visited <= 1_000_000) { "Theme diagnostic exceeded bounded model graph" }
                return when (value) {
                    null -> JSONObject.NULL

                    is String, is Boolean -> value

                    is Number -> value.also {
                        check(it is Byte || it is Short || it is Int || it is Long || it is Float || it is Double) { "Unknown theme numeric type" }
                        check(it.toDouble().isFinite()) { "Non-finite theme configuration" }
                    }

                    is Enum<*> -> {
                        check(value.declaringJavaClass.name.startsWith("com.osfans.trime.data.theme.model.")) { "Unknown theme enum type" }
                        json("__class" to value.declaringJavaClass.name, "name" to value.name)
                    }

                    is List<*> -> JSONArray().apply { value.forEach { put(project(it, depth + 1)) } }

                    is Map<*, *> -> JSONObject().apply {
                        check(value.keys.all { it is String }) { "Unknown theme map key type" }
                        value.entries.sortedBy { it.key as String }.forEach { (key, item) -> put(key as String, project(item, depth + 1)) }
                    }

                    else -> {
                        val type = value.javaClass
                        check(type == Theme::class.java || type.name.startsWith("com.osfans.trime.data.theme.model.")) {
                            "Unknown effective theme model type: ${type.name}"
                        }
                        val fields = type.declaredFields.filterNot { field ->
                            Modifier.isStatic(field.modifiers) || field.isSynthetic ||
                                (type == Theme::class.java && field.name in setOf("fonts\$delegate", "actionCache")) ||
                                (type.name == "com.osfans.trime.data.theme.model.LiquidKeyboard" && field.name == "data")
                        }.sortedBy { it.name }
                        check(fields.isNotEmpty() || type.name == "com.osfans.trime.data.theme.model.MaybeStringList\$Empty") {
                            "Theme model has no explicit configuration fields: ${type.name}"
                        }
                        json("__class" to type.name).apply {
                            fields.forEach { field ->
                                field.isAccessible = true
                                put(field.name, project(field.get(value), depth + 1))
                            }
                        }
                    }
                }
            }
            return project(active, 0) as JSONObject
        }

        suspend fun state(): JSONObject {
            val native = api {
                json(
                    "ascii_mode" to getRuntimeOption("ascii_mode"),
                    "raw_input" to getRawInput(),
                    "preedit" to compositionCached.preedit,
                    "generation" to candidateSnapshotCached.generation,
                    "snapshot_editor" to candidateSnapshotCached.editorToken,
                    "snapshot_request" to candidateSnapshotCached.requestId,
                    "schema_id" to selectedSchemaId(),
                    "locked" to t9Cached.segments.count { it.locked },
                )
            }
            return main {
                native.put("editor_token", service.editorToken).put("field_id", service.currentInputEditorInfo?.fieldId)
                    .put("keyboard_id", keyboardId()).put("keyboard_object", System.identityHashCode(keyboard()))
                    .put("t9_layout", keyboard().isT9Layout).put("shifted", keyboard().isShifted)
                    .put("editor_text", current.text.toString())
                    .put("selection", JSONArray(listOf(current.selectionStart, current.selectionEnd)))
                    .put("composing_span", JSONArray(listOf(BaseInputConnection.getComposingSpanStart(current.text), BaseInputConnection.getComposingSpanEnd(current.text))))
            }
        }

        suspend fun settled(): JSONObject = withTimeout(15_000) {
            // Main attachments and the serial IME queue can enqueue native work in either turn.
            // Compare actual stable states after two queue barriers; no fixed sleep substitutes
            // for a queue barrier, and no mutable cached-only mode is treated as native evidence.
            var previous: String? = null
            var count = 0
            while (true) {
                main { }
                val value = state()
                val signature = value.toString()
                if (signature == previous) count++ else count = 0
                if (count >= 2) return@withTimeout value
                previous = signature
                delay(80)
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        }

        fun screenshot(label: String) {
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                try {
                    File(output, "$theme-$task-$label.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
            } ?: error("Screenshot unavailable")
        }

        fun save() {
            val corpus = JSONArray(tasks.map { json("id" to it.id, "target" to it.target, "input_type" to it.inputType) })
            File(output, "report.json").writeText(
                json(
                    "format" to 1, "baseline" to baseline,
                    "run_id" to runId, "collection_complete" to collectionComplete,
                    "driver" to "logical_visible_key_actions", "physical_touch_benchmark" to false,
                    "performance_acceptance" to false, "version" to BuildConfig.VERSION_NAME,
                    "schema_id" to schemaId,
                    "sdk" to Build.VERSION.SDK_INT, "fingerprint" to Build.FINGERPRINT,
                    "corpus" to corpus, "corpus_json" to corpus.toString(),
                    "corpus_sha256" to digest(corpus.toString().toByteArray(Charsets.UTF_8)),
                    "theme_asset_sha256" to themeHashes, "effective_theme_identity" to effectiveThemes,
                    "manual_language_preference" to (manualPreference?.original() ?: json("status" to "NOT_CAPTURED")),
                    "learning" to "real EditorInfo no-personalized-learning flag; normal privacy policy applies",
                    "results" to results, "regressions" to regressions, "rapid_regressions" to rapidRegressions, "macro_regressions" to macroRegressions,
                ).toString(2) + "\n",
            )
            File(output, "$theme-$task-actions.json").writeText(ledger.toString(2) + "\n")
        }

        data class VisibleKey(val keyboard: Keyboard, val index: Int, val view: KeyView, val action: KeyAction, val label: String, val behavior: KeyBehavior)

        fun visibleKeys(): List<VisibleKey> = main {
            val view = checkNotNull(input())
            val active = keyboard()
            val visibleKeyboard = descendants(view).filterIsInstance<KeyboardView>().single { it.isShown }
            active.keys.flatMapIndexed { index, key ->
                val keyView = visibleKeyboard.getChildAt(index) as? KeyView ?: return@flatMapIndexed emptyList()
                val visible = Rect()
                if (!keyView.isShown || !keyView.isEnabled || keyView.width <= 0 || !keyView.getLocalVisibleRect(visible)) return@flatMapIndexed emptyList()
                buildList {
                    key.getAction(KeyBehavior.CLICK)?.let { add(VisibleKey(active, index, keyView, it, key.getLabel(), KeyBehavior.CLICK)) }
                    if (keyView.hasLongPress && key.popup.isEmpty() && key.hasAction(KeyBehavior.LONG_CLICK)) {
                        key.getAction(KeyBehavior.LONG_CLICK)?.let { add(VisibleKey(active, index, keyView, it, key.symbolLabel, KeyBehavior.LONG_CLICK)) }
                    }
                }
            }
        }

        fun describe(action: KeyAction) = json(
            "code" to action.code,
            "modifier" to action.modifier,
            "text" to action.text,
            "commit" to action.commit,
            "select" to action.select,
            "toggle" to action.toggle,
            "command" to action.command,
        )

        fun outputOf(action: KeyAction): String? = main {
            if (action.commit.isNotEmpty()) return@main action.commit.takeIf { it.length == 1 }
            val text = action.getText(keyboard())
            if (text.isNotEmpty()) return@main text.takeIf { it.length == 1 }
            if (action.code == KeyEvent.KEYCODE_SPACE) return@main " "
            if (action.isFunctional || action.isModifierKey || action.select.isNotEmpty() || action.toggle.isNotEmpty()) return@main null
            val active = keyboard()
            val prefs = AppPrefs.defaultInstance().keyboard
            val hookShift = (prefs.hookShiftSpace.getValue() && action.code == KeyEvent.KEYCODE_SPACE) ||
                (prefs.hookShiftNum.getValue() && action.code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) ||
                (prefs.hookShiftSymbol.getValue() && action.code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH) ||
                (prefs.hookShiftSymbol.getValue() && action.code in setOf(KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD))
            var modifiers = when {
                action.modifier == 0 && active.isOnlyShiftOn && hookShift -> 0
                action.modifier == 0 -> active.modifier
                else -> action.modifier
            }
            // Match actual enqueueKey NUMPAD handling. Android's character map cannot resolve
            // synthetic Rime symbol codes, which are nevertheless real visible key actions.
            if (action.code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_EQUALS) modifiers = modifiers or KeyEvent.META_NUM_LOCK_ON
            if (!KeyCode.isStandardKey(action.code)) return@main KeyCode.getDisplayLabel(action.code, modifiers).takeIf { it.length == 1 }
            virtualKeyCharacterMap.get(action.code, modifiers).takeIf { it > 0 }?.toChar()?.toString()
        }

        suspend fun key(selected: VisibleKey, role: String = "input") {
            val before = settled()
            val record = json(
                "seq" to ledger.length() + 1,
                "driver" to "logical_key_action",
                "behavior" to if (selected.behavior == KeyBehavior.CLICK) "click" else "long",
                "role" to role,
                "key_index" to selected.index,
                "displayed_label" to selected.label,
                "resolved_action" to describe(selected.action),
                "before" to before,
            )
            ledger.put(record) // Keep an attempted action even when dispatch throws.
            save()
            main {
                check(selected.view.isShown && selected.view.isEnabled) { "Resolved KeyView became hidden" }
                val active = keyboard()
                val activeView = descendants(checkNotNull(input())).filterIsInstance<KeyboardView>().single { it.isShown }
                check(active === selected.keyboard && activeView.getChildAt(selected.index) === selected.view) { "Resolved key belongs to an old keyboard" }
                check(active.keys[selected.index].getAction(selected.behavior) === selected.action) { "Resolved key binding changed before dispatch" }
                selected.view.onPress?.invoke() ?: error("KeyView lacks actual press callback")
                try {
                    if (selected.behavior == KeyBehavior.CLICK) {
                        selected.view.onRelease?.invoke(KeyBehavior.CLICK, false) ?: error("KeyView lacks actual release callback")
                    } else {
                        check(selected.view.hasLongPress && !selected.view.hasPopup)
                        check(selected.view.performLongClick()) { "Actual long action was not handled" }
                        selected.view.onRelease?.invoke(KeyBehavior.LONG_CLICK, true)
                    }
                    record.put("dispatched", true)
                } finally {
                    selected.view.cancelGesture()
                }
            }
            val after = settled()
            check(after.getLong("editor_token") == before.getLong("editor_token")) { "Action crossed an editor boundary" }
            record.put("after", after)
            if (after.getString("keyboard_id") != before.getString("keyboard_id")) screenshot("layout-${ledger.length()}")
            save()
        }

        suspend fun layout(target: String): Boolean {
            val choice = visibleKeys().firstOrNull { it.action.code == KeyEvent.KEYCODE_EISU && it.action.select == target } ?: return false
            key(choice, if (target in setOf(".ascii", ".native")) "mode" else "layout")
            return true
        }

        suspend fun letters() {
            fun available() = main { keyboard().keys.any { it.code == KeyEvent.KEYCODE_A } && keyboard().keys.any { it.code == KeyEvent.KEYCODE_Z } }
            if (available()) return
            // Only current displayed actions are executable. No listener .ascii/switchKeyboard
            // substitute is allowed in task costs. Both actual paths and failures are preserved.
            if (layout(".ascii") && available()) return
            if (layout("letter") && available()) return
            if (layout("number") && layout("letter") && available()) return
            if (theme == "trime") {
                if (layout("symbols")) layout("mini")
            }
            throw Unsupported("No verified visible route to a letter layout; available actions=${visibleKeys().map { describe(it.action) }}", "no_visible_letter_route")
        }

        suspend fun returnT9() {
            if (main { keyboard().isT9Layout } && !settled().getBoolean("ascii_mode")) return
            val returnedDirectly = layout(".native") || layout(".return") || layout(".default")
            if (!returnedDirectly || !main { keyboard().isT9Layout }) {
                if (!layout("number") || !layout(".default")) throw Unsupported("No visible return to T9")
            }
            check(main { keyboard().isT9Layout }) { "Visible return did not select a nine-key layout" }
            check(!settled().getBoolean("ascii_mode")) { "Visible T9 return retained ASCII mode" }
        }

        suspend fun digit(value: Char) {
            val code = KeyEvent.KEYCODE_0 + value.digitToInt()
            key(
                visibleKeys().firstOrNull { it.behavior == KeyBehavior.CLICK && it.action.code == code }
                    ?: throw Unsupported("Actual T9 digit <$value> is missing"),
            )
        }

        suspend fun lockVisibleNi() {
            val before = settled()
            val choice = main {
                descendants(checkNotNull(input())).filterIsInstance<T9DisambiguationView>()
                    .flatMap(::descendants).filterIsInstance<TextView>().singleOrNull {
                        it.isShown && it.isEnabled && it.contentDescription?.toString() == "ni" && it.hasOnClickListeners()
                    }
            } ?: throw Unsupported("Exact ni syllable choice is not currently visible")
            val item = json(
                "seq" to ledger.length() + 1,
                "driver" to "logical_visible_syllable_click",
                "behavior" to "click",
                "role" to "syllable",
                "displayed_label" to choice.text.toString(),
                "before" to before,
            )
            ledger.put(item)
            save()
            main {
                check(choice.performClick())
                item.put("dispatched", true)
            }
            item.put("after", settled())
            check(item.getJSONObject("after").getInt("locked") > 0) { "Actual syllable choice did not lock ni" }
            save()
        }

        suspend fun character(value: Char) {
            if (value.isLetter() && value.code < 128) {
                letters()
                val shifted = main { keyboard().isShifted }
                if (shifted != value.isUpperCase()) {
                    val shift = visibleKeys().firstOrNull { it.behavior == KeyBehavior.CLICK && it.action.isModifierKey && it.action.code in setOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT) }
                        ?: throw Unsupported("No visible Shift for <$value>")
                    key(shift, "shift")
                }
            }
            var choice = visibleKeys().firstOrNull { outputOf(it.action) == value.toString() }
            if (choice == null && (value.isDigit() || value in "+-=/.")) {
                if (layout("number")) choice = visibleKeys().firstOrNull { outputOf(it.action) == value.toString() }
            }
            if (choice == null && theme == "trime") {
                if (layout("symbols")) choice = visibleKeys().firstOrNull { outputOf(it.action) == value.toString() }
            }
            val found = choice ?: throw Unsupported("Character <$value> has no supported visible click/long route")
            key(found)
        }

        suspend fun candidate(text: String?) {
            val before = settled()
            val holder = main {
                descendants(checkNotNull(input())).filterIsInstance<RecyclerView>().flatMap { list ->
                    list.children.mapNotNull { list.getChildViewHolder(it) as? CandidateViewHolder }
                }.firstOrNull {
                    it.itemView.isShown && it.snapshot.editorToken == before.getLong("editor_token") &&
                        it.snapshot.generation == before.getLong("generation") && it.snapshot.requestId == before.getLong("snapshot_request") &&
                        it.itemView.getGlobalVisibleRect(Rect()) && (text == null || it.text == text)
                }
            } ?: throw Unsupported("Target candidate <$text> is not currently visible/bound; no hidden native selection")
            val record = json(
                "seq" to ledger.length() + 1,
                "driver" to "logical_visible_candidate_click",
                "behavior" to "click",
                "role" to "candidate",
                "candidate" to holder.text,
                "candidate_index" to holder.idx,
                "candidate_generation" to holder.snapshot.generation,
                "before" to before,
            )
            ledger.put(record)
            save()
            main {
                val latest = checkNotNull(input()).di.direct.instance<RimeSession>().run { candidateSnapshotCached }
                check(service.isCurrentEditor(holder.snapshot.editorToken) && holder.snapshot == latest) { "Candidate became stale before the visible click" }
                check(holder.itemView.performClick()) { "Actual candidate row click failed" }
                record.put("dispatched", true)
            }
            val after = settled()
            record.put("after", after)
            check(after.getLong("editor_token") == before.getLong("editor_token"))
            check(after.getString("raw_input").isEmpty()) { "Candidate click left pending raw input" }
            save()
        }

        suspend fun prepare(item: Task) {
            // A failed preparation must retain this task's empty attempt frame,
            // never the previous task's ledger or input-connection calls.
            ledger = JSONArray()
            prepared = JSONObject()
            chat.calls.clear()
            restricted.calls.clear()
            chat.keyRecipients.clear()
            restricted.keyRecipients.clear()
            synchronized(events) { events.clear() }
            manualPreference?.resetUnset() // Test fixture isolation, excluded from action costs.
            val oldEditorToken = main { service.editorToken }
            current = chat
            main {
                chat.setText("")
                chat.setSelection(0)
                chat.requestFocus()
                manager.restartInput(chat) // Re-read user mode at a genuine new editor boundary.
                manager.showSoftInput(chat, 0)
            }
            until { input() != null && service.currentInputEditorInfo?.fieldId == chat.id && service.editorToken != oldEditorToken }
            api {
                clearComposition()
                check(selectSchema(schemaId))
                setRuntimeOption("ascii_mode", false)
            }
            // Explicit preparation only: not a user baseline action, never enters ledger.
            main { window().switchKeyboard(".default") }
            until { input() != null && keyboard().isT9Layout }
            settled()
            main {
                restricted.inputType = item.inputType
                restricted.imeOptions = EditorInfo.IME_ACTION_DONE or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG
                restricted.setText("")
                restricted.setSelection(0)
            }
            if (item.inputType != InputType.TYPE_CLASS_TEXT) {
                current = restricted
                main {
                    restricted.requestFocus()
                    manager.restartInput(restricted)
                    manager.showSoftInput(restricted, 0)
                }
                until { input() != null && service.currentInputEditorInfo?.fieldId == restricted.id && service.currentInputEditorInfo?.inputType == item.inputType }
            }
            prepared = settled().put("manual_language_preference", manualPreference?.state() ?: JSONObject.NULL)
                .put("effective_theme_sha256", effectiveThemes.getJSONObject(theme).getString("effective_theme_sha256"))
            if (item.inputType == InputType.TYPE_CLASS_TEXT) {
                check(prepared.getBoolean("t9_layout") && !prepared.getBoolean("ascii_mode"))
            } else {
                check(prepared.getBoolean("ascii_mode")) { "Real restricted editor did not force ASCII" }
            }
            chat.calls.clear()
            restricted.calls.clear()
            chat.keyRecipients.clear()
            restricted.keyRecipients.clear()
            synchronized(events) { events.clear() }
            screenshot("start")
        }

        fun counts(): JSONObject {
            val steps = (0 until ledger.length()).map { ledger.getJSONObject(it) }
            return json(
                "total_actions" to steps.size,
                "dispatched_actions" to steps.count { it.optBoolean("dispatched") },
                "logical_clicks" to steps.count { it.getString("driver") == "logical_key_action" && it.getString("behavior") == "click" },
                "logical_longs" to steps.count { it.getString("behavior") == "long" },
                "candidate_clicks" to steps.count { it.getString("role") == "candidate" },
                "shift_actions" to steps.count { it.getString("role") == "shift" },
                "layout_actions" to steps.count { it.getString("role") == "layout" },
                "mode_actions" to steps.count { it.getString("role") == "mode" },
                "actual_stable_layout_changes" to steps.count { it.has("after") && it.getJSONObject("before").getString("keyboard_id") != it.getJSONObject("after").getString("keyboard_id") },
                "actual_stable_ascii_changes" to steps.count { it.has("after") && it.getJSONObject("before").getBoolean("ascii_mode") != it.getJSONObject("after").getBoolean("ascii_mode") },
            )
        }

        suspend fun runTask(item: Task) {
            task = item.id
            val result = json("theme" to theme, "task" to task, "target" to item.target, "status" to "incomplete")
            results.put(result)
            try {
                prepare(item)
                result.put("prepared", prepared)
                if (item.id == "b_site") {
                    character('B')
                    returnT9()
                    "9426".forEach { digit(it) }
                    candidate("站")
                } else {
                    item.target.forEach { character(it) }
                }
                // A mixed ordinary task includes the actual route back to Chinese T9.
                // This is measured input work, not preparation or an uncounted API reset.
                if (item.inputType == InputType.TYPE_CLASS_TEXT) returnT9()
                val final = settled()
                if (item.inputType == InputType.TYPE_CLASS_TEXT) check(final.getBoolean("t9_layout") && !final.getBoolean("ascii_mode")) { "Ordinary task did not end in Chinese T9" }
                check(final.getString("editor_text") == item.target) { "Exact text mismatch: ${final.getString("editor_text")} != ${item.target}" }
                check(final.getString("raw_input").isEmpty()) { "Task left native composition" }
                check(final.getJSONArray("composing_span").getInt(0) == -1 && final.getJSONArray("composing_span").getInt(1) == -1)
                check(final.getJSONArray("selection").getInt(0) == item.target.length && final.getJSONArray("selection").getInt(1) == item.target.length)
                delay(400)
                check(settled().getString("editor_text") == item.target) { "Editor changed or duplicate commit arrived after completion" }
                if (item.id == "b_site") {
                    check(current.successfulFinalizations("站").size == 1) {
                        "Candidate selection did not forward exactly one successful IC finalization for 站"
                    }
                    val commits = synchronized(events) {
                        events.filter { it.optString("event") == "native_commit" && it.optString("text") == "站" && it.optLong("editor") == final.getLong("editor_token") }
                    }
                    check(commits.size == 1) { "Candidate selection did not emit one native commit snapshot" }
                }
                result.put("final", final).put("status", "passed")
                phase("PASS: T09 $theme ${item.id}: actual ${ledger.length()} logical actions")
            } catch (unsupported: Unsupported) {
                result.put("status", if (baseline) "unsupported" else "failed").put("reason", unsupported.message)
                    .put("reason_code", unsupported.code).put("failure_kind", "visible_route_unsupported")
                phase("${if (baseline) "UNSUPPORTED" else "FAIL"}: T09 $theme ${item.id}: ${unsupported.message}")
            } catch (failure: Throwable) {
                result.put("status", "failed").put("reason", failure.stackTraceToString()).put("failure_kind", "assertion_or_harness_failure")
                phase("FAIL: T09 $theme ${item.id}: ${failure.message}")
            } finally {
                result.put("counts", counts()).put("actions", ledger)
                result.put("ic_calls", JSONArray(current.snapshotCalls()))
                result.put("native_events", JSONArray(synchronized(events) { events.toList() }))
                screenshot("final")
                save()
            }
        }

        suspend fun regression(id: String, block: suspend () -> Unit) {
            task = id
            val item = json("theme" to theme, "id" to id, "status" to "incomplete")
            regressions.put(item)
            try {
                prepare(Task(id, "", InputType.TYPE_CLASS_TEXT))
                item.put("prepared", prepared)
                block()
                item.put("final", settled()).put("status", "passed")
                phase("PASS: T09 $theme $id")
            } catch (unsupported: Unsupported) {
                item.put("status", if (baseline) "unsupported" else "failed").put("reason", unsupported.message)
                    .put("reason_code", unsupported.code).put("failure_kind", "visible_route_unsupported")
                phase("${if (baseline) "UNSUPPORTED" else "FAIL"}: T09 $theme $id: ${unsupported.message}")
            } catch (failure: Throwable) {
                item.put("status", "failed").put("reason", failure.stackTraceToString()).put("failure_kind", "assertion_or_harness_failure")
                phase("FAIL: T09 $theme $id: ${failure.message}")
            } finally {
                item.put("counts", counts()).put("actions", ledger).put("ic_calls", JSONArray(current.snapshotCalls()))
                    .put("native_events", JSONArray(synchronized(events) { events.toList() }))
                screenshot("final")
                save()
            }
        }

        // Post-implementation-only queue test; never contributes to the fixed task costs.
        fun rapidIdentity(): JSONObject = main {
            check(checkNotNull(input()).theme == ThemeManager.activeTheme)
            check(ThemeManager.prefs.selectedTheme.getValue() == theme)
            val cached = rapidSession.run {
                json(
                    "schema_id" to statusCached.schemaId,
                    "generation" to candidateSnapshotCached.generation,
                    "snapshot_editor" to candidateSnapshotCached.editorToken,
                    "snapshot_request" to candidateSnapshotCached.requestId,
                )
            }
            cached.put("editor_token", service.editorToken).put("field_id", service.currentInputEditorInfo?.fieldId)
                .put("theme", theme).put("theme_object", System.identityHashCode(ThemeManager.activeTheme))
                .put("input_object", System.identityHashCode(checkNotNull(input())))
        }

        fun rapidKey(selected: VisibleKey, role: String, anchor: JSONObject, release: CompletableDeferred<Unit>) {
            check(Looper.myLooper() == Looper.getMainLooper())
            check(!release.isCompleted) { "FIFO gate was released between callbacks" }
            val identity = rapidIdentity()
            check(identity.toString() == anchor.toString()) { "Rapid callbacks crossed editor/schema/snapshot/theme ownership" }
            val active = keyboard()
            val activeView = descendants(checkNotNull(input())).filterIsInstance<KeyboardView>().single { it.isShown }
            val visible = Rect()
            check(selected.view.isShown && selected.view.isEnabled && selected.view.getLocalVisibleRect(visible)) { "Rapid KeyView is not visible" }
            check(active === selected.keyboard && activeView.getChildAt(selected.index) === selected.view) { "Rapid key belongs to an old keyboard" }
            check(active.keys[selected.index].getAction(selected.behavior) === selected.action) { "Rapid action changed before callback" }
            val record = json(
                "seq" to ledger.length() + 1, "driver" to "logical_key_action",
                "behavior" to if (selected.behavior == KeyBehavior.CLICK) "click" else "long",
                "role" to role, "key_index" to selected.index, "displayed_label" to selected.label,
                "resolved_action" to describe(selected.action), "identity" to identity,
                "keyboard_id" to keyboardId(), "t9_layout" to active.isT9Layout, "keyboard_object" to System.identityHashCode(active),
                "key_view_object" to System.identityHashCode(selected.view), "visible_rect" to visible.toShortString(),
                "callback_begin_ns" to SystemClock.elapsedRealtimeNanos(), "gate_released" to release.isCompleted,
            )
            ledger.put(record)
            selected.view.onPress?.invoke() ?: error("Rapid KeyView lacks actual press callback")
            try {
                if (selected.behavior == KeyBehavior.CLICK) {
                    selected.view.onRelease?.invoke(KeyBehavior.CLICK, false) ?: error("Rapid KeyView lacks actual release callback")
                } else {
                    check(selected.view.hasLongPress && !selected.view.hasPopup)
                    check(selected.view.performLongClick()) { "Actual visible rapid long action was not handled" }
                    selected.view.onRelease?.invoke(KeyBehavior.LONG_CLICK, true)
                }
                record.put("dispatched", true).put("callback_end_ns", SystemClock.elapsedRealtimeNanos())
                    .put("identity_after", rapidIdentity()).put("keyboard_id_after", keyboardId()).put("t9_layout_after", keyboard().isT9Layout)
                check(record.getJSONObject("identity_after").toString() == anchor.toString())
            } finally {
                selected.view.cancelGesture()
            }
        }

        private suspend fun retainLateFailureState(item: JSONObject) {
            withContext(NonCancellable) {
                try {
                    withTimeout(5_000) {
                        delay(400)
                        item.put("late_failure_state", settled())
                    }
                } catch (failure: Throwable) {
                    item.put("late_failure_state_error", failure.stackTraceToString())
                }
                save()
            }
        }

        suspend fun verifyRapid(withChinese: Boolean) {
            val id = if (withChinese) "rapid_cn_en_a_cn_64_space" else "rapid_cn_en_a_cn"
            val expected = if (withChinese) "a你" else "a"
            task = id
            val item = json(
                "theme" to theme,
                "id" to id,
                "status" to "incomplete",
                "expected_text" to expected,
                "scope" to "explicit_visible_commands_fifo",
                "excluded_from_fixed_task_costs" to true,
                "interkey_queue_barriers" to 0,
                "interkey_native_queries" to 0,
            )
            item.put("submission_modes", json("a" to "virtual_printable_ascii_to_real_ic_text", "你" to if (withChinese) "native_commit_to_real_ic_finalization" else "not_requested"))
            rapidRegressions.put(item)
            try {
                prepare(Task(id, "", InputType.TYPE_CLASS_TEXT))
                item.put("prepared", prepared)
                check(prepared.getString("raw_input").isEmpty() && !prepared.getBoolean("ascii_mode") && prepared.getBoolean("t9_layout"))
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val gateProof = json("original_service_post_rime_job" to true, "single_main_dispatch" to true)
                item.put("gate", gateProof)
                val gate = main {
                    service.postRimeJob {
                        gateProof.put("entered_ns", SystemClock.elapsedRealtimeNanos())
                        entered.complete(Unit)
                        release.await()
                    }
                }
                try {
                    withTimeout(5_000) { entered.await() }
                    main {
                        val anchor = rapidIdentity()
                        check(
                            anchor.getLong("editor_token") == prepared.getLong("editor_token") &&
                                anchor.getLong("generation") == prepared.getLong("generation") && anchor.getString("schema_id") == schemaId,
                        )
                        gateProof.put("identity", anchor)
                        val english = visibleKeys().singleOrNull {
                            it.behavior == KeyBehavior.LONG_CLICK &&
                                it.action.code == KeyEvent.KEYCODE_EISU && it.action.select == ".ascii"
                        }
                            ?: throw Unsupported("No actual visible T9 123 long EN action", "rapid_english_route_unavailable")
                        val englishKey = english.keyboard.keys[english.index]
                        check(englishKey.getLabel() == "123" && englishKey.hint == "EN" && !AppPrefs.defaultInstance().keyboard.hideKeyHint.getValue()) {
                            "Rapid English entry is not the visible T9 123 EN hint"
                        }
                        rapidKey(english, "english", anchor, release)
                        check(!keyboard().isT9Layout) { "Accepted English command did not expose its real letter layout" }
                        // Isolated notification control: an old Chinese payload must not clear
                        // Shift in the newly accepted English layout while its native job waits.
                        keyboard().setShifted(true, true)
                        check(keyboard().isShifted)
                        window().onRimeOptionUpdated(RimeMessage.OptionMessage.Data("ascii_mode", false))
                        check(keyboard().isShifted) { "Old Chinese notification cleared pending English Shift" }
                        gateProof.put("pending_english_shift_survived_old_chinese_notification", true)
                        gateProof.put("shift_control_driver", "fixture_modifier_setup_and_direct_option_callback")
                        keyboard().setShifted(false, false)
                        val a = visibleKeys().singleOrNull { it.behavior == KeyBehavior.CLICK && it.action.code == KeyEvent.KEYCODE_A && outputOf(it.action) == "a" }
                            ?: throw Unsupported("No actual visible lowercase a key", "rapid_letter_route_unavailable")
                        rapidKey(a, "ascii_a", anchor, release)
                        val chinese = visibleKeys().singleOrNull {
                            it.behavior == KeyBehavior.CLICK &&
                                ((it.action.code == KeyEvent.KEYCODE_EISU && it.action.select == ".native") || it.action.toggle == "ascii_mode")
                        }
                            ?: throw Unsupported("No actual visible Chinese return command", "rapid_chinese_route_unavailable")
                        // Trime may still resolve its visible base Mode_switch while native ASCII
                        // is queued; record that actual action rather than inventing an override.
                        rapidKey(chinese, "chinese", anchor, release)
                        check(keyboard().isT9Layout) { "Accepted Chinese command did not expose schema-native T9" }
                        if (withChinese) {
                            for (digit in "64") {
                                val key = visibleKeys().singleOrNull { it.behavior == KeyBehavior.CLICK && it.action.code == KeyEvent.KEYCODE_0 + (digit - '0') }
                                    ?: throw Unsupported("No actual visible T9 $digit key", "rapid_digit_route_unavailable")
                                rapidKey(key, "native_$digit", anchor, release)
                            }
                            val space = visibleKeys().singleOrNull { it.behavior == KeyBehavior.CLICK && it.action.code == KeyEvent.KEYCODE_SPACE }
                                ?: throw Unsupported("No actual visible T9 Space key", "rapid_space_route_unavailable")
                            rapidKey(space, "native_space", anchor, release)
                        }
                    }
                } finally {
                    // Even a failed visible-route assertion must release the original service
                    // queue before cleanup or another task can start.
                    gateProof.put("release_ns", SystemClock.elapsedRealtimeNanos())
                    release.complete(Unit)
                    withContext(NonCancellable) { withTimeout(5_000) { gate.join() } }
                    gateProof.put("joined_ns", SystemClock.elapsedRealtimeNanos()).put("cancelled", gate.isCancelled)
                }
                check(!gate.isCancelled) { "Original queued FIFO gate was cancelled" }
                val final = settled()
                item.put("final", final)
                save()
                check(final.getLong("editor_token") == prepared.getLong("editor_token") && final.getString("schema_id") == schemaId)
                check(
                    final.getString("editor_text") == expected && final.getString("raw_input").isEmpty() &&
                        !final.getBoolean("ascii_mode") && final.getBoolean("t9_layout"),
                ) { "Rapid FIFO changed exact text or final Chinese mode" }
                check(
                    final.getJSONArray("composing_span").toString() == JSONArray(listOf(-1, -1)).toString() &&
                        final.getJSONArray("selection").toString() == JSONArray(listOf(expected.length, expected.length)).toString(),
                )
                val native = synchronized(events) { events.toList() }
                check(native.filter { it.optString("event") == "ascii_option" }.map { it.getBoolean("value") } == listOf(true, false)) {
                    "Rapid native ASCII options were lost, merged or reordered"
                }
                val commits = native.filter { it.optString("event") == "native_commit" && !it.isNull("text") && it.optString("text").isNotEmpty() }
                check(commits.map { it.getString("text") } == if (withChinese) listOf("你") else emptyList()) { "Rapid native commits differ" }
                check(commits.all { it.getLong("editor") == final.getLong("editor_token") })
                current.checkVirtualAsciiTextDelivery(gateProof.getLong("release_ns"), withChinese)
                check(current.successfulFinalizations("你").size == if (withChinese) 1 else 0) { "Chinese text lacks exactly one real IC finalization" }
                delay(400)
                val delayed = settled()
                item.put("post_delay_state", delayed)
                save()
                check(
                    delayed.getString("editor_text") == expected && delayed.getString("raw_input").isEmpty() &&
                        !delayed.getBoolean("ascii_mode") && delayed.getBoolean("t9_layout") &&
                        delayed.getLong("editor_token") == final.getLong("editor_token") && delayed.getString("schema_id") == schemaId &&
                        delayed.getJSONArray("composing_span").toString() == final.getJSONArray("composing_span").toString() &&
                        delayed.getJSONArray("selection").toString() == final.getJSONArray("selection").toString(),
                ) {
                    "Late queued callback changed the completed rapid text/mode/owner"
                }
                item.put("status", "passed")
                phase("PASS: T09 $theme $id")
            } catch (unsupported: Unsupported) {
                item.put("status", "failed").put("reason", unsupported.message).put("reason_code", unsupported.code)
                    .put("failure_kind", "visible_route_unsupported")
                phase("FAIL: T09 $theme $id: ${unsupported.message}")
            } catch (failure: Throwable) {
                item.put("status", "failed").put("reason", failure.stackTraceToString()).put("failure_kind", "assertion_or_harness_failure")
                phase("FAIL: T09 $theme $id: ${failure.message}")
            } finally {
                if (item.optString("status") == "failed") retainLateFailureState(item)
                item.put("counts", counts()).put("actions", ledger).put("ic_calls", JSONArray(current.snapshotCalls()))
                    .put("native_events", JSONArray(synchronized(events) { events.toList() }))
                    .put("key_recipient_calls", JSONArray(current.snapshotKeyRecipients()))
                screenshot("final")
                save()
            }
        }
        suspend fun verifyMacro(bracedKey: Boolean) {
            val id = if (bracedKey) "macro_ascii_key" else "macro_ascii_text"
            val source = if (bracedKey) "{Keyboard_ascii}{a}{Keyboard_native}" else "{Keyboard_ascii}a{Keyboard_native}"
            task = id
            val item = json(
                "theme" to theme, "id" to id, "status" to "incomplete",
                "scope" to "real_on_text_entry_original_fifo", "excluded_from_fixed_task_costs" to true,
                "macro_source" to source, "macro_source_sha256" to digest(source.toByteArray(Charsets.UTF_8)),
                "interkey_queue_barriers" to 0, "interkey_native_queries" to 0,
            )
            item.put("submission_mode", if (bracedKey) "virtual_printable_ascii_to_real_ic_text" else "app_simulate_literal_fallback_commit_proto_to_real_ic_text")
                .put("librime_ascii_commit_claimed", false)
            macroRegressions.put(item)
            try {
                prepare(Task(id, "", InputType.TYPE_CLASS_TEXT))
                item.put("prepared", prepared)
                check(
                    prepared.getString("editor_text").isEmpty() && prepared.getString("raw_input").isEmpty() &&
                        !prepared.getBoolean("ascii_mode") && prepared.getBoolean("t9_layout"),
                )
                // Fixture setup only, outside measured input actions: retain a locked native
                // keyboard in the cache so the real macro's Chinese return must release Shift.
                main {
                    keyboard().setShifted(true, true)
                    check(keyboard().isShifted)
                }
                item.put("native_shift_locked_before_macro", true)
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val gateProof = json("original_service_post_rime_job" to true, "single_main_dispatch" to true)
                item.put("gate", gateProof)
                val gate = main {
                    service.postRimeJob {
                        gateProof.put("entered_ns", SystemClock.elapsedRealtimeNanos())
                        entered.complete(Unit)
                        release.await()
                    }
                }
                try {
                    withTimeout(5_000) { entered.await() }
                    main {
                        val anchor = rapidIdentity()
                        check(
                            anchor.getLong("editor_token") == prepared.getLong("editor_token") &&
                                anchor.getLong("generation") == prepared.getLong("generation") && anchor.getString("schema_id") == schemaId,
                        )
                        gateProof.put("identity", anchor)
                        val actualTheme = ThemeManager.activeTheme
                        val english = actualTheme.resolveAction("Keyboard_ascii")
                        val chinese = actualTheme.resolveAction("Keyboard_native")
                        check(english.code == KeyEvent.KEYCODE_EISU && english.select == ".ascii" && english.commit.isEmpty() && english.text.isEmpty())
                        check(chinese.code == KeyEvent.KEYCODE_EISU && chinese.select == ".native" && chinese.commit.isEmpty() && chinese.text.isEmpty())
                        val record = json(
                            "seq" to 1, "driver" to "logical_input_macro", "behavior" to "macro",
                            "role" to "macro", "macro_source" to source, "identity" to anchor,
                            "resolved_english" to describe(english), "resolved_chinese" to describe(chinese),
                            "callback_begin_ns" to SystemClock.elapsedRealtimeNanos(), "gate_released" to release.isCompleted,
                        )
                        ledger.put(record)
                        val listener = checkNotNull(input()).di.direct.instance<CommonKeyboardActionListener>().listener
                        listener.onText(source) // The real existing mixed-text/key entry; no fake commits or invisible key callback.
                        record.put("dispatched", true).put("callback_end_ns", SystemClock.elapsedRealtimeNanos()).put("identity_after", rapidIdentity())
                        check(!release.isCompleted && record.getJSONObject("identity_after").toString() == anchor.toString())
                        check(keyboard().isT9Layout && keyboard().isShifted) { "Native Shift control cleared before the queued mode completed" }
                        gateProof.put("native_shift_still_locked_before_gate_release", true)
                    }
                } finally {
                    gateProof.put("release_ns", SystemClock.elapsedRealtimeNanos())
                    release.complete(Unit)
                    withContext(NonCancellable) { withTimeout(5_000) { gate.join() } }
                    gateProof.put("joined_ns", SystemClock.elapsedRealtimeNanos()).put("cancelled", gate.isCancelled)
                }
                check(!gate.isCancelled)
                val final = settled()
                item.put("final", final)
                save()
                fun correct(value: JSONObject) = value.getString("editor_text") == "a" && value.getString("raw_input").isEmpty() &&
                    !value.getBoolean("ascii_mode") && !value.getBoolean("shifted") && value.getBoolean("t9_layout") && value.getString("schema_id") == schemaId &&
                    value.getLong("editor_token") == prepared.getLong("editor_token") &&
                    value.getJSONArray("composing_span").toString() == JSONArray(listOf(-1, -1)).toString() &&
                    value.getJSONArray("selection").toString() == JSONArray(listOf(1, 1)).toString()
                check(correct(final)) { "Actual macro reordered English input or final Chinese state" }
                delay(400)
                val delayed = settled()
                item.put("post_delay_state", delayed)
                save()
                check(correct(delayed)) { "Actual macro produced late/duplicate editor input" }
                val native = synchronized(events) { events.toList() }
                check(native.filter { it.optString("event") == "ascii_option" }.map { it.getBoolean("value") } == listOf(true, false))
                val messages = native.filter { it.optString("event") == "native_commit" && !it.isNull("text") && it.optString("text").isNotEmpty() }
                val calls = current.snapshotCalls()
                if (bracedKey) {
                    current.checkVirtualAsciiTextDelivery(gateProof.getLong("release_ns"))
                    check(messages.isEmpty()) { "Braced key fabricated a native ASCII commit" }
                } else {
                    // simulateKeySequence's existing literal fallback publishes CommitProto(a).
                    // This is app fallback evidence, never proof of a librime ASCII commit.
                    check(messages.map { it.getString("text") } == listOf("a") && messages.single().getLong("editor") == final.getLong("editor_token"))
                    check(current.successfulFinalizations("a").size == 1)
                    check(calls.none { it.optString("method") == "sendKeyEvent" && it.optString("text") in setOf("0:${KeyEvent.KEYCODE_A}", "1:${KeyEvent.KEYCODE_A}") })
                }
                item.put("status", "passed")
                phase("PASS: T09 $theme $id")
            } catch (failure: Throwable) {
                item.put("status", "failed").put("reason", failure.stackTraceToString()).put("failure_kind", "assertion_or_harness_failure")
                phase("FAIL: T09 $theme $id: ${failure.message}")
            } finally {
                if (item.optString("status") == "failed") retainLateFailureState(item)
                item.put("counts", counts()).put("actions", ledger).put("ic_calls", JSONArray(current.snapshotCalls()))
                    .put("native_events", JSONArray(synchronized(events) { events.toList() }))
                    .put("key_recipient_calls", JSONArray(current.snapshotKeyRecipients()))
                screenshot("final")
                save()
            }
        }

        suspend fun verifyComposition(locked: Boolean) = regression(if (locked) "locked_ni_symbols" else "raw_64_symbols") {
            "64".forEach { digit(it) }
            if (locked) lockVisibleNi()
            val before = settled()
            check(before.getString("raw_input") == "64")
            if (!layout("symbols")) throw Unsupported("No actual visible symbol action")
            val symbols = settled()
            check(symbols.getString("raw_input") == before.getString("raw_input")) { "Symbols lost native raw input" }
            check(symbols.getString("preedit") == before.getString("preedit") && symbols.getInt("locked") == before.getInt("locked")) { "Symbols changed pending/locked composition" }
            check(symbols.getString("editor_text") == before.getString("editor_text")) { "Symbols committed pending text" }
            returnT9()
            val returned = settled()
            check(returned.getString("raw_input") == "64" && returned.getInt("locked") == before.getInt("locked")) { "T9 return lost pending composition" }
            candidate(null)
            val committed = settled()
            val candidateAction = ledger.getJSONObject(ledger.length() - 1)
            val selected = candidateAction.getString("candidate")
            check(committed.getString("editor_text") == selected && committed.getString("raw_input").isEmpty()) { "Symbol round trip committed unexpected text" }
            check(current.successfulFinalizations(selected).size == 1) { "Visible candidate did not forward exactly one successful IC finalization" }
            check(
                synchronized(events) {
                    events.count { it.optString("event") == "native_commit" && it.optString("text") == selected && it.optLong("editor") == committed.getLong("editor_token") }
                } == 1,
            )
            delay(400)
            check(settled().getString("editor_text") == selected) { "A second candidate commit arrived" }
        }

        suspend fun verifyEditorRestore(manualEnglish: Boolean) = regression(if (manualEnglish) "manual_english_editor_restore" else "chinese_editor_restore") {
            if (manualEnglish) letters()
            val before = settled()
            val policySteps = JSONArray()
            val item = regressions.getJSONObject(regressions.length() - 1)
            item.put("user_selected_mode_before_focus", before).put("editor_focus_steps", policySteps)
            save()
            fun record(label: String, state: JSONObject) {
                policySteps.put(json("driver" to "instrumentation_editor_focus", "label" to label, "state" to state))
                save() // Retain each actual state before an assertion can fail.
            }
            main { manager.hideSoftInputFromWindow(chat.windowToken, 0) }
            until { !service.isInputViewShown }
            main { manager.showSoftInput(chat, 0) }
            until { service.isInputViewShown && input() != null }
            val resumed = settled()
            record("hide_show", resumed)
            check(resumed.getBoolean("ascii_mode") == before.getBoolean("ascii_mode") && resumed.getString("keyboard_id") == before.getString("keyboard_id")) {
                "Hide/show lost the actual user mode/layout"
            }
            for (type in listOf(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED,
            )) {
                main {
                    restricted.inputType = type
                    restricted.imeOptions = EditorInfo.IME_ACTION_DONE or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG
                    restricted.setText("")
                }
                current = restricted
                main {
                    restricted.requestFocus()
                    manager.restartInput(restricted)
                    manager.showSoftInput(restricted, 0)
                }
                until { service.currentInputEditorInfo?.fieldId == restricted.id && service.currentInputEditorInfo?.inputType == type && input() != null }
                val forced = settled()
                record("forced_$type", forced)
                check(forced.getBoolean("ascii_mode")) { "Restricted real editor did not enter ASCII" }
                current = chat
                main {
                    chat.requestFocus()
                    manager.showSoftInput(chat, 0)
                }
                until { service.currentInputEditorInfo?.fieldId == chat.id && input() != null }
                val restored = settled()
                record("ordinary_after_$type", restored)
                check(restored.getBoolean("ascii_mode") == before.getBoolean("ascii_mode") && restored.getString("keyboard_id") == before.getString("keyboard_id")) {
                    "Temporary editor override changed the user's actual mode/layout"
                }
            }
            regressions.getJSONObject(regressions.length() - 1).put("editor_focus_steps", policySteps)
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun run(instrumentation: Instrumentation, baseline: Boolean = true, schemaId: String = "luna_pinyin_t9") {
        instrumentation.waitForIdleSync()
        val result = Bundle()
        val runner = Runner(instrumentation, baseline, schemaId)
        val originalTheme = ThemeManager.prefs.selectedTheme.getValue()
        val preference = AppPrefs.defaultInstance().general.inlinePreeditMode
        val originalInline = preference.getValue()
        var session: RimeSession? = null
        var originalSchema: String? = null
        var originalAscii = false
        var activity: Activity? = null
        var passed = false
        var dedicatedValidated = false
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Dedicated emulator only" }
            check(schemaId.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid fixture schema identifier" }
            check(File(instrumentation.targetContext.getExternalFilesDir(null), "runtime-audit-dedicated").let { it.isFile && it.length() == 0L }) { "Requires empty dedicated marker" }
            dedicatedValidated = true
            runner.manualPreference = ManualLanguagePreference()
            check(baseline || checkNotNull(runner.manualPreference).implemented) { "Acceptance requires the implemented manual language preference" }
            instrumentation.sendStatus(0, Bundle().apply { putString("t09_run_id", runner.runId) })
            preference.setValue(InlinePreeditMode.COMMIT_TEXT_PREVIEW)
            session = runner.main { RimeDaemon.createSession(javaClass.name) }
            runner.rapidSession = checkNotNull(session)
            runBlocking {
                withTimeout(300_000) {
                    checkNotNull(session).runOnReady {
                        originalSchema = selectedSchemaId()
                        originalAscii = getRuntimeOption("ascii_mode")
                    }
                }
            }
            val editor = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ClipEditActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = editor
            runner.activity = editor
            runner.manager = editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            runner.main {
                val row = LinearLayout(editor).apply { orientation = LinearLayout.VERTICAL }
                runner.chat = RecordingEditor(editor).apply {
                    id = View.generateViewId()
                    hint = "T09 ordinary test editor"
                    inputType = InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_DONE or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG
                }
                runner.restricted = RecordingEditor(editor).apply {
                    id = View.generateViewId()
                    hint = "T09 restricted test editor"
                }
                runner.current = runner.chat
                row.addView(runner.chat, LinearLayout.LayoutParams(-1, 96))
                row.addView(runner.restricted, LinearLayout.LayoutParams(-1, 96))
                editor.setContentView(row)
                runner.chat.requestFocus()
            }
            runBlocking {
                withTimeout(900_000) {
                    // API21 host must keep the existing LatinIME→focused editor→Trime gate.
                    runner.until { runner.chat.hasWindowFocus() && runner.chat.hasFocus() && runner.manager.isActive(runner.chat) }
                    runner.main { runner.manager.showSoftInput(runner.chat, 0) }
                    runner.until { runner.input() != null }
                    runner.service = runner.main { checkNotNull(runner.input()).di.direct.instance() }
                    runner.until { runner.service.currentInputEditorInfo?.fieldId == runner.chat.id }
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        checkNotNull(session).run { messageFlow }.collect { message ->
                            when (message) {
                                is RimeMessage.CommitTextMessage -> runner.events.add(
                                    json(
                                        "event" to "native_commit",
                                        "text" to message.data.text,
                                        "editor" to message.editorToken,
                                        "generation" to message.snapshot.generation,
                                        "t_ns" to SystemClock.elapsedRealtimeNanos(),
                                    ),
                                )

                                is RimeMessage.OptionMessage -> if (message.data.option == "ascii_mode") {
                                    runner.events.add(
                                        json(
                                            "event" to "ascii_option",
                                            "value" to message.data.value,
                                            "editor" to message.editorToken,
                                            "generation" to message.snapshot.generation,
                                            "t_ns" to SystemClock.elapsedRealtimeNanos(),
                                        ),
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }
                    try {
                        for (theme in listOf("trime", "tongwenfeng.trime")) {
                            runner.theme = theme
                            runner.themeHashes.put(theme, instrumentation.targetContext.assets.open("shared/$theme.yaml").use { digest(it.readBytes()) })
                            val previousTheme = ThemeManager.prefs.selectedTheme.getValue()
                            val oldInput = runner.main { runner.input() }
                            check(ThemeManager.selectTheme(theme) == theme) { "Theme fell back" }
                            if (theme != previousTheme) {
                                runner.until { runner.input()?.let { it !== oldInput } == true }
                            } else {
                                runner.until { runner.input() != null }
                            }
                            runner.effectiveThemes.put(theme, runner.effectiveThemeIdentity())
                            tasks.forEach { runner.runTask(it) }
                            runner.verifyComposition(false)
                            runner.verifyComposition(true)
                            runner.verifyEditorRestore(false)
                            runner.verifyEditorRestore(true)
                            if (!baseline) {
                                runner.verifyRapid(false)
                                runner.verifyRapid(true)
                                runner.verifyMacro(false)
                                runner.verifyMacro(true)
                            }
                            check(runner.effectiveThemeIdentity().toString() == runner.effectiveThemes.getJSONObject(theme).toString()) { "Theme source dependencies changed during collection" }
                        }
                    } finally {
                        collector.cancel()
                        collector.join()
                    }
                }
            }
            val statuses = (0 until runner.results.length()).map { runner.results.getJSONObject(it).getString("status") }
            val regressions = (0 until runner.regressions.length()).map { runner.regressions.getJSONObject(it).getString("status") }
            val rapid = (0 until runner.rapidRegressions.length()).map { runner.rapidRegressions.getJSONObject(it).getString("status") }
            val macros = (0 until runner.macroRegressions.length()).map { runner.macroRegressions.getJSONObject(it).getString("status") }
            runner.collectionComplete = statuses.size == 14 && regressions.size == 8 && rapid.size == (if (baseline) 0 else 4) &&
                macros.size == (if (baseline) 0 else 4) && (statuses + regressions + rapid + macros).none { it == "incomplete" }
            runner.save()
            result.putBoolean("collection_complete", runner.collectionComplete)
            // Known old-product failures remain failures. A complete baseline collection is a
            // separate fact, allowing comparison without pretending the old product passed.
            check(statuses.size == 14 && statuses.none { it in setOf("failed", "incomplete") }) { "T09 recorded failures; preserve report before retry" }
            check(regressions.size == 8 && regressions.none { it in setOf("failed", "incomplete") }) { "T09 composition/mode regression failed" }
            check(rapid.size == (if (baseline) 0 else 4) && rapid.all { it == "passed" }) { "T09 rapid FIFO regressions failed" }
            check(macros.size == (if (baseline) 0 else 4) && macros.all { it == "passed" }) { "T09 actual macro FIFO regressions failed" }
            passed = true
            val supported = statuses.count { it == "passed" }
            val unsupported = statuses.count { it == "unsupported" }
            result.putString("stream", "PASS: T09 ${if (baseline) "baseline collection completed: $supported supported, $unsupported unsupported" else "fixed-task acceptance"}; report cache/t09-mixed-input/report.json\n")
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            val cleanupErrors = mutableListOf<String>()
            if (dedicatedValidated) {
                // One failed restore must not skip the independent preference/activity/client cleanup.
                try {
                    runner.manualPreference?.restore()
                    runner.manualPreferenceCleanup.put("restored_store", runner.manualPreference?.state() ?: JSONObject.NULL)
                    if (runner.manualPreference?.implemented == true && runner.hasService()) {
                        runBlocking {
                            val priorToken = runner.main { runner.service.editorToken }
                            runner.current = runner.chat
                            runner.main {
                                runner.chat.requestFocus()
                                runner.manager.restartInput(runner.chat)
                                runner.manager.showSoftInput(runner.chat, 0)
                            }
                            runner.until { runner.input() != null && runner.service.currentInputEditorInfo?.fieldId == runner.chat.id && runner.service.editorToken != priorToken }
                            val reread = runner.settled()
                            val original = checkNotNull(runner.manualPreference).original()
                            if (original.optString("raw") in setOf("native", "ascii")) {
                                check(reread.getBoolean("ascii_mode") == (original.getString("raw") == "ascii")) { "Restored manual preference was not re-read by the new ordinary editor" }
                            }
                            runner.manualPreferenceCleanup.put("new_ordinary_editor", reread).put("prior_editor_token", priorToken)
                        }
                    }
                    runner.manualPreferenceCleanup.put(
                        "status",
                        when {
                            runner.manualPreference?.implemented != true -> "NOT_APPLICABLE_T09_PREF_NOT_IMPLEMENTED"
                            runner.manualPreferenceCleanup.has("new_ordinary_editor") -> "restored_and_rebound"
                            else -> "restored_store_only_editor_unavailable"
                        },
                    )
                } catch (failure: Throwable) {
                    cleanupErrors.add(failure.stackTraceToString())
                }
                val finalEditorToken = if (runner.hasService()) runner.main { runner.service.editorToken } else null
                try {
                    activity?.let { runner.main { it.finish() } }
                } catch (failure: Throwable) {
                    cleanupErrors.add(failure.stackTraceToString())
                }
                runBlocking {
                    withContext(NonCancellable) {
                        if (finalEditorToken != null) {
                            try {
                                runner.until { !runner.service.isCurrentEditor(finalEditorToken) }
                                runner.api { } // Drain the serial onFinishInput cleanup before restoring.
                            } catch (failure: Throwable) {
                                cleanupErrors.add(failure.stackTraceToString())
                            }
                        }
                        try {
                            withTimeout(30_000) { ThemeManager.selectTheme(originalTheme) }
                        } catch (failure: Throwable) {
                            cleanupErrors.add(failure.stackTraceToString())
                        }
                        try {
                            withTimeout(30_000) {
                                originalSchema?.let { schema ->
                                    session?.runOnReady {
                                        clearComposition()
                                        check(selectSchema(schema))
                                        setRuntimeOption("ascii_mode", originalAscii)
                                        check(selectedSchemaId() == schema && getRuntimeOption("ascii_mode") == originalAscii) { "Original native schema/mode did not restore" }
                                    }
                                }
                            }
                        } catch (failure: Throwable) {
                            cleanupErrors.add(failure.stackTraceToString())
                        }
                    }
                }
                for (cleanup in listOf<() -> Unit>(
                    { preference.setValue(originalInline) },
                    { runner.manualPreference?.verifyRestored() },
                    { session?.let { runner.main { RimeDaemon.destroySession(javaClass.name) } } },
                )) {
                    try {
                        cleanup()
                    } catch (failure: Throwable) {
                        cleanupErrors.add(failure.stackTraceToString())
                    }
                }
                if (cleanupErrors.isNotEmpty()) {
                    passed = false
                    result.putString("cleanup_failure", cleanupErrors.joinToString("\n"))
                }
                try {
                    File(runner.output, "completion.json").writeText(
                        json(
                            "passed" to passed, "baseline" to baseline,
                            "run_id" to runner.runId, "collection_complete" to runner.collectionComplete,
                            "cleanup_errors" to JSONArray(cleanupErrors), "manual_language_preference_cleanup" to runner.manualPreferenceCleanup, "device_run" to true,
                            "physical_touch_benchmark" to false, "performance_acceptance" to false,
                        ).toString(2) + "\n",
                    )
                    // Manifest binds this nonce to only this test-owned directory. It excludes
                    // itself, avoiding a circular digest; partial failure artifacts remain usable.
                    val attachments = JSONObject()
                    checkNotNull(runner.output.listFiles()).sortedBy { it.name }.forEach { file ->
                        check(file.isFile) { "Unexpected nested test artifact" }
                        attachments.put(file.name, json("sha256" to digest(file.readBytes()), "bytes" to file.length()))
                    }
                    File(runner.output, "artifact-manifest.json").writeText(
                        json(
                            "format" to 1,
                            "run_id" to runner.runId,
                            "attachments" to attachments,
                        ).toString(2) + "\n",
                    )
                } catch (failure: Throwable) {
                    passed = false
                    result.putString("report_failure", failure.stackTraceToString())
                }
            }
            result.putBoolean("passed", passed)
            instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }
}
