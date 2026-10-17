/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.EditorPolicy
import com.osfans.trime.quality.MigratedPreferenceDialogFixture
import com.osfans.trime.quality.OriginalPreferenceDialogFixture
import com.osfans.trime.quality.PreferenceProbeFixtures
import com.osfans.trime.quality.PreferenceProbeMemory
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.setup.SetupActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Small ordinary helpers; no coroutine state machine or replicated giant finally body. */
internal class PublicPreferenceProbeContext(val instrumentation: Instrumentation) {
    val context: Context = instrumentation.targetContext
    val token: String = UUID.randomUUID().toString()
    val report = JSONObject().put("run_id", token).put("scope", "Synthetic in-memory preference windows on real Main; same-APK recreation only")
    private val started = SystemClock.uptimeMillis()
    private val workDeadline = started + 240_000
    private val totalDeadline = started + 300_000
    private var deadline = workDeadline
    private val directory = File(context.filesDir, "runtime-audit/public-preference/$token")
    private val checks = JSONArray()
    private val markers = JSONArray()
    private val images = JSONArray()
    private val windows = JSONArray()
    private val cleanupErrors = JSONArray()
    private val created = mutableListOf<Activity>()
    private val resumed = mutableSetOf<Activity>()
    private var registered = false
    private var baselineSessions: Set<String>? = null
    private var baselineIme: Pair<Boolean, String?>? = null
    private var baselineMode: Pair<Boolean, Any?>? = null
    private var baselineEditorPreferences: Map<String, Pair<Boolean, Any?>>? = null
    private var baselineGrants: Set<String>? = null
    private var baselineFragments: List<String>? = null
    private var sessionsRestored = false
    private var imeRestored = false
    private var modeRestored = false
    private var editorPreferencesUnchanged = false
    private var ownedUiReferencesCleared = false
    private var grantsRestored = false
    private var sessionsAfter: List<String> = emptyList()
    var activity: MainActivity? = null
        private set
    private var fixtureToken: String? = null
    private var fixtureTag: String? = null
    val memory: PreferenceProbeMemory get() = PreferenceProbeFixtures.require(checkNotNull(fixtureToken))
    var failure: Throwable? = null
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            created += activity
        }
        override fun onActivityResumed(activity: Activity) {
            resumed += activity
        }
        override fun onActivityPaused(activity: Activity) {
            resumed -= activity
        }
        override fun onActivityDestroyed(activity: Activity) {
            resumed -= activity
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    }

    fun <T> main(block: () -> T): T {
        check(SystemClock.uptimeMillis() <= deadline) { "Native stage budget expired before main operation" }
        var outcome: Result<T>? = null
        instrumentation.runOnMainSync { outcome = runCatching(block) }
        val completed = checkNotNull(outcome)
        if (SystemClock.uptimeMillis() > deadline) {
            val late = IllegalStateException("Native stage budget expired after main operation")
            val primary = completed.exceptionOrNull()
            if (primary == null) throw late else primary.addSuppressed(late)
        }
        return completed.getOrThrow()
    }
    fun await(label: String, condition: () -> Boolean) {
        val until = minOf(deadline, SystemClock.uptimeMillis() + 30_000)
        while (!condition()) {
            check(SystemClock.uptimeMillis() < until) { label }
            SystemClock.sleep(50)
        }
        check(SystemClock.uptimeMillis() <= until) { "$label: late observation" }
    }
    private fun sessionNames(): Set<String> {
        // Read own app state, never Android/AndroidX private fields.
        val field = RimeDaemon.javaClass.getDeclaredField("sessions").apply { isAccessible = true }
        return (field.get(RimeDaemon) as Map<*, *>).keys.map { it as String }.toSet()
    }
    private fun ime(): Pair<Boolean, String?> {
        val key = Settings.Secure.DEFAULT_INPUT_METHOD
        return checkNotNull(context.contentResolver.query(Settings.Secure.getUriFor(key), arrayOf("name", "value"), null, null, null)).use {
            check(it.count in 0..1)
            val result = if (it.moveToFirst()) {
                check(it.getString(it.getColumnIndexOrThrow("name")) == key)
                true to it.getString(it.getColumnIndexOrThrow("value"))
            } else {
                false to null
            }
            check(result.second == Settings.Secure.getString(context.contentResolver, key))
            result
        }
    }
    private fun grants(): Set<String> = context.contentResolver.persistedUriPermissions.map {
        "${it.uri}|${it.isReadPermission}|${it.isWritePermission}"
    }.toSet()
    private fun editorPreferences(): Map<String, Pair<Boolean, Any?>> {
        val prefs = AppPrefs.defaultInstance()
        val store = prefs.advanced.uiMode.sharedPreferences
        val values = store.all
        // Only the four explicit keys are an equality gate; never rewrite or normalize them.
        return listOf(
            prefs.advanced.uiMode.key,
            prefs.keyboard.manualLanguageMode.key,
            prefs.general.manualPrivacyMode.key,
            prefs.general.asciiSwitchTips.key,
        ).associateWith { key ->
            store.contains(key) to values[key]
        }
    }
    private fun editorPreferencesJson(values: Map<String, Pair<Boolean, Any?>>): JSONObject = JSONObject().apply {
        values.forEach { (key, state) ->
            put(
                key,
                JSONObject().put("exists", state.first)
                    .put("type", state.second?.javaClass?.name ?: JSONObject.NULL).put("value", state.second ?: JSONObject.NULL),
            )
        }
    }
    private fun fragmentShape(): List<String> = checkNotNull(activity).supportFragmentManager.fragments
        .filter { it.tag != fixtureTag && it.tag != DIALOG_TAG }
        .map { "${it.javaClass.name}|${it.id}|${it.tag}" }.sorted()

    fun prepare(disposable: Boolean, expectedCode: String) {
        check(disposable && BuildConfig.DEBUG && Build.HARDWARE in setOf("ranchu", "goldfish") && Build.VERSION.SDK_INT in setOf(21, 35))
        check(expectedCode.matches(Regex("[0-9a-f]{40}")) && BuildConfig.BUILD_COMMIT_HASH == expectedCode)
        check(context.packageName == BuildConfig.APPLICATION_ID)
        check(File(checkNotNull(context.getExternalFilesDir(null)), "runtime-audit-dedicated").let { it.isFile && it.length() == 0L })
        check(runBlocking { withTimeout(30_000) { !SetupActivity.shouldShowUp() } }) { "A completed disposable route profile is required" }
        if (Build.VERSION.SDK_INT >= 33) check(context.packageManager.checkPermission("android.permission.POST_NOTIFICATIONS", context.packageName) == PackageManager.PERMISSION_GRANTED)
        baselineIme = ime()
        check(baselineIme?.second?.startsWith("${context.packageName}/") == true)
        baselineMode = AppPrefs.defaultInstance().advanced.uiMode.let { it.sharedPreferences.contains(it.key) to it.sharedPreferences.all[it.key] }
        val rawMode = baselineMode?.second
        check(rawMode == null || (rawMode is String && AppPrefs.Advanced.UiMode.entries.any { it.name == rawMode.uppercase() }))
        baselineEditorPreferences = editorPreferences()
        baselineGrants = grants()
        await("The selected IME did not bind before preference windows") {
            main {
                val observed = sessionNames()
                val state = RimeDaemon.engineState.value
                report.put("sessions_preflight_observed", JSONArray(observed.sorted()))
                    .put("engine_preflight_observed", state.name)
                observed.size == 1 &&
                    observed.single().startsWith("com.osfans.trime.ime.core.TrimeInputMethodService#") &&
                    state == RimeLifecycle.State.READY
            }
        }
        baselineSessions = main { sessionNames() }
        check(checkNotNull(baselineSessions).none { it == MainViewModel::class.java.name }) { "Existing Main owner must not be reused" }
        check(
            checkNotNull(baselineSessions).size == 1 && checkNotNull(baselineSessions).all {
                it.startsWith("com.osfans.trime.ime.core.TrimeInputMethodService#")
            },
        ) { "Exactly one already-bound selected IME owner and no unknown owners required" }
        await("The already-bound IME did not become READY before preference windows") { RimeDaemon.engineState.value == RimeLifecycle.State.READY }
        check(!directory.exists() && directory.mkdirs())
        main {
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(callbacks)
            registered = true
        }
        report.put("api", Build.VERSION.SDK_INT).put("pid", android.os.Process.myPid()).put("code_sha", expectedCode)
        report.put("default_ime_before", JSONObject().put("exists", baselineIme!!.first).put("value", baselineIme!!.second))
            .put("sessions_before", JSONArray(checkNotNull(baselineSessions).sorted()))
            .put("ui_mode_raw_before_exists", baselineMode!!.first).put("ui_mode_raw_before", baselineMode!!.second)
            .put("editor_preferences_before", editorPreferencesJson(checkNotNull(baselineEditorPreferences)))
            .put("engine_ready_before", RimeDaemon.engineState.value == RimeLifecycle.State.READY)
            .put("process_start_ticks", File("/proc/self/stat").readText().substringAfterLast(") ").trim().split(Regex("\\s+"))[19])
            .put("boot_identity_host_binding_required", true) // Host reads/authenticates boot; do not probe new app /proc permissions.
        launchMain()
    }
    private fun ready(candidate: MainActivity): Boolean = !candidate.isFinishing && !candidate.isDestroyed &&
        candidate in resumed && candidate.hasWindowFocus() && candidate.findViewById<android.view.View>(R.id.toolbar)?.isShown == true
    private fun launchMain() {
        main { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        await("A new owned public Main did not become ready") {
            main {
                activity = created.filterIsInstance<MainActivity>().lastOrNull { !it.isDestroyed }
                activity?.let(::ready) == true
            }
        }
        main {
            check(created.none { it is AppCompatActivity })
            baselineFragments = fragmentShape()
        }
    }
    fun newOwner(legacy: Boolean) {
        removeOwner()
        main {
            val id = UUID.randomUUID().toString()
            fixtureToken = id
            fixtureTag = "preference-fixture-$id"
            PreferenceProbeFixtures.create(id)
            val owner = if (legacy) OriginalPreferenceDialogFixture() else MigratedPreferenceDialogFixture()
            owner.arguments = Bundle().apply { putString("fixture_token", id) } // normal arguments, never saved-state fabrication
            checkNotNull(activity).supportFragmentManager.beginTransaction().add(owner, fixtureTag).commitNow()
            check(checkNotNull(owner.preferenceScreen).preferenceCount == 4)
        }
    }
    fun owner(): PreferenceFragmentCompat = checkNotNull(checkNotNull(activity).supportFragmentManager.findFragmentByTag(fixtureTag)) as PreferenceFragmentCompat
    fun dialogFragment(): DialogFragment? = checkNotNull(activity).supportFragmentManager.findFragmentByTag(DIALOG_TAG) as? DialogFragment
    fun dialog(): Dialog = checkNotNull(dialogFragment()?.dialog)
    private fun visibleDialog(expectedClass: String): Boolean {
        val fragment = dialogFragment() ?: return false
        val dialog = fragment.dialog ?: return false
        val root = dialog.window?.decorView ?: return false
        return fragment.javaClass.name == expectedClass && dialog.isShowing && root.isShown && root.hasWindowFocus() &&
            root.width > 0 && root.height > 0 && !root.isLayoutRequested
    }
    private fun recordWindow(label: String) {
        val editor = dialog().findViewById<android.widget.EditText>(android.R.id.edit)
        val privacyPreference = AppPrefs.defaultInstance().general.manualPrivacyMode
        val rawPrivacy = privacyPreference.sharedPreferences.all[privacyPreference.key]
        check(rawPrivacy == null || rawPrivacy is Boolean) { "Malformed manual privacy policy; never normalize the saved preference" }
        val manualPrivacy = rawPrivacy ?: privacyPreference.defaultValue
        windows.put(
            JSONObject().put("label", label).put("fragment_class", dialogFragment()!!.javaClass.name)
                .put("dialog_class", dialog().javaClass.name).put("owner_class", owner().javaClass.name)
                .put("main_instance", System.identityHashCode(activity)).put("same_process_pid", android.os.Process.myPid())
                .put("actual_editor_class", editor?.javaClass?.name ?: JSONObject.NULL)
                .put("editor_input_type", editor?.inputType ?: JSONObject.NULL).put("editor_ime_options", editor?.imeOptions ?: JSONObject.NULL)
                .put("computed_editor_keyboard_policy", editor?.let { EditorPolicy.keyboard(it.inputType, it.imeOptions).name } ?: JSONObject.NULL)
                .put("computed_editor_privacy_active", editor?.let { EditorPolicy.privacy(it.inputType, it.imeOptions, manualPrivacy).active } ?: JSONObject.NULL),
        )
    }
    fun show(kind: String, expectedClass: String) {
        marker("${kind}_show_begin")
        main {
            check(dialogFragment() == null)
            owner().onDisplayPreferenceDialog(checkNotNull(owner().findPreference<Preference>(memory.key(kind))))
        }
        await("Expected preference dialog did not show and lay out") { main { visibleDialog(expectedClass) } }
        main { recordWindow("$kind-initial") }
    }
    fun recreate(expectedClass: String) {
        marker("restore_transition_begin") // Contains old teardown AND new creation; never called a pure new-window interval.
        val old = checkNotNull(activity)
        val before = main { created.size }
        main { old.recreate() }
        await("Actual Main recreation did not restore expected dialog") {
            main {
                val replacement = created.drop(before).filterIsInstance<MainActivity>().lastOrNull { !it.isDestroyed }
                if (replacement != null) activity = replacement
                old.isDestroyed && replacement != null && !replacement.isFinishing && replacement in resumed &&
                    visibleDialog(expectedClass)
            }
        }
        main {
            check(fragmentShape() == baselineFragments)
            check(owner().isAdded)
            recordWindow("restored")
        }
        marker("restore_transition_end")
    }
    fun awaitDismissed() = await("Owned dialog was not normally removed") { main { dialogFragment() == null } }
    fun removeOwner() {
        if (fixtureToken == null) return
        main { dialogFragment()?.dismiss() }
        awaitDismissed()
        main {
            val manager = checkNotNull(activity).supportFragmentManager
            manager.findFragmentByTag(fixtureTag)?.let { manager.beginTransaction().remove(it).commitNow() }
            check(manager.findFragmentByTag(fixtureTag) == null)
            PreferenceProbeFixtures.release(checkNotNull(fixtureToken))
            fixtureToken = null
            fixtureTag = null
            check(fragmentShape() == baselineFragments)
        }
    }
    fun marker(label: String) {
        val text = "PUBLIC_PREFERENCE_${token}_${markers.length()}_$label"
        Log.i("TrimePublicPreference", text)
        markers.put(JSONObject().put("label", label).put("text", text).put("uptime_ms", SystemClock.uptimeMillis()))
        instrumentation.sendStatus(0, Bundle().apply { putString("public_preference_marker", text) })
    }
    fun capture(label: String) {
        val root = main { dialog().window!!.decorView }
        val pair = RenderedScreenshotBarrier.capture(instrumentation, root) { check(dialog().isShowing && dialog().window?.decorView === root) }
        try {
            val file = File(directory, "$label.png")
            file.outputStream().use { check(pair.first.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            images.put(binding(file).put("draw_barrier", pair.second).put("human_reviewed", false))
        } finally {
            pair.first.recycle()
        }
    }
    fun passed(label: String) {
        check(SystemClock.uptimeMillis() <= workDeadline) { "Logical group completed after work deadline" }
        checks.put(label)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS public preference ${checks.length()}: $label\n") })
    }
    private fun binding(file: File): JSONObject = JSONObject().put("path", file.absolutePath).put("bytes", file.length()).put("sha256", MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })

    private fun clean(block: () -> Unit) {
        try {
            check(SystemClock.uptimeMillis() <= deadline)
            block()
            check(SystemClock.uptimeMillis() <= deadline)
        } catch (error: Throwable) {
            cleanupErrors.put(error.stackTraceToString())
            val primary = failure
            if (primary == null) {
                failure = error
            } else if (primary !== error) {
                primary.addSuppressed(error)
            }
        }
    }
    fun cleanupAndReport(): Bundle {
        val workElapsed = SystemClock.uptimeMillis() - started
        deadline = minOf(totalDeadline, SystemClock.uptimeMillis() + 60_000)
        if (workElapsed > 240_000 && failure == null) failure = IllegalStateException("Work budget exceeded")
        clean { if (activity != null && !checkNotNull(activity).isDestroyed) removeOwner() }
        clean { main { created.asReversed().filter { !it.isDestroyed && !it.isFinishing }.forEach(Activity::finish) } }
        clean { await("Owned activities did not reach normal onDestroy") { main { created.all { it.isDestroyed } } } }
        clean {
            fixtureToken?.let { id -> main { if (PreferenceProbeFixtures.contains(id)) PreferenceProbeFixtures.release(id) } }
            fixtureToken = null
            fixtureTag = null
        }
        baselineSessions?.let { initial ->
            clean {
                await("Rime owner set and READY state did not restore") { main { sessionNames() == initial } && RimeDaemon.engineState.value == RimeLifecycle.State.READY }
                sessionsAfter = main { sessionNames().sorted() }
                sessionsRestored = true
            }
        }
        baselineIme?.let { initial ->
            clean {
                check(ime() == initial)
                imeRestored = true
            }
        }
        baselineMode?.let { initial ->
            clean {
                val p = AppPrefs.defaultInstance().advanced.uiMode
                check((p.sharedPreferences.contains(p.key) to p.sharedPreferences.all[p.key]) == initial)
                modeRestored = true
            }
        }
        baselineEditorPreferences?.let { initial ->
            clean {
                val after = editorPreferences()
                report.put("editor_preferences_after", editorPreferencesJson(after))
                check(after == initial) { "A protected editor policy/tips preference changed; probe never restores or normalizes it" }
                editorPreferencesUnchanged = true
            }
        }
        baselineGrants?.let { initial ->
            clean {
                check(grants() == initial)
                grantsRestored = true
            }
        }
        if (registered) {
            clean {
                main { (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(callbacks) }
                registered = false
            }
        }
        clean {
            main {
                // Assert true destruction BEFORE releasing strong references, never hide a live window.
                check(!registered && created.all { it.isDestroyed } && (activity == null || checkNotNull(activity).isDestroyed))
                report.put(
                    "owned_activities_at_release",
                    JSONArray(
                        created.map { owned ->
                            JSONObject()
                                .put("class", owned.javaClass.name).put("identity", System.identityHashCode(owned)).put("destroyed", owned.isDestroyed)
                        },
                    ),
                )
                created.clear()
                resumed.clear()
                activity = null
                ownedUiReferencesCleared = true
            }
        }
        report.put("checks", checks).put("screenshots", images).put("markers", markers).put("windows", windows).put("cleanup_errors", cleanupErrors)
            .put("fixture_state_released", fixtureToken == null).put("callbacks_unregistered", !registered)
            .put("default_ime_unchanged", imeRestored).put("ui_mode_raw_unchanged", modeRestored)
            .put("editor_preferences_unchanged", editorPreferencesUnchanged).put("owned_ui_references_cleared", ownedUiReferencesCleared)
            .put("uri_grants_unchanged", grantsRestored).put("sessions_restored", sessionsRestored)
            .put("sessions_after", JSONArray(sessionsAfter)).put("engine_ready_after", sessionsRestored && RimeDaemon.engineState.value == RimeLifecycle.State.READY)
            .put("work_elapsed_ms", workElapsed).put("work_budget_ms", 240_000).put("cleanup_budget_ms", 60_000).put("total_budget_ms", 300_000)
            .put("synthetic_memory_only", true).put("same_apk_recreate_only", true).put("old_apk_upgrade_verified", false)
            .put("physical_touch_tested", false).put("talkback_tested", false).put("probe_requests_dictionary_writes", false)
            .put("human_png_reviewed", false).put("warning_intervals_host_reviewed", false).put("whole_app_warning_closed", false)
            .put("editor_policy_scope", "Actual dialog inputType/imeOptions classified by stateless EditorPolicy; not proof of service currentEditorInfo or restoration of the previous editor generation/connection")
            .put("persistent_preference_scope", "Exact raw presence/type/value equality for ui_mode, manual_language_mode, manual_privacy_mode and ascii_switch_tips only; synthetic preferences use in-memory DataStore; no whole-store/dictionary-byte unchanged claim")
        if (checks.length() != 8 && failure == null) failure = IllegalStateException("Eight complete logical groups required")
        return terminalBundle()
    }
    private fun parcelSize(result: Bundle): Int {
        val parcel = Parcel.obtain()
        return try {
            result.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }
    private fun bundleFor(payload: JSONObject, file: File): Bundle = Bundle().apply {
        val json = payload.toString()
        file.writeText(json + "\n")
        putBoolean("passed", payload.getBoolean("passed"))
        putBoolean("automatic_reuse_allowed", payload.getBoolean("automatic_reuse_allowed"))
        putBoolean("private_report_valid", true)
        putInt("public_preference_checks", checks.length())
        putString("public_preference_report_json", json)
        putString("public_preference_report_path", file.absolutePath)
        putLong("public_preference_report_bytes", file.length())
        putString("public_preference_report_sha256", binding(file).getString("sha256"))
        putInt("terminal_bundle_parcel_bytes", 0)
    }
    private fun terminalBundle(): Bundle {
        if (SystemClock.uptimeMillis() > deadline && failure == null) failure = IllegalStateException("Total/cleanup budget exceeded")
        report.put("cleanup_complete", cleanupErrors.length() == 0).put("passed", failure == null).put("automatic_reuse_allowed", failure == null)
        failure?.let { report.put("error", it.stackTraceToString()) }
        if (!directory.isDirectory) {
            return Bundle().apply {
                putBoolean("passed", false)
                putBoolean("automatic_reuse_allowed", false)
                putString("error", "Preflight refused before report directory creation")
            }
        }
        val file = File(directory, "report.json")
        var result = bundleFor(report, file)
        if (SystemClock.uptimeMillis() > deadline && failure == null) {
            failure = IllegalStateException("Native report completed after total deadline")
            report.put("passed", false).put("automatic_reuse_allowed", false).put("error", failure!!.stackTraceToString())
            result = bundleFor(report, file)
        }
        var bytes = parcelSize(result)
        if (bytes > 65_536) {
            val original = File(directory, "report-over-budget.json")
            original.writeText(report.toString() + "\n")
            val compact = JSONObject().put("passed", false).put("automatic_reuse_allowed", false)
                .put("error", "Actual terminal Bundle exceeds64KiB; all original report bytes retained")
                .put("original_report", binding(original))
            result = bundleFor(compact, file)
            bytes = parcelSize(result)
        }
        result.putInt("terminal_bundle_parcel_bytes", bytes)
        check(parcelSize(result) == bytes && bytes <= 65_536)
        if (SystemClock.uptimeMillis() > deadline && result.getBoolean("passed")) {
            val late = JSONObject(report.toString()).put("passed", false).put("automatic_reuse_allowed", false)
                .put("error", "Terminal serialization completed after native deadline")
            result = bundleFor(late, file)
            bytes = parcelSize(result)
            check(bytes <= 65_536)
            result.putInt("terminal_bundle_parcel_bytes", bytes)
            check(parcelSize(result) == bytes)
        }
        return result
    }
    companion object {
        const val DIALOG_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}
