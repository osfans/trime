/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeDiagnostics
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ui.common.PublicWindowPilotConfiguration
import com.osfans.trime.ui.common.PublicWindowPilotConfigurationAssertions
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.settings.theme.ThemeDiagnosticsActivity
import com.osfans.trime.ui.setup.SetupActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Narrow disposable-emulator pilot. It never reports whole-app warning or visual acceptance. */
internal object PublicWindowPilotProbe {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    /** Own application state only; no reflection into Android/AppCompat private Window APIs. */
    private fun ownField(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).run {
        isAccessible = true
        get(target)
    }

    private fun sessions(): Set<String> {
        val raw = checkNotNull(ownField(RimeDaemon, "sessions")) as Map<*, *>
        return raw.keys.map { it as String }.toSet()
    }

    private fun binding(file: File): JSONObject = JSONObject().put("path", file.absolutePath)
        .put("bytes", file.length()).put(
            "sha256",
            MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes()).joinToString("") { "%02x".format(it) },
        )

    private fun configuration(value: Configuration): JSONObject = JSONObject()
        .put("font_scale", value.fontScale.toDouble()).put("locales", ConfigurationCompat.getLocales(value).toLanguageTags())
        .put("density_dpi", value.densityDpi).put("width_dp", value.screenWidthDp).put("height_dp", value.screenHeightDp)
        .put("smallest_width_dp", value.smallestScreenWidthDp).put("orientation", value.orientation)
        .put("ui_mode", value.uiMode).put("screen_layout", value.screenLayout)

    private fun ime(context: Context): Pair<Boolean, String?> {
        val key = Settings.Secure.DEFAULT_INPUT_METHOD
        val cursor = checkNotNull(context.contentResolver.query(Settings.Secure.getUriFor(key), arrayOf("name", "value"), null, null, null))
        return cursor.use {
            check(it.count in 0..1)
            val value = if (it.moveToFirst()) {
                check(it.getString(it.getColumnIndexOrThrow("name")) == key)
                true to it.getString(it.getColumnIndexOrThrow("value"))
            } else {
                false to null
            }
            check(value.second == Settings.Secure.getString(context.contentResolver, key))
            value
        }
    }

    private fun restore(store: SharedPreferences, values: Map<String, Any?>) {
        val editor = store.edit()
        values.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                else -> error("Unexpected raw pilot preference type")
            }
        }
        check(editor.commit()) { "Pilot preference restore failed" }
        check(values.all { (key, value) -> store.all[key] == value && store.contains(key) == (value != null) })
    }

    fun run(instrumentation: Instrumentation, disposable: Boolean, copyShare: Boolean) {
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        val result = Bundle()
        val report = JSONObject().put("scope", "ThemeDiagnostics public-window pilot only; no global warning/visual/TalkBack acceptance")
            .put("api", Build.VERSION.SDK_INT).put("pid", android.os.Process.myPid())
            .put("human_png_reviewed", false).put("true_tts_tested", false)
            .put("future_system_night_change_tested", false).put("cold_up_recreate_task_branch_tested", false)
        val checks = JSONArray()
        val screenshots = JSONArray()
        val windows = JSONArray()
        val cleanupErrors = JSONArray()
        val created = mutableListOf<Activity>()
        val resumed = mutableSetOf<Activity>()
        val token = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "runtime-audit/public-window-pilot/$token")
        val started = SystemClock.uptimeMillis()
        val workDeadline = started + 240_000
        val deadline = started + 300_000 // separate bounded cleanup reserve; no pass after this total limit
        var cleaning = false
        var cleanupDeadline = deadline
        var workElapsed = 0L
        var failure: Throwable? = null
        var callbacksRegistered = false
        var directOpen = false
        var copied = false
        var clipboardWriteAttempted = false
        var chooserObserved = false
        var chooserPackage: String? = null
        var clipboardCaptured = false
        var clipboardRestored = false
        var copyShareChecks = 0
        var fixtureActivity: ThemeDiagnosticsActivity? = null
        var fixtureOriginalReport: String? = null
        val directViolations = JSONArray()
        var clipboardBackup: ClipData? = null
        var preferencesBackup: Map<String, Any?>? = null
        var store: SharedPreferences? = null
        var beforeIme: Pair<Boolean, String?>? = null
        var originalDelegateMode: Int? = null
        var delegateModeRestored = false
        var processRetirementRequired = false
        var originalMode: AppPrefs.Advanced.UiMode? = null
        var beforeSessions: Set<String>? = null

        fun <T> main(block: () -> T): T {
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            return checkNotNull(outcome).getOrThrow()
        }
        fun await(message: String, timeout: Long = 30_000, condition: () -> Boolean) {
            val until = minOf(if (cleaning) cleanupDeadline else workDeadline, SystemClock.uptimeMillis() + timeout)
            while (!condition()) {
                check(SystemClock.uptimeMillis() < until) { message }
                SystemClock.sleep(50)
            }
            check(SystemClock.uptimeMillis() <= until) { "$message (observed after deadline)" }
        }
        fun passed(label: String) {
            checks.put(label)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS public window pilot ${checks.length()}: $label\n") })
        }
        fun marker(label: String) {
            val text = "PUBLIC_WINDOW_PILOT_${token}_$label"
            Log.i("TrimePublicWindowPilot", text)
            instrumentation.sendStatus(0, Bundle().apply { putString("public_window_marker", text) })
            report.put(label, text)
        }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                created += activity
                if (directOpen && activity is AppCompatActivity) directViolations.put(activity.javaClass.name)
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
        fun ready(activity: ThemeDiagnosticsActivity): Boolean = main {
            !activity.isFinishing && !activity.isDestroyed && activity in resumed && activity.hasWindowFocus() &&
                activity.findViewById<Button>(R.id.copyButton)?.isEnabled == true &&
                activity.findViewById<Button>(R.id.shareButton)?.isEnabled == true
        }
        fun launch(parent: Activity? = null): ThemeDiagnosticsActivity {
            val before = main { created.size }
            main {
                val intent = Intent(context, ThemeDiagnosticsActivity::class.java)
                if (parent == null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else parent.startActivity(intent)
            }
            var activity: ThemeDiagnosticsActivity? = null
            await("The real diagnostics Activity did not become ready") {
                activity = main { created.drop(before).filterIsInstance<ThemeDiagnosticsActivity>().lastOrNull { !it.isDestroyed && !it.isFinishing } }
                activity?.let(::ready) == true
            }
            return checkNotNull(activity)
        }
        fun lease(activity: ThemeDiagnosticsActivity): String = main {
            check(ownField(activity, "daemonSessionCreated") == true)
            (ownField(activity, "daemonSessionName") as String).also { check(it in sessions()) }
        }
        fun capture(activity: ThemeDiagnosticsActivity, label: String) {
            val root = main { (activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) }
            val pair = RenderedScreenshotBarrier.capture(instrumentation, root) {
                check(!activity.isDestroyed && !activity.isFinishing && activity.hasWindowFocus())
            }
            try {
                val file = File(directory, "$label.png")
                file.outputStream().use { check(pair.first.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                screenshots.put(binding(file).put("draw_barrier", pair.second).put("human_reviewed", false))
            } finally {
                pair.first.recycle()
            }
        }
        fun window(activity: ThemeDiagnosticsActivity, label: String): JSONObject = main {
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val observedBars = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.systemBars())
            val margins = root.layoutParams as ViewGroup.MarginLayoutParams
            val toolbarMargins = toolbar.layoutParams as ViewGroup.MarginLayoutParams
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            val lightNavigationBars = if (Build.VERSION.SDK_INT >= 35) controller.isAppearanceLightNavigationBars else null
            val observation = JSONObject().put("label", label).put("configuration", configuration(activity.resources.configuration))
                .put("activity_ui_mode", activity.resources.configuration.uiMode)
                .put("decor_ui_mode", activity.window.decorView.resources.configuration.uiMode)
                .put("navigation_bar_light_appearance", lightNavigationBars ?: JSONObject.NULL)
                .put("root_left_margin", margins.leftMargin).put("root_right_margin", margins.rightMargin)
                .put("root_bottom_margin", margins.bottomMargin).put("toolbar_top_margin", toolbarMargins.topMargin)
                .put("system_bars", observedBars?.let { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) } ?: JSONObject.NULL)
                .put("root_width", root.width).put("root_height", root.height)
                .put("toolbar_width", toolbar.width).put("toolbar_height", toolbar.height)
                .put("root_layout_requested", root.isLayoutRequested).put("toolbar_layout_requested", toolbar.isLayoutRequested)
                .put("root_attached", root.isAttachedToWindow).put("toolbar_attached", toolbar.isAttachedToWindow)
                .put("activity_window_focus", activity.hasWindowFocus())
            windows.put(observation)
            val bars = checkNotNull(observedBars)
            for (id in intArrayOf(R.id.copyButton, R.id.shareButton)) {
                val button = activity.findViewById<View>(id)
                val visible = Rect()
                check(button.isShown && button.width > 0 && button.height > 0 && button.getLocalVisibleRect(visible))
                check(visible == Rect(0, 0, button.width, button.height)) { "A diagnostics action is clipped" }
            }
            check(margins.leftMargin == bars.left && margins.rightMargin == bars.right && margins.bottomMargin == bars.bottom) {
                "$label root margins [left,right,bottom]=[${margins.leftMargin},${margins.rightMargin},${margins.bottomMargin}], system bars=[${bars.left},${bars.right},${bars.bottom}]"
            }
            check(toolbarMargins.topMargin == bars.top) { "$label toolbar top margin=${toolbarMargins.topMargin}, system bar top=${bars.top}" }
            check(activity.actionBar == null && activity.window.hasFeature(Window.FEATURE_NO_TITLE))
            check(!controller.isAppearanceLightStatusBars)
            if (Build.VERSION.SDK_INT >= 35) {
                val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                check(lightNavigationBars == (night != Configuration.UI_MODE_NIGHT_YES)) {
                    "$label navigation appearance=$lightNavigationBars, Activity night mode=$night"
                }
            }
            val expectedContext = PublicWindowPilotConfiguration.wrap(context, AppPrefs.defaultInstance().advanced.uiMode.getValue())
            check(ContextCompat.getColor(activity, R.color.colorPrimary) == ContextCompat.getColor(expectedContext, R.color.colorPrimary))
            check(ContextCompat.getColor(activity, R.color.toolbarForegroundColor) == ContextCompat.getColor(expectedContext, R.color.toolbarForegroundColor))
            check(toolbar.title.toString() == activity.getString(R.string.theme_diagnostics))
            check(toolbar.subtitle.toString() == ThemeManager.activeTheme.name)
            check(toolbar.navigationIcon != null && toolbar.navigationContentDescription.toString() == activity.getString(R.string.public_window_navigate_up))
            check(root.width > 0 && root.height > 0 && toolbar.width > 0 && toolbar.height > 0)
            observation
                .put("color_primary", ContextCompat.getColor(activity, R.color.colorPrimary))
                .put("toolbar_foreground", ContextCompat.getColor(activity, R.color.toolbarForegroundColor))
                .put("title_ok", true).put("subtitle_ok", true).put("navigation_name_ok", true)
        }
        fun touch(view: View) {
            val point = main {
                val visible = Rect()
                check(view.isShown && view.isEnabled && view.getLocalVisibleRect(visible))
                check(visible.contains(view.width / 2, view.height / 2))
                IntArray(2).also(view::getLocationOnScreen).let { (x, y) -> (x + view.width / 2f) to (y + view.height / 2f) }
            }
            val down = SystemClock.uptimeMillis()
            for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 80)) {
                val event = MotionEvent.obtain(down, time, action, point.first, point.second, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.sendPointerSync(event)
                } finally {
                    event.recycle()
                }
            }
        }
        fun changeMode(previous: ThemeDiagnosticsActivity, mode: AppPrefs.Advanced.UiMode): ThemeDiagnosticsActivity {
            val oldLease = lease(previous)
            val before = main { created.size }
            main { AppPrefs.defaultInstance().advanced.uiMode.setValue(mode) }
            var replacement: ThemeDiagnosticsActivity? = null
            await("The changed policy did not recreate the real diagnostics window") {
                replacement = main { created.drop(before).filterIsInstance<ThemeDiagnosticsActivity>().lastOrNull { !it.isDestroyed && !it.isFinishing } }
                main { previous.isDestroyed && oldLease !in sessions() } && replacement?.let(::ready) == true
            }
            val current = checkNotNull(replacement)
            check(lease(current) != oldLease)
            val expected = when (mode) {
                AppPrefs.Advanced.UiMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
                AppPrefs.Advanced.UiMode.DARK -> Configuration.UI_MODE_NIGHT_YES
                AppPrefs.Advanced.UiMode.AUTO -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            }
            main { check((current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == expected) }
            return current
        }

        fun restoreClipboard() {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            main {
                check(created.any { !it.isDestroyed && !it.isFinishing && it.hasWindowFocus() }) {
                    "Clipboard restoration requires a still-focused owned Activity for real readback"
                }
                val previous = clipboardBackup
                if (previous != null) {
                    clipboard.setPrimaryClip(ClipData(previous))
                    val actual = checkNotNull(clipboard.primaryClip)
                    check(actual.itemCount == 1 && actual.getItemAt(0).text == previous.getItemAt(0).text)
                    check(actual.getItemAt(0).htmlText == previous.getItemAt(0).htmlText && actual.getItemAt(0).uri == null && actual.getItemAt(0).intent == null)
                    check(actual.description.label == previous.description.label)
                    check(
                        (0 until actual.description.mimeTypeCount).map(actual.description::getMimeType) ==
                            (0 until previous.description.mimeTypeCount).map(previous.description::getMimeType),
                    )
                    check(Build.VERSION.SDK_INT < 24 || Api24.noExtras(actual.description))
                } else {
                    if (Build.VERSION.SDK_INT >= 28) Api28.clear(clipboard) else error("No public absent clipboard restoration on this API")
                    check(!clipboard.hasPrimaryClip() && clipboard.primaryClip == null)
                }
                clipboardRestored = true
            }
        }

        fun cleanupAndReport() {
            workElapsed = SystemClock.uptimeMillis() - started
            cleaning = true
            cleanupDeadline = minOf(deadline, SystemClock.uptimeMillis() + 60_000)
            directOpen = false
            fun clean(block: () -> Unit) {
                try {
                    check(SystemClock.uptimeMillis() <= cleanupDeadline) { "Pilot cleanup budget expired before operation" }
                    block()
                    check(SystemClock.uptimeMillis() <= cleanupDeadline) { "Pilot cleanup budget expired after operation" }
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
            // Restore sensitive external state while an owned Activity still has foreground read access.
            val fixtureOwner = fixtureActivity
            val fixtureReport = fixtureOriginalReport
            if (fixtureOwner != null && fixtureReport != null) {
                clean {
                    main { if (!fixtureOwner.isDestroyed) fixtureOwner.javaClass.getDeclaredField("report").apply { isAccessible = true }.set(fixtureOwner, fixtureReport) }
                }
            }
            if (clipboardCaptured && clipboardWriteAttempted && !clipboardRestored) {
                clean {
                    val expectedChooser = chooserPackage
                    if (chooserObserved && expectedChooser != null) {
                        val root = instrumentation.uiAutomation.rootInActiveWindow
                        val stillChooser = root?.let {
                            try {
                                it.packageName?.toString() == expectedChooser
                            } finally {
                                @Suppress("DEPRECATION")
                                it.recycle()
                            }
                        } == true
                        if (stillChooser) {
                            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                            chooserObserved = false
                            await("Cleanup Back from the observed chooser did not restore an owned foreground window", 10_000) {
                                main { created.any { !it.isDestroyed && !it.isFinishing && it.hasWindowFocus() } }
                            }
                        }
                    }
                    restoreClipboard()
                }
            }
            // Finish only Activities actually created by this probe. Never force-stop/clear/uninstall.
            clean { main { created.asReversed().filter { !it.isFinishing && !it.isDestroyed }.forEach(Activity::finish) } }
            clean { await("Owned Activity cleanup did not reach onDestroy", 30_000) { main { created.all { it.isDestroyed } } } }
            val backup = preferencesBackup
            val preferenceStore = store
            val clipboardSafetyHold = clipboardWriteAttempted && !clipboardRestored
            if (backup != null && preferenceStore != null) {
                clean {
                    main {
                        val safeBackup = if (clipboardSafetyHold) backup - AppPrefs.defaultInstance().clipboard.clipboardListening.key else backup
                        restore(preferenceStore, safeBackup)
                        if (clipboardSafetyHold) AppPrefs.defaultInstance().clipboard.clipboardListening.setValue(false)
                    }
                    check(!clipboardSafetyHold) { "Synthetic clipboard could not be restored; history remains disabled and this VM cannot be reused" }
                }
            }
            originalDelegateMode?.let { mode ->
                clean {
                    main {
                        if (mode != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) AppCompatDelegate.setDefaultNightMode(mode)
                        // AppCompat1.7.1 has no public setter accepting UNSPECIFIED. Never write its private global field.
                        delegateModeRestored = AppCompatDelegate.getDefaultNightMode() == mode
                        processRetirementRequired = !delegateModeRestored
                        report.put("appcompat_default_mode_before", mode).put("appcompat_default_mode_after", AppCompatDelegate.getDefaultNightMode())
                        check(mode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED || delegateModeRestored)
                    }
                }
            }
            originalMode?.let { mode ->
                clean {
                    runBlocking {
                        withTimeout(minOf(30_000, (cleanupDeadline - SystemClock.uptimeMillis()).coerceAtLeast(1))) {
                            ThemeManager.init(PublicWindowPilotConfiguration.wrap(context, mode).resources.configuration)
                        }
                    }
                }
            }
            beforeSessions?.let { initial ->
                clean {
                    main {
                        val observed = sessions()
                        report.put("session_owners_after", JSONArray(observed.sorted()))
                            .put("session_owners_added", JSONArray((observed - initial).sorted()))
                            .put("session_owners_removed", JSONArray((initial - observed).sorted()))
                            .put("session_engine_after", RimeDaemon.engineState.value.name)
                        check(observed == initial) { "Pilot left different Rime session owners: before=$initial after=$observed" }
                    }
                }
            }
            beforeIme?.let { initial -> clean { check(ime(context) == initial) { "Default IME presence/value changed; probe never sets IME" } } }
            if (callbacksRegistered) clean { main { application.unregisterActivityLifecycleCallbacks(callbacks) } }
            if (directory.isDirectory) {
                clean {
                    report.put("run_id", token).put("checks", checks).put("screenshots", screenshots).put("windows", windows)
                        .put("copy_share_requested", copyShare).put("copy_touched", copied).put("copy_share_checks", copyShareChecks)
                        .put("clipboard_payload_restored", clipboardRestored).put("clipboard_platform_timestamp_restored", false)
                        .put("clipboard_write_attempted", clipboardWriteAttempted).put("clipboard_history_safety_hold", clipboardSafetyHold)
                        .put("direct_activity_violations", directViolations)
                        .put("cleanup_errors", cleanupErrors).put("passed", failure == null)
                        .put("cleanup_complete", cleanupErrors.length() == 0).put("default_ime_unchanged", beforeIme != null && ime(context) == beforeIme)
                        .put("cleanup_scope", "Owned Activity/session/persistent preference/IME/clipboard cleanup; not all initialized process-global caches")
                        .put("appcompat_default_mode_restored", delegateModeRestored).put("process_retirement_required", processRetirementRequired)
                        .put("automatic_reuse_allowed", failure == null && !processRetirementRequired)
                        .put("preferences_owned_keys_restored", backup != null && preferenceStore != null && backup.all { (key, value) -> preferenceStore.all[key] == value && preferenceStore.contains(key) == (value != null) })
                        .put("warning_interval_host_reviewed", false).put("whole_app_warning_closed", false)
                        .put("work_budget_ms", 240_000).put("work_elapsed_ms", workElapsed)
                        .put("cleanup_budget_ms", 60_000).put("total_budget_ms", 300_000)
                        .put("report_elapsed_ms", SystemClock.uptimeMillis() - started)
                    failure?.let { report.put("error", it.stackTraceToString()) }
                    val file = File(directory, "report.json")
                    file.writeText(report.toString(2) + "\n")
                    val row = binding(file)
                    result.putString("public_window_pilot_report_path", row.getString("path"))
                    result.putString("public_window_pilot_report_sha256", row.getString("sha256"))
                    result.putLong("public_window_pilot_report_bytes", row.getLong("bytes"))
                }
            }
        }

        try {
            check(disposable && Build.HARDWARE in setOf("ranchu", "goldfish") && Build.VERSION.SDK_INT in setOf(21, 35)) {
                "Explicit disposable emulator opt-in and API21/35 required"
            }
            check(!directory.exists() && directory.mkdirs())
            // Read the real policy before any direct Window; route fixtures are explicitly synthetic.
            check(runBlocking { withTimeout(30_000) { !SetupActivity.shouldShowUp() } }) {
                "Pilot requires an already completed disposable route profile before opening any Window"
            }
            PublicImeWindowPreflight.synchronize(
                instrumentation,
                workDeadline,
                deadline,
                JSONObject().also { report.put("ime_preflight_window", it) },
                ::sessions,
            )
            val prefs = AppPrefs.defaultInstance()
            val preferenceStore = prefs.advanced.uiMode.sharedPreferences
            val touchedKeys = listOf(prefs.advanced.uiMode.key, prefs.clipboard.clipboardListening.key, ThemeManager.prefs.selectedTheme.key)
            store = preferenceStore
            val rawPreferences = touchedKeys.associateWith { preferenceStore.all[it] }
            preferencesBackup = rawPreferences
            val rawMode = rawPreferences[prefs.advanced.uiMode.key]
            check(rawMode == null || (rawMode is String && AppPrefs.Advanced.UiMode.entries.any { it.name == rawMode.uppercase() })) {
                "Pilot requires a valid existing UI policy; it does not repair malformed stored preferences"
            }
            originalMode = prefs.advanced.uiMode.getValue()
            originalDelegateMode = AppCompatDelegate.getDefaultNightMode()
            beforeIme = ime(context)
            val imeClass = TrimeInputMethodService::class.java.name
            val selectedIme = "${context.packageName}/$imeClass"
            check(beforeIme == (true to selectedIme)) { "Pilot requires the actual candidate IME to stay selected" }
            val imeOwnerPrefix = "$imeClass#"
            // Public selection can still refer to the service in a process replaced by instrumentation.
            // Wait for this PID's actual IME session before taking the full lifecycle baseline.
            await("The selected IME did not establish one READY session in this process before any pilot Window") {
                main {
                    val observed = sessions()
                    val state = RimeDaemon.engineState.value
                    report.put("session_owners_preflight_observed", JSONArray(observed.sorted()))
                        .put("session_engine_preflight_observed", state.name)
                    observed.size == 1 && observed.single().startsWith(imeOwnerPrefix) && state == RimeLifecycle.State.READY
                }
            }
            beforeSessions = main {
                val observed = sessions()
                check(
                    observed.size == 1 && observed.single().startsWith(imeOwnerPrefix) &&
                        RimeDaemon.engineState.value == RimeLifecycle.State.READY,
                )
                observed
            }
            report.put("session_owners_before", JSONArray(checkNotNull(beforeSessions).sorted()))
                .put("session_baseline_elapsed_ms", SystemClock.uptimeMillis() - started)
            main {
                application.registerActivityLifecycleCallbacks(callbacks)
                callbacksRegistered = true
            }
            val configurationRows = PublicWindowPilotConfigurationAssertions.verify(context)
            check(configurationRows.length() == 6)
            report.put("configuration_rows", configurationRows)
            passed("six real Android configuration merges preserve font locale density viewport and input contexts")

            // Before any Main/AppCompat Activity. Host must authenticate this new PID/start and inspect the full raw interval.
            directOpen = true
            marker("direct_begin")
            var activity = launch()
            val firstLease = lease(activity)
            val originalReport = main { ownField(activity, "report") as String }
            check(originalReport.endsWith(ThemeDiagnostics.format(ThemeManager.prefs.selectedTheme.getValue(), ThemeManager.activeTheme.name, ThemeManager.activeFindings)))
            capture(activity, "direct-initial")
            window(activity, "direct-initial")
            passed("direct public window keeps toolbar title subtitle Up name native insets and live diagnostics session")

            val beforeConfiguration = main { Configuration(activity.resources.configuration) }
            for (mode in listOf(AppPrefs.Advanced.UiMode.LIGHT, AppPrefs.Advanced.UiMode.DARK, AppPrefs.Advanced.UiMode.AUTO)) {
                if (prefs.advanced.uiMode.getValue() == mode) {
                    activity = changeMode(activity, if (mode == AppPrefs.Advanced.UiMode.LIGHT) AppPrefs.Advanced.UiMode.DARK else AppPrefs.Advanced.UiMode.LIGHT)
                }
                activity = changeMode(activity, mode)
                val actual = main { Configuration(activity.resources.configuration) }
                check(actual.fontScale == beforeConfiguration.fontScale && actual.densityDpi == beforeConfiguration.densityDpi)
                check(ConfigurationCompat.getLocales(actual) == ConfigurationCompat.getLocales(beforeConfiguration))
                check(actual.screenWidthDp == beforeConfiguration.screenWidthDp && actual.screenHeightDp == beforeConfiguration.screenHeightDp)
                check(actual.smallestScreenWidthDp == beforeConfiguration.smallestScreenWidthDp && actual.orientation == beforeConfiguration.orientation)
                check((actual.uiMode and Configuration.UI_MODE_TYPE_MASK) == (beforeConfiguration.uiMode and Configuration.UI_MODE_TYPE_MASK))
                capture(activity, "mode-${mode.name}")
                window(activity, "mode-${mode.name}")
            }
            check(main { firstLease !in sessions() })
            passed("Light Dark and Auto each recreate actual windows preserve non-night configuration and release previous leases")
            check(main { directViolations.length() == 0 && created.none { it is AppCompatActivity } })
            marker("direct_end")
            directOpen = false

            if (copyShare) {
                marker("copy_share_begin")
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val backup = main { clipboard.primaryClip?.let(::ClipData) }
                // API21 has no public absent-state restore; refuse before writing instead of inventing an empty equivalent.
                check(backup != null || Build.VERSION.SDK_INT >= 28) { "API21 copy stage requires a pre-existing restorable clipboard; no public clearPrimaryClip" }
                check(
                    backup == null || (
                        backup.itemCount == 1 && backup.getItemAt(0).uri == null && backup.getItemAt(0).intent == null &&
                            backup.getItemAt(0).htmlText == null && (backup.getItemAt(0).text == null || backup.getItemAt(0).text is String) &&
                            (Build.VERSION.SDK_INT < 24 || Api24.noExtras(backup.description))
                        ),
                ) {
                    "This narrow copy stage accepts only an existing plain-text clipboard or absence on API28+"
                }
                clipboardBackup = backup
                clipboardCaptured = true
                main { prefs.clipboard.clipboardListening.setValue(false) }
                await("The real clipboard listener did not detach") { main { ownField(ClipboardHelper, "listenerRegistered") == false } }
                val synthetic = "PUBLIC_WINDOW_PILOT_$token\nSynthetic diagnostics report; no personal input."
                fixtureActivity = activity
                fixtureOriginalReport = main { ownField(activity, "report") as String }
                main { activity.javaClass.getDeclaredField("report").apply { isAccessible = true }.set(activity, synthetic) }
                capture(activity, "before-copy")
                clipboardWriteAttempted = true
                touch(main { activity.findViewById<Button>(R.id.copyButton) })
                await("The actual Copy touch did not publish the synthetic report") {
                    main { clipboard.primaryClip?.let { it.itemCount == 1 && it.getItemAt(0).text.toString() == synthetic && it.description.label.toString() == "theme-diagnostics" } == true }
                }
                copied = true
                copyShareChecks++
                passed("real Copy touch publishes exact synthetic report with original label while history listener is detached")
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, synthetic)
                    },
                    activity.getString(R.string.share),
                )
                val resolvedChooserPackage = checkNotNull(context.packageManager.resolveActivity(chooser, 0)).activityInfo.packageName
                chooserPackage = resolvedChooserPackage
                touch(main { activity.findViewById<Button>(R.id.shareButton) })
                await("The actual Share touch did not open the system chooser") {
                    val node = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
                    try {
                        node.packageName?.toString() == resolvedChooserPackage
                    } finally {
                        @Suppress("DEPRECATION")
                        node.recycle()
                    }
                }
                chooserObserved = true
                report.put("actual_share_chooser_package", resolvedChooserPackage).put("share_payload_extra_parity_tested", false)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await("Back from real chooser did not resume diagnostics") { ready(activity) }
                chooserObserved = false
                main {
                    check(ownField(activity, "report") == synthetic)
                    activity.javaClass.getDeclaredField("report").apply { isAccessible = true }.set(activity, fixtureOriginalReport)
                }
                restoreClipboard()
                copyShareChecks++
                passed("real Share touch opens system chooser and ordinary Back restores the original report and clipboard payload")
                marker("copy_share_end")
            }

            val leavingLease = lease(activity)
            capture(activity, "before-back")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await("Ordinary Back did not destroy diagnostics and release its exact session") { main { activity.isDestroyed && leavingLease !in sessions() } }
            passed("ordinary system Back destroys diagnostics through its normal lifecycle and releases the exact session")

            check(runBlocking { withTimeout(30_000) { !SetupActivity.shouldShowUp() } }) { "Parent route stage requires an already completed disposable setup profile" }
            main { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            var parent: MainActivity? = null
            await("Existing manifest parent Main did not resume") {
                parent = main { created.filterIsInstance<MainActivity>().lastOrNull { !it.isDestroyed && !it.isFinishing } }
                main { parent?.let { it in resumed && it.hasWindowFocus() && it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) } == true }
            }
            val existingParent = checkNotNull(parent)
            val parentCount = main { created.count { it is MainActivity } }
            activity = launch(existingParent)
            val upLease = lease(activity)
            main { check(!activity.shouldUpRecreateTask(checkNotNull(NavUtils.getParentActivityIntent(activity)))) }
            capture(activity, "before-existing-parent-up")
            val navigation = main { descendants(activity.findViewById<Toolbar>(R.id.toolbar)).single { it.isClickable && it.contentDescription?.toString() == activity.getString(R.string.public_window_navigate_up) } }
            touch(navigation)
            await("The real Up touch did not return to the existing manifest parent") {
                main { activity.isDestroyed && upLease !in sessions() && existingParent in resumed && existingParent.hasWindowFocus() }
            }
            main { check(created.count { it is MainActivity } == parentCount && !existingParent.isDestroyed) }
            report.put("existing_parent_task_id", main { existingParent.taskId }).put("existing_parent_reused", true)
            passed("real toolbar Up follows manifest policy to the existing Main task without a duplicate parent")
            check(checks.length() == if (copyShare) 7 else 5)
            check(!copyShare || (copyShareChecks == 2 && clipboardRestored))
            check(SystemClock.uptimeMillis() <= workDeadline) { "Pilot work exceeded its 240000ms work budget" }
        } catch (error: Throwable) {
            failure = error
        } finally {
            cleanupAndReport()
        }
        val acceptedElapsed = SystemClock.uptimeMillis() - started
        if (acceptedElapsed > 300_000) {
            val late = IllegalStateException("Pilot exceeded its 300000ms total work plus cleanup budget")
            val primary = failure
            if (primary == null) failure = late else primary.addSuppressed(late)
            report.put("total_deadline_respected", false)
        }
        val finalFailure = failure
        if (finalFailure != null && directory.isDirectory) {
            try {
                report.put("passed", false).put("automatic_reuse_allowed", false).put("accepted_elapsed_ms", acceptedElapsed)
                    .put("cleanup_complete", cleanupErrors.length() == 0).put("error", finalFailure.stackTraceToString())
                val file = File(directory, "report.json")
                file.writeText(report.toString(2) + "\n")
                val row = binding(file)
                result.putString("public_window_pilot_report_path", row.getString("path"))
                result.putString("public_window_pilot_report_sha256", row.getString("sha256"))
                result.putLong("public_window_pilot_report_bytes", row.getLong("bytes"))
            } catch (error: Throwable) {
                if (error !== finalFailure) finalFailure.addSuppressed(error)
            }
        }
        result.putLong("public_window_pilot_accepted_elapsed_ms", acceptedElapsed)
        result.putLong("public_window_pilot_total_budget_ms", 300_000)
        result.putBoolean("public_window_pilot_passed", failure == null)
        result.putBoolean("public_window_pilot_cleanup_complete", cleanupErrors.length() == 0)
        result.putInt("public_window_pilot_checks", checks.length())
        result.putBoolean("public_window_pilot_copy_share_requested", copyShare)
        result.putBoolean("public_window_pilot_process_retirement_required", processRetirementRequired)
        result.putBoolean("public_window_pilot_automatic_reuse_allowed", failure == null && !processRetirementRequired)
        failure?.let { result.putString("error", it.stackTraceToString().take(12_000)) }
        instrumentation.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    @RequiresApi(24)
    private object Api24 {
        @DoNotInline fun noExtras(description: ClipDescription) = description.extras?.isEmpty != false
    }

    @RequiresApi(28)
    private object Api28 {
        @DoNotInline fun clear(clipboard: ClipboardManager) = clipboard.clearPrimaryClip()
    }
}
