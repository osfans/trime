/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.osfans.trime.core.RimeConfig
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeSchema
import com.osfans.trime.core.T9Action
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.Keyboard
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Emulator-only integration probe; no user dictionary is cleared or imported. */
class StartupResponsivenessInstrumentation : Instrumentation() {
    private var safOnly = false
    private var safTree: String? = null
    private var safRevoke = false
    private var clipOnly = false
    private var clipSaveOnly = false
    private var t02Only = false
    private var t03Only = false
    private var t03GeometryOnly = false
    private var t05Only = false
    private var t08Only = false
    private var t09Only = false
    private var t10Only = false
    private var t11Only = false
    private var t13Only = false
    private var t09Baseline = true
    private var t09Schema = "luna_pinyin_t9"
    private var setupOnly = false
    private var setupClosedLoopOnly = false
    private var configImportOnly = false
    private var configImportTree: String? = null
    private var shutdownOnly = false
    private var failureOnly = false
    private var lateStorageOnly = false
    private var feedbackOnly = false
    private var editorsOnly = false
    private var editorsAnimationCallbacks = true
    private var logResourcesOnly = false
    private var privacyOnly = false
    private var publicHostMode: String? = null
    private var publicHostDisposable = false
    private var publicPreferenceOnly = false
    private var publicPreferenceDisposable = false
    private var publicPreferenceCode = ""
    private var publicWindowPilotOnly = false
    private var publicWindowPilotDisposable = false
    private var publicWindowPilotCopyShare = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        safOnly = arguments?.getString("saf") == "true"
        safTree = arguments?.getString("safTree")
        safRevoke = arguments?.getString("safRevoke") == "true"
        clipOnly = arguments?.getString("clip") == "true"
        clipSaveOnly = arguments?.getString("clipSave") == "true"
        t02Only = arguments?.getString("t02") == "true"
        t03Only = arguments?.getString("t03") == "true"
        t03GeometryOnly = arguments?.getString("t03GeometryOnly") == "true" || arguments?.getString("t05GeometryOnly") == "true"
        t05Only = arguments?.getString("t05") == "true"
        t08Only = arguments?.getString("t08") == "true"
        t09Only = arguments?.getString("t09") == "true"
        t10Only = arguments?.getString("t10") == "true"
        t11Only = arguments?.getString("t11") == "true"
        t13Only = arguments?.getString("t13") == "true"
        t09Baseline = arguments?.getString("t09Baseline") != "false"
        t09Schema = arguments?.getString("t09Schema") ?: "luna_pinyin_t9"
        setupOnly = arguments?.getString("setup") == "true"
        setupClosedLoopOnly = arguments?.getString("setup_closed_loop") == "true"
        configImportOnly = arguments?.getString("config_import") == "true"
        configImportTree = arguments?.getString("config_import_tree")
        shutdownOnly = arguments?.getString("shutdown") == "true"
        failureOnly = arguments?.getString("startupFailure") == "true"
        lateStorageOnly = arguments?.getString("lateStorage") == "true"
        feedbackOnly = arguments?.getString("feedback") == "true"
        editorsOnly = arguments?.getString("editors") == "true"
        editorsAnimationCallbacks = arguments?.getString("editorsAnimationCallbacks") != "false"
        logResourcesOnly = arguments?.getString("log_resources") == "true"
        privacyOnly = arguments?.getString("privacy") == "true"
        publicHostMode = arguments?.getString("public_host_mode")
        publicHostDisposable = arguments?.getString("public_host_disposable") == "true"
        publicPreferenceOnly = arguments?.getString("public_preference_dialogs") == "true"
        publicPreferenceDisposable = arguments?.getString("public_preference_disposable") == "true"
        publicPreferenceCode = arguments?.getString("public_preference_code_sha") ?: ""
        publicWindowPilotOnly = arguments?.getString("public_window_pilot") == "true"
        publicWindowPilotDisposable = arguments?.getString("public_window_pilot_disposable") == "true"
        publicWindowPilotCopyShare = arguments?.getString("public_window_pilot_copy_share") == "true"
        start()
    }

    override fun onStart() {
        sendStatus(0, Bundle().apply { putInt("audit_pid", android.os.Process.myPid()) })
        // start() can launch this thread before Application.onCreate() returns.
        // Never initialize production preferences from the test to hide that race.
        val applicationWait = SystemClock.uptimeMillis()
        waitForIdleSync()
        sendStatus(0, Bundle().apply { putLong("application_ready_wait_ms", SystemClock.uptimeMillis() - applicationWait) })
        val hostMode = publicHostMode
        if (hostMode != null) {
            PublicHostWindowProbe.run(this, publicHostDisposable, hostMode)
            return
        }
        if (publicPreferenceOnly) {
            PublicPreferenceDialogProbe.run(this, publicPreferenceDisposable, publicPreferenceCode)
            return
        }
        if (publicWindowPilotOnly) {
            PublicWindowPilotProbe.run(this, publicWindowPilotDisposable, publicWindowPilotCopyShare)
            return
        }
        if (t13Only) {
            T13AccessibilityProbe.run(this)
            return
        }
        if (t11Only) {
            NextWordPredictionProbe.run(this)
            return
        }
        if (t10Only) {
            PersonalDictionaryProbe.run(this)
            return
        }
        if (t09Only) {
            MixedInputProbe.run(this, t09Baseline, t09Schema)
            return
        }
        if (t08Only) {
            CandidateSnapshotProbe.run(this)
            return
        }
        if (editorsOnly) {
            EditorLifecycleProbe.run(this, editorsAnimationCallbacks)
            return
        }
        if (logResourcesOnly) {
            LogcatResourcesProbe.run(this)
            return
        }
        if (privacyOnly) {
            PrivacyModeProbe.run(this)
            return
        }
        if (feedbackOnly) {
            InputFeedbackProbe.run(this)
            return
        }
        if (failureOnly) {
            StartupFailureProbe.run(this)
            return
        }
        if (lateStorageOnly) {
            LateStorageProbe.run(this)
            return
        }
        if (setupOnly) {
            SetupResponsivenessProbe.run(this)
            return
        }
        if (setupClosedLoopOnly) {
            SetupClosedLoopProbe.run(this)
            return
        }
        if (configImportOnly) {
            ConfigImportProbe.run(this, configImportTree)
            return
        }
        if (t02Only || t03Only || t05Only) {
            T9EditingProbe.run(this, t03Only, t03GeometryOnly, t05Only)
            return
        }
        if (clipOnly) {
            ClipEditorProbe.run(this)
            return
        }
        if (clipSaveOnly) {
            ClipSaveProbe.run(this)
            return
        }
        if (safOnly) {
            SafCompatibilityProbe.run(this, safTree, safRevoke)
            return
        }
        val result = Bundle()
        val handler = Handler(Looper.getMainLooper())
        val ticks = AtomicInteger()
        val maxGap = AtomicLong()
        var previous = SystemClock.uptimeMillis()
        val heartbeat = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                // Only the main looper writes; keep the probe executable on API 21.
                maxGap.set(maxOf(maxGap.get(), now - previous))
                previous = now
                ticks.incrementAndGet()
                handler.postDelayed(this, 50)
            }
        }
        var session: RimeSession? = null
        var success = false
        fun phase(name: String) {
            android.util.Log.i("TrimeRuntimeAudit", name)
            sendStatus(0, Bundle().apply { putString("stream", "$name: main ticks=${ticks.get()}, maximum gap=${maxGap.get()} ms\n") })
        }
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "This probe only runs on an emulator" }
            handler.post(heartbeat)
            result.putInt("coroutine_packaging_checks", CoroutinePackagingProbe.verify(this))
            if (shutdownOnly) {
                runBlocking {
                    withTimeout(300_000) {
                        check(RimeDaemon.getFirstSessionOrNull() == null) { "Shutdown probe requires no other clients" }
                        // Record the brief startup state before creating the client.
                        // The later Main-thread removal may occur after it reaches READY.
                        val startupObserved = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                            RimeDaemon.engineState.first { it == RimeLifecycle.State.STARTING }
                        }
                        val first = withContext(Dispatchers.Main) { RimeDaemon.createSession("abandoned-startup") }
                        val stopped = first.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
                        startupObserved.await()
                        phase("Initial STARTING observed before client removal")
                        withContext(Dispatchers.Main) { RimeDaemon.destroySession("abandoned-startup") }
                        stopped.join()
                        check(stopped.isCancelled) { "Removed client's jobs were not cancelled" }
                        RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                        phase("Abandoned startup finalized")
                        val retained = withContext(Dispatchers.Main) {
                            val removed = RimeDaemon.createSession("replaced-startup")
                            val stale = removed.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
                            RimeDaemon.destroySession("replaced-startup")
                            check(stale.isCancelled)
                            val next = RimeDaemon.createSession("retained-startup")
                            check(runCatching { removed.run { isReady } }.isFailure)
                            next to next.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
                        }
                        try {
                            retained.first.runOnReady { check(isReady) }
                            delay(200)
                            check(!retained.second.isCancelled) { "A new client was shut down by the abandoned startup" }
                        } finally {
                            withContext(Dispatchers.Default) { RimeDaemon.destroySession("retained-startup") }
                        }
                        retained.second.join()
                        check(retained.second.isCancelled)
                        RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                        phase("Reattached client retained until its own shutdown")
                        repeat(5) { cycle ->
                            val previous = withContext(Dispatchers.Main) { RimeDaemon.createSession("rapid-reconnect") }
                            previous.runOnReady { check(isReady) }
                            val next = withContext(Dispatchers.Main) {
                                RimeDaemon.destroySession("rapid-reconnect")
                                RimeDaemon.createSession("rapid-reconnect")
                            }
                            check(next !== previous)
                            check(runCatching { previous.run { isReady } }.isFailure)
                            next.runOnReady { check(isReady) }
                            // Cancellation survives a fast STOPPING/STOPPED transition
                            // that a conflated StateFlow observer could miss.
                            val oldGeneration = next.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                awaitCancellation()
                            }
                            check(oldGeneration.isActive) { "Restart observer was cancelled before the request" }
                            withContext(Dispatchers.Main) { RimeDaemon.restartRime() }
                            oldGeneration.join()
                            check(oldGeneration.isCancelled) { "Restart did not cancel the old generation" }
                            phase("Restart cycle $cycle cancelled the old generation")
                            next.runOnReady { check(isReady) }
                            withContext(Dispatchers.Main) { RimeDaemon.destroySession("rapid-reconnect") }
                            RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                            phase("Ready/reconnect/restart cycle $cycle completed")
                        }
                    }
                }
                check(maxGap.get() < 4000)
                success = true
                result.putString("stream", "PASS: abandoned startup cleanup and reattach; main ticks=${ticks.get()}, maximum gap=${maxGap.get()} ms\n")
                return
            }
            runOnMainSync {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    check(TrimeInputMethodService().onCreateInlineSuggestionsRequest(Bundle()) == null)
                }
            }
            runOnMainSync { session = RimeDaemon.createSession(javaClass.name) }
            val rime = checkNotNull(session)
            runBlocking {
                withTimeout(300_000) {
                    rime.runOnReady { }
                    phase("Engine ready")
                    withContext(Dispatchers.Main) { ThemeManager.init(targetContext.resources.configuration) }
                    val selected = ThemeManager.prefs.selectedTheme.getValue()
                    val originalScope = ColorManager.currentScope()
                    var themeChanges = 0
                    val reloadListener = ThemeManager.OnThemeChangeListener { themeChanges++ }
                    withContext(Dispatchers.Main) { ThemeManager.addOnChangedListener(reloadListener) }
                    try {
                        repeat(3) {
                            ThemeManager.selectTheme(selected)
                            check(ColorManager.currentScope() === originalScope) { "Equal-theme reload detached the view scope" }
                        }
                        check(themeChanges == 0) { "Equal-theme selection emitted a change" }
                        val themeBeforeForcedReload = ThemeManager.activeTheme
                        ThemeManager.selectTheme(selected, forceReload = true)
                        check(ColorManager.currentScope() !== originalScope) { "Forced equal-theme reload retained stale caches and scope" }
                        check(ThemeManager.activeTheme == themeBeforeForcedReload) { "Forced reload changed the decoded theme" }
                        check(themeChanges == 1) { "Forced equal-theme reload did not emit exactly one change" }
                    } finally {
                        withContext(Dispatchers.Main) { ThemeManager.removeOnChangedListener(reloadListener) }
                    }
                    try {
                        phase("Inject missing theme")
                        check(ThemeManager.selectTheme("__missing_anr_test__") == "trime")
                    } finally {
                        ThemeManager.selectTheme(selected)
                        phase("Missing theme restored")
                    }
                    phase("Theme reload and fallback complete")
                    rime.runOnReady {
                        JniInteropRegression.verify()
                        RimeConfig.openConfig("default").use { expected ->
                            check(RimeSchema(".default").alphabet == (expected.getString("speller/alphabet") ?: ""))
                            expected.close()
                            expected.close()
                            check(expected.getString("speller/alphabet") == null)
                        }
                        val presets = ThemeManager.activeTheme.presetKeyboards
                        if (selected == "trime") {
                            val base = checkNotNull(presets["default"])
                            val letter = checkNotNull(presets["letter"])
                            check(letter == base.copy(asciiMode = 1, resetAsciiMode = true, lock = false))
                            check(presets["scj6"] == presets["cangjie5"])
                        }
                        val originalSchema = selectedSchemaId()
                        val originalAscii = getRuntimeOption("ascii_mode")
                        try {
                            check(selectSchema("luna_pinyin_t9"))
                            for (enabled in listOf(true, false)) {
                                setRuntimeOption("ascii_mode", enabled)
                                check(getRuntimeOptionCached("ascii_mode") == enabled)
                            }
                            clearComposition()
                            "64426".forEach { check(processKey(it.code)) }
                            check(t9Cached.choices.any { it.spelling == "mi" })
                            val ni = t9Cached.choices.first { it.spelling == "ni" && !it.completion }
                            check(t9Action(t9Cached.revision, T9Action.Lock, ni.start, ni.end, ni.spelling))
                            val locked = t9Cached
                            check(locked.input == "64426" && locked.segments.any { it.locked && it.spelling == "ni" })
                            withContext(Dispatchers.Main) { ThemeMergeRegression.verify(targetContext, locked) }
                            check(t9Cached.revision == locked.revision && t9Cached.input == "64426")
                            clearComposition()
                            check(selectSchema("luna_pinyin"))
                            check(getRuntimeOptionCached("ascii_mode") == getRuntimeOption("ascii_mode"))
                        } finally {
                            clearComposition()
                            selectSchema(originalSchema)
                            setRuntimeOption("ascii_mode", originalAscii)
                        }
                    }
                    phase("Schema, option cache and portrait/landscape T9 recolor verified")
                    rime.runOnReady { SchemaPickerRegression.verify(targetContext, this) }
                    phase("F4 schema picker and empty list verified")
                    val queries = async(Dispatchers.Default) {
                        repeat(20) { rime.runOnReady { selectedSchemata() } }
                    }
                    withContext(Dispatchers.Main) {
                        val theme = ThemeManager.activeTheme
                        val keyboard = Keyboard(targetContext, theme, 720, theme.presetKeyboards["luna_pinyin_t9"])
                        val mode = KeyAction(KeyActionToken.Plain("Mode_switch"), theme.presetKeys)
                        repeat(100) { mode.getLabel(keyboard) }
                    }
                    queries.await()
                    phase("Busy queries and toggle drawing complete")
                    delay(200)
                }
            }
            check(ticks.get() >= 4) { "Main looper did not make progress" }
            check(maxGap.get() < 4000) { "Main looper stalled for ${maxGap.get()} ms" }
            success = true
            result.putString("stream", "PASS: startup, theme reload/fallback, schema/option cache, busy queries and toggle drawing; main ticks=${ticks.get()}, maximum gap=${maxGap.get()} ms\n")
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
        } finally {
            handler.removeCallbacks(heartbeat)
            if (session != null) RimeDaemon.destroySession(javaClass.name)
            if (shutdownOnly) {
                listOf("abandoned-startup", "replaced-startup", "retained-startup", "rapid-reconnect").forEach {
                    RimeDaemon.destroySession(it)
                }
            }
            result.putLong("max_main_gap_ms", maxGap.get())
            result.putInt("main_ticks", ticks.get())
            result.putBoolean("passed", success)
            finish(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }
}
