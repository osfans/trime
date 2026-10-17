/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.system.Os
import android.system.OsConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.mikepenz.aboutlibraries.Libs
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.core.data.DataStorageMode
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.common.PublicWindowPilotActivity
import com.osfans.trime.ui.common.PublicWindowPilotConfiguration
import com.osfans.trime.ui.main.LicenseFragment
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.ui.main.settings.ProfileSettingsFragment
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.ui.setup.SetupAdvancedSettingsActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Small, independent host modes; does not replace Setup14, Clip12, T10 or strict-import checks. */
internal object PublicHostWindowProbe {
    fun run(instrumentation: Instrumentation, disposable: Boolean, mode: String) {
        Runner(instrumentation, disposable, mode).run()
    }

    private class Runner(
        private val instrumentation: Instrumentation,
        private val disposable: Boolean,
        private val mode: String,
    ) {
        private val context = instrumentation.targetContext
        private val application = context.applicationContext as Application
        private val token = UUID.randomUUID().toString()
        private val directory = File(context.filesDir, "runtime-audit/public-host-windows/$token")
        private val started = SystemClock.uptimeMillis()
        private val workDeadline = started + 240_000L
        private val totalDeadline = started + 300_000L
        private var cleanupDeadline = totalDeadline
        private var cleaning = false
        private var failure: Throwable? = null
        private var registered = false
        private var workElapsed = 0L
        private var stateCaptured = false
        private var originalIme: Pair<Boolean, String?>? = null
        private var originalPreferences: Map<String, Any?>? = null
        private var originalGrants: List<String>? = null
        private var originalSessions: Set<String>? = null
        private val created = mutableListOf<Activity>()
        private val resumed = mutableSetOf<Activity>()
        private val checks = JSONArray()
        private val windows = JSONArray()
        private val markers = JSONArray()
        private val screenshots = JSONArray()
        private val cleanupErrors = JSONArray()
        private val externalIntentMonitors = mutableListOf<Instrumentation.ActivityMonitor>()
        private val report = JSONObject().put("schema", "public-host-window-native/v1")
            .put("mode", mode).put("run_id", token).put("api", Build.VERSION.SDK_INT)
            .put("pid", Process.myPid()).put("uid", Process.myUid())
            .put("scope", "Reached independent Main/Advanced/Log host or U cancellation paths only; no global warning or visual acceptance")
            .put("physical_font_rotation_split_tested", false).put("mode_recreation_cycle_tested", false)
            .put("action_mode_tested", false).put("retry_positive_tested", false)
            .put("dictionary_dialog_business_tested", false).put("whole_app_warning_closed", false)
            .put("human_png_reviewed", false)

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

        private fun checkTime() {
            val bound = if (cleaning) cleanupDeadline else workDeadline
            check(SystemClock.uptimeMillis() <= bound) { "Public host $mode exceeded its ${if (cleaning) "cleanup" else "work"} deadline" }
        }

        private fun <T> main(block: () -> T): T {
            checkTime()
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            val observed = checkNotNull(outcome)
            val businessFailure = observed.exceptionOrNull()
            try {
                checkTime()
            } catch (deadlineFailure: Throwable) {
                if (businessFailure != null) {
                    if (businessFailure !== deadlineFailure) businessFailure.addSuppressed(deadlineFailure)
                    throw businessFailure
                }
                throw deadlineFailure
            }
            return observed.getOrThrow()
        }

        private fun await(label: String, timeout: Long = 30_000L, predicate: () -> Boolean) {
            checkTime()
            val bound = minOf(if (cleaning) cleanupDeadline else workDeadline, SystemClock.uptimeMillis() + timeout)
            while (!predicate()) {
                check(SystemClock.uptimeMillis() < bound) { label }
                SystemClock.sleep(50)
            }
            check(SystemClock.uptimeMillis() <= bound) { "$label (observed too late)" }
        }

        private fun marker(label: String) {
            checkTime()
            val value = "PUBLIC_HOST_WINDOW_${token}_${markers.length() + 1}_$label"
            android.util.Log.i("TrimePublicHostAudit", value)
            markers.put(JSONObject().put("label", label).put("value", value).put("uptime_ms", SystemClock.uptimeMillis()))
            instrumentation.sendStatus(0, Bundle().apply { putString("public_host_marker", value) })
        }

        private fun passed(label: String) {
            checkTime()
            checks.put(label)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS public host ${checks.length()}: $label\n") })
        }

        private fun binding(file: File): JSONObject = JSONObject().put("path", file.absolutePath)
            .put("bytes", file.length()).put(
                "sha256",
                MessageDigest.getInstance("SHA-256")
                    .digest(file.readBytes()).joinToString("") { "%02x".format(it) },
            )

        private fun descendants(root: View): Sequence<View> = sequence {
            yield(root)
            if (root is ViewGroup) root.children.forEach { yieldAll(descendants(it)) }
        }

        private fun sessions(): Set<String> {
            // Read-only app-owned ownership inventory, exactly the established P0 mechanism.
            val owners = RimeDaemon::class.java.getDeclaredField("sessions").run {
                isAccessible = true
                get(RimeDaemon) as Map<*, *>
            }
            return owners.keys.map { it as String }.toSet()
        }

        private fun preferences(): SharedPreferences = AppPrefs.defaultInstance().advanced.uiMode.sharedPreferences

        private fun preferenceSnapshot(): Map<String, Any?> = preferences().all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }

        private fun snapshotPreferences(label: String, values: Map<String, Any?>): JSONObject {
            val rows = JSONArray()
            values.toSortedMap().forEach { (key, value) ->
                val encoded = if (value is Set<*>) JSONArray(value.map { it as String }.sorted()) else value
                rows.put(JSONObject().put("key", key).put("type", value?.javaClass?.name).put("value", encoded))
            }
            val file = File(directory, "$label.json")
            file.writeText(rows.toString(2) + "\n")
            check(file.length() <= 1_048_576L) { "Native preference snapshot exceeded1MiB" }
            return binding(file)
        }

        private fun grants(): List<String> = context.contentResolver.persistedUriPermissions.map {
            "${it.uri}|${it.isReadPermission}|${it.isWritePermission}|${it.persistedTime}"
        }.sorted()

        private fun ime(): Pair<Boolean, String?> {
            val key = Settings.Secure.DEFAULT_INPUT_METHOD
            val cursor = checkNotNull(context.contentResolver.query(Settings.Secure.getUriFor(key), arrayOf("name", "value"), null, null, null))
            return cursor.use {
                check(it.count in 0..1)
                val entry = if (it.moveToFirst()) {
                    check(it.getString(it.getColumnIndexOrThrow("name")) == key)
                    true to it.getString(it.getColumnIndexOrThrow("value"))
                } else {
                    false to null
                }
                check(entry.second == Settings.Secure.getString(context.contentResolver, key))
                entry
            }
        }

        private fun active(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed && activity in resumed

        // Only observes a field in our own Activity; no Android/AppCompat private Window API.
        private fun notification(activity: MainActivity): AlertDialog? = MainActivity::class.java.getDeclaredField("notificationDialog").run {
            isAccessible = true
            get(activity) as AlertDialog?
        }

        private fun touch(view: View) {
            val position = main {
                val visible = Rect()
                check(view.isShown && view.isEnabled && view.width > 0 && view.height > 0 && view.getLocalVisibleRect(visible))
                check(visible == Rect(0, 0, view.width, view.height)) { "Actual touch target is clipped" }
                val origin = IntArray(2)
                view.getLocationOnScreen(origin)
                origin[0] + view.width / 2f to origin[1] + view.height / 2f
            }
            val down = SystemClock.uptimeMillis()
            for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, position.first, position.second, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.sendPointerSync(event)
                } finally {
                    event.recycle()
                }
            }
            checkTime()
        }

        @Suppress("DEPRECATION") // API21 node pooling cleanup; does not change UI or logging.
        private fun recycle(node: AccessibilityNodeInfo) {
            node.recycle()
        }

        private fun awaitVisibleText(text: String) {
            await("Visible action '$text' was not found") {
                val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@await false
                try {
                    if (root.packageName?.toString() != context.packageName) return@await false
                    val matches = root.findAccessibilityNodeInfosByText(text)
                    try {
                        matches.count { it.text?.toString() == text && it.isVisibleToUser && it.isEnabled } == 1
                    } finally {
                        matches.forEach(::recycle)
                    }
                } finally {
                    recycle(root)
                }
            }
        }

        private fun clickVisibleText(text: String) {
            awaitVisibleText(text)
            val root = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            try {
                check(root.packageName?.toString() == context.packageName)
                val matches = root.findAccessibilityNodeInfosByText(text)
                try {
                    var node = matches.single { it.text?.toString() == text && it.isVisibleToUser && it.isEnabled }
                    val parents = mutableListOf<AccessibilityNodeInfo>()
                    try {
                        repeat(4) {
                            if (!node.isClickable) {
                                node = checkNotNull(node.parent)
                                parents += node
                            }
                        }
                        check(node.isClickable && node.isVisibleToUser && node.isEnabled)
                        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Actual visible menu action was rejected" }
                    } finally {
                        parents.forEach(::recycle)
                    }
                } finally {
                    matches.forEach(::recycle)
                }
            } finally {
                recycle(root)
            }
            checkTime()
        }

        private fun toolbar(activity: Activity): Toolbar = checkNotNull(activity.findViewById(R.id.toolbar))

        private fun route(activity: MainActivity): String? = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)?.navController?.currentDestination?.route

        private fun clickMenu(activity: MainActivity, id: Int) {
            val actual = main { activity.findViewById<View>(id)?.takeIf { it.isShown && it.isEnabled } }
            if (actual != null) {
                touch(actual)
            } else {
                val text = main {
                    val bar = toolbar(activity)
                    val item = checkNotNull(bar.menu.findItem(id))
                    check(item.isVisible && item.isEnabled && !bar.isOverflowMenuShowing)
                    check(bar.showOverflowMenu()) { "Real overflow popup did not open" }
                    item.title.toString()
                }
                clickVisibleText(text)
            }
        }

        private fun awaitMainReady(activity: MainActivity) {
            await("Main/Advanced actual content, menu and focus were not ready") {
                main {
                    active(activity) && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        activity.findViewById<Toolbar>(R.id.toolbar)?.menu?.size() == 6 &&
                        (activity.hasWindowFocus() || notification(activity)?.isShowing == true)
                }
            }
            val dialog = main { notification(activity)?.takeIf { it.isShowing } }
            if (dialog != null) {
                marker("notification_inspection_begin")
                val button = main {
                    check(dialog.javaClass == AlertDialog::class.java)
                    check(dialog.window?.decorView?.isShown == true)
                    report.put("notification_platform_dialog_observed", true)
                    checkNotNull(dialog.getButton(AlertDialog.BUTTON_NEGATIVE))
                }
                touch(button)
                await("Actual notification Cancel did not restore the host focus") {
                    main { !dialog.isShowing && active(activity) && activity.hasWindowFocus() }
                }
                marker("notification_inspection_end")
            }
            await("Main host focus did not settle") { main { active(activity) && activity.hasWindowFocus() } }
        }

        private fun launchMain(advanced: Boolean = false): MainActivity {
            val before = main { created.size }
            marker(if (advanced) "advanced_launch_begin" else "main_launch_begin")
            main {
                val intent = if (advanced) {
                    Intent(context, SetupAdvancedSettingsActivity::class.java).setAction(Intent.ACTION_RUN)
                        .putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, NavigationRoute.SchemaList)
                } else {
                    Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            var found: MainActivity? = null
            await("A fresh owned host Activity was not created") {
                found = main { created.drop(before).filterIsInstance<MainActivity>().singleOrNull { !it.isDestroyed && !it.isFinishing } }
                found != null
            }
            return checkNotNull(found).also(::awaitMainReady)
        }

        private fun screenshot(activity: Activity, label: String) {
            val root = main { activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) }
            val original = RenderedScreenshotBarrier.capture(instrumentation, root) {
                check(!activity.isDestroyed && !activity.isFinishing && activity.hasWindowFocus())
            }
            try {
                val file = File(directory, "$label.png")
                file.outputStream().use { check(original.first.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                screenshots.put(binding(file).put("barrier", original.second).put("human_reviewed", false))
            } finally {
                original.first.recycle()
            }
            checkTime()
        }

        private fun window(activity: Activity, label: String, title: Int, up: Boolean) = main {
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(root))
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val margins = root.layoutParams as ViewGroup.MarginLayoutParams
            val bar = toolbar(activity)
            check(activity is PublicWindowPilotActivity)
            check(activity.actionBar == null && activity.window.hasFeature(Window.FEATURE_NO_TITLE))
            check(!WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars)
            check(bar.title.toString() == activity.getString(title))
            check((bar.navigationIcon != null) == up)
            if (up) check(bar.navigationContentDescription.toString() == activity.getString(R.string.public_window_navigate_up))
            check(margins.leftMargin == bars.left && margins.rightMargin == bars.right)
            val expectedBottom = if (activity is MainActivity) maxOf(bars.bottom, ime.bottom) else bars.bottom
            check(margins.bottomMargin == expectedBottom)
            val toolbarTop = if (activity is MainActivity) {
                activity.findViewById<View>(R.id.mainToolbar).paddingTop
            } else {
                (bar.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            }
            check(toolbarTop == bars.top)
            val expected = PublicWindowPilotConfiguration.wrap(context, AppPrefs.defaultInstance().advanced.uiMode.getValue())
            check(ContextCompat.getColor(activity, R.color.colorPrimary) == ContextCompat.getColor(expected, R.color.colorPrimary))
            val c = activity.resources.configuration
            windows.put(
                JSONObject().put("label", label).put("activity", activity.javaClass.name).put("activity_identity", System.identityHashCode(activity)).put("task_id", activity.taskId)
                    .put("title", bar.title.toString()).put("subtitle", bar.subtitle?.toString()).put("up", up)
                    .put("font_scale", c.fontScale.toDouble()).put("locales", ConfigurationCompat.getLocales(c).toLanguageTags())
                    .put("density_dpi", c.densityDpi).put("orientation", c.orientation).put("ui_mode", c.uiMode)
                    .put("width_dp", c.screenWidthDp).put("height_dp", c.screenHeightDp).put("smallest_width_dp", c.smallestScreenWidthDp)
                    .put("root_left_margin", margins.leftMargin).put("root_right_margin", margins.rightMargin)
                    .put("root_bottom_margin", margins.bottomMargin).put("toolbar_top", toolbarTop)
                    .put("system_bars", JSONArray(listOf(bars.left, bars.top, bars.right, bars.bottom)))
                    .put("ime_bottom", ime.bottom).put("window_identity", System.identityHashCode(activity.window)),
            )
        }

        private fun verifyMainMenu(activity: MainActivity, visible: Boolean) = main {
            val bar = toolbar(activity)
            val ids = (0 until bar.menu.size()).map { bar.menu.getItem(it).itemId }
            val expected = listOf(R.id.action_deploy, R.id.action_test_input, R.id.action_developer, R.id.action_about, R.id.action_edit, R.id.action_delete)
            check(ids.size == 6 && ids.toSet() == expected.toSet() && ids.distinct().size == ids.size)
            expected.take(4).forEach { check(bar.menu.findItem(it).isVisible == visible) }
            check(!bar.menu.findItem(R.id.action_edit).isVisible && !bar.menu.findItem(R.id.action_delete).isVisible)
        }

        private fun mainNavigation(activity: MainActivity) {
            await("Main destination was not ready") { main { route(activity) == NavigationRoute.Main.path } }
            verifyMainMenu(activity, true)
            main { check(toolbar(activity).subtitle.toString() == activity.getString(R.string.trime_app_slogan)) }
            val titleClasses = main {
                descendants(activity.window.decorView).filter { it.id == android.R.id.title && it.isShown }.toList().also {
                    check(it.isNotEmpty() && it.all { view -> view is AppCompatTextView })
                }.map { it.javaClass.name }
            }
            report.put("main_actual_preference_title_classes", JSONArray(titleClasses))
            window(activity, "main_root", R.string.trime_app_name, true)
            screenshot(activity, "main-root")
            for ((id, destination, title) in listOf(
                Triple(R.id.action_developer, NavigationRoute.Developer, R.string.developer),
                Triple(R.id.action_about, NavigationRoute.About, R.string.about),
            )) {
                clickMenu(activity, id)
                await("Actual overflow action did not navigate to ${destination.path}") { main { route(activity) == destination.path } }
                main { check(toolbar(activity).title.toString() == activity.getString(title)) }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                await("Natural Back did not restore the Main route") { main { route(activity) == NavigationRoute.Main.path && activity.hasWindowFocus() } }
                verifyMainMenu(activity, true)
            }
            passed("real Main overflow actions navigate and Back restores title/menu without duplicate items")
        }

        private fun panelBack(activity: MainActivity) {
            clickMenu(activity, R.id.action_test_input)
            await("Actual test-input menu touch did not show its panel") { main { activity.findViewById<View>(R.id.test_input_panel).isShown } }
            var presses = 0
            while (main { activity.findViewById<View>(R.id.test_input_panel).isShown } && presses < 2) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                presses++
                await("Back did not hide the IME or panel") {
                    main {
                        val panel = activity.findViewById<View>(R.id.test_input_panel)
                        !panel.isShown || ViewCompat.getRootWindowInsets(panel)?.isVisible(WindowInsetsCompat.Type.ime()) == false
                    }
                }
            }
            await("At most two natural Back presses did not hide the input panel") { main { !activity.findViewById<View>(R.id.test_input_panel).isShown } }
            main { check(active(activity) && route(activity) == NavigationRoute.Main.path) }
            report.put("panel_back_presses", presses)
            passed("real TestInputPanel show and natural Back preserve the current destination")
        }

        private fun warmRouteAndRecreate(activity: MainActivity) {
            val before = main { created.size }
            main {
                context.startActivity(
                    Intent(context, MainActivity::class.java).setAction(Intent.ACTION_RUN)
                        .putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, NavigationRoute.Profile).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            await("Warm ACTION_RUN did not consume its one-shot route") {
                main { route(activity) == NavigationRoute.Profile.path && activity.intent.action == null && !activity.intent.hasExtra(MainActivity.EXTRA_SETTINGS_ROUTE) }
            }
            main { check(created.size == before) }
            val task = main { activity.taskId }
            main { activity.recreate() }
            var replacement: MainActivity? = null
            await("Real recreation did not create a new Main instance") {
                replacement = main { created.drop(before).filterIsInstance<MainActivity>().singleOrNull { it.javaClass == MainActivity::class.java && !it.isDestroyed && !it.isFinishing } }
                replacement != null
            }
            val restored = checkNotNull(replacement)
            awaitMainReady(restored)
            await("Saved Profile destination was overwritten during recreation") { main { activity.isDestroyed && route(restored) == NavigationRoute.Profile.path } }
            main { check(restored.taskId == task && restored.intent.action == null && !restored.intent.hasExtra(MainActivity.EXTRA_SETTINGS_ROUTE)) }
            verifyMainMenu(restored, false)
            window(restored, "main_restored_profile", R.string.profile, true)
            screenshot(restored, "main-restored-profile")
            passed("warm ACTION_RUN is consumed once and actual Activity recreation preserves Profile in the same task")
        }

        private fun mainMode() {
            val activity = launchMain()
            mainNavigation(activity)
            panelBack(activity)
            warmRouteAndRecreate(activity)
            check(checks.length() == 3)
        }

        private fun advancedMode() {
            val activity = launchMain(advanced = true)
            await("Actual Advanced host did not open the real schema page") { main { activity is SetupAdvancedSettingsActivity && route(activity) == NavigationRoute.SchemaList.path } }
            main { check(activity.intent.action == null && !activity.intent.hasExtra(MainActivity.EXTRA_SETTINGS_ROUTE)) }
            window(activity, "advanced_schemata", R.string.schemata, true)
            screenshot(activity, "advanced-schemata")
            report.put("wizard_stack_return_tested", false)
            passed("real standard Advanced host inherits the public Window and consumes the schema route")
            check(checks.length() == 1)
        }

        private fun launchLog(parent: MainActivity, kind: String): LogActivity {
            val before = main { created.size }
            marker("log_${kind}_launch_begin")
            main {
                val intent = Intent(parent, LogActivity::class.java)
                if (kind == "deploy") intent.putExtra(LogActivity.FROM_DEPLOY, true).putExtra(LogActivity.DEPLOY_FAILURE_TRACE, "__public_host_${token}_deploy__")
                if (kind == "crash") intent.putExtra(LogActivity.FROM_CRASH, true).putExtra(LogActivity.CRASH_STACK_TRACE, "__public_host_${token}_crash__")
                parent.startActivity(intent)
            }
            var found: LogActivity? = null
            await("Fresh $kind LogActivity was not created") {
                found = main { created.drop(before).filterIsInstance<LogActivity>().singleOrNull { !it.isDestroyed && !it.isFinishing } }
                found?.let { main { active(it) && it.window.decorView.isShown } } == true
            }
            return checkNotNull(found)
        }

        private fun logButtons(activity: LogActivity, kind: String) = main {
            for (id in intArrayOf(R.id.clearButton, R.id.copyButton, R.id.exportButton, R.id.jumpToBottomButton)) {
                check(activity.findViewById<Button>(id) is AppCompatButton)
            }
            check(activity.findViewById<View>(R.id.clearButton).visibility == if (kind == "normal") View.VISIBLE else View.GONE)
            check(activity.findViewById<View>(R.id.copyButton).visibility == if (kind == "deploy") View.VISIBLE else View.GONE)
            check(activity.findViewById<View>(R.id.exportButton).isShown)
            val retry = toolbar(activity).menu.findItem(R.id.action_retry_deploy)
            check(if (kind == "deploy") retry != null && retry.isVisible && retry.isEnabled else retry == null)
        }

        private fun logMode() {
            val parent = launchMain()
            for (kind in listOf("normal", "deploy", "crash")) {
                val log = launchLog(parent, kind)
                if (kind == "crash") {
                    awaitVisibleText(log.getString(R.string.app_crash))
                }
                // A crash notice is an actual product AlertDialog, but it is not stored publicly.
                // Accessible text is only reached-UI evidence, never a fabricated Dialog.javaClass.
                if (kind == "crash") {
                    clickVisibleText(log.getString(android.R.string.ok))
                    report.put("crash_notice_accessibility_activated", true).put("crash_notice_dialog_class_observed", false)
                }
                await("Log window did not regain actual focus") { main { active(log) && log.hasWindowFocus() } }
                logButtons(log, kind)
                val title = when (kind) {
                    "normal" -> R.string.real_time_logs
                    "deploy" -> R.string.deploy_failure
                    else -> R.string.crash_logs
                }
                window(log, "log_$kind", title, kind == "normal")
                screenshot(log, "log-$kind")
                if (kind == "normal") {
                    val count = main { created.count { it is MainActivity } }
                    val nav = main { descendants(toolbar(log)).single { it.contentDescription?.toString() == log.getString(R.string.public_window_navigate_up) && it.isClickable } }
                    touch(nav)
                    await("Actual normal Log Up did not reuse its manifest Main parent") { main { log.isDestroyed && active(parent) && parent.hasWindowFocus() } }
                    main { check(created.count { it is MainActivity } == count) }
                } else {
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    await("Natural Back did not close $kind Log and restore its parent") { main { log.isDestroyed && active(parent) && parent.hasWindowFocus() } }
                }
                marker("log_${kind}_terminal")
                passed("real $kind Log title/button/widget/retry state and ${if (kind == "normal") "manifest Up" else "Back"} behavior")
            }
            check(checks.length() == 3)
        }

        // These paths own no Dialog instance. Observe only the real accessibility
        // window and actual screen pixels; never claim a platform Dialog/View class.
        private fun accessibleState(root: AccessibilityNodeInfo, text: String): JSONObject? {
            if (root.packageName?.toString() != context.packageName) return null
            val found = root.findAccessibilityNodeInfosByText(text)
            return try {
                val nodes = found.filter { it.text?.toString() == text && it.isVisibleToUser && it.isEnabled }
                check(nodes.size <= 1) { "Ambiguous actual dialog text: $text" }
                nodes.singleOrNull()?.let { node ->
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    JSONObject().put("text", text).put("accessibility_class_name", node.className?.toString())
                        .put("window_id", node.windowId).put("bounds", bounds.flattenToString())
                        .put("checkable", node.isCheckable).put("checked", node.isCheckedForTest())
                        .put("instance_class_observed", false)
                }
            } finally {
                found.forEach(::recycle)
            }
        }

        private fun accessibleState(text: String): JSONObject? {
            checkTime()
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
            return try {
                accessibleState(root, text)
            } finally {
                recycle(root)
            }
        }

        private fun activeTextStates(
            texts: List<String>,
            expectedWindowId: Int? = null,
            rejectedWindowIds: Set<Int> = emptySet(),
        ): Pair<Int, List<JSONObject>>? {
            checkTime()
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
            return try {
                val windowId = root.windowId
                if (root.packageName?.toString() != context.packageName ||
                    (expectedWindowId != null && windowId != expectedWindowId) || windowId in rejectedWindowIds
                ) {
                    return null
                }
                val states = mutableListOf<JSONObject>()
                for (text in texts) {
                    val state = accessibleState(root, text) ?: return null
                    if (state.getInt("window_id") != windowId) return null
                    states += state
                }
                windowId to states
            } finally {
                recycle(root)
            }
        }

        private fun clickVisibleTextInWindow(text: String, companionTexts: List<String>, expectedWindowId: Int): Int {
            val required = (listOf(text) + companionTexts).distinct()
            await("Visible action '$text' did not remain in its evidenced dialog window") {
                activeTextStates(required, expectedWindowId = expectedWindowId) != null
            }
            val root = checkNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            val actionWindowId = try {
                check(root.packageName?.toString() == context.packageName && root.windowId == expectedWindowId)
                companionTexts.forEach { companion ->
                    check(checkNotNull(accessibleState(root, companion)).getInt("window_id") == expectedWindowId)
                }
                val matches = root.findAccessibilityNodeInfosByText(text)
                try {
                    var node = matches.single {
                        it.text?.toString() == text && it.isVisibleToUser && it.isEnabled && it.windowId == expectedWindowId
                    }
                    val parents = mutableListOf<AccessibilityNodeInfo>()
                    try {
                        repeat(4) {
                            if (!node.isClickable) {
                                node = checkNotNull(node.parent)
                                parents += node
                                check(node.windowId == expectedWindowId)
                            }
                        }
                        check(node.isClickable && node.isVisibleToUser && node.isEnabled)
                        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Actual visible dialog action was rejected" }
                    } finally {
                        parents.forEach(::recycle)
                    }
                } finally {
                    matches.forEach(::recycle)
                }
                root.windowId
            } finally {
                recycle(root)
            }
            checkTime()
            return actionWindowId
        }

        private fun dialogScreen(
            label: String,
            texts: List<String>,
            rejectedWindowIds: Set<Int> = emptySet(),
        ): JSONObject {
            var evidence: Pair<Int, List<JSONObject>>? = null
            await("Actual dialog texts were not unique in one new active window: ${texts.joinToString()}") {
                activeTextStates(texts, rejectedWindowIds = rejectedWindowIds).also { evidence = it } != null
            }
            val (windowId, states) = checkNotNull(evidence)
            val observed = JSONObject().put("label", label).put("dialog_instance_class_observed", false)
                .put("window_id", windowId).put("visible_text", JSONArray(states))
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val file = File(directory, "$label.png")
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val image = binding(file).put("human_reviewed", false)
                    .put("capture_kind", "actual UiAutomation screen; no owned View draw barrier available")
                screenshots.put(image)
                observed.put("screenshot", image)
            } finally {
                bitmap.recycle()
            }
            checkTime()
            return observed
        }

        private fun currentPreferences(activity: MainActivity): PreferenceFragmentCompat = main {
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            host.childFragmentManager.primaryNavigationFragment as PreferenceFragmentCompat
        }

        private fun preferenceTitles(group: PreferenceGroup): Sequence<Preference> = sequence {
            for (index in 0 until group.preferenceCount) {
                val preference = group.getPreference(index)
                yield(preference)
                if (preference is PreferenceGroup) yieldAll(preferenceTitles(preference))
            }
        }

        private fun clickPreference(activity: MainActivity, title: String, key: String? = null) {
            val owner = currentPreferences(activity)
            await("Actual preference row did not become enabled: $title") {
                main {
                    owner.preferenceScreen?.let { screen ->
                        preferenceTitles(screen).count { it.title?.toString() == title && (key == null || it.key == key) && it.isEnabled } == 1
                    } == true
                }
            }
            main {
                val matches = preferenceTitles(checkNotNull(owner.preferenceScreen)).filter {
                    it.title?.toString() == title && (key == null || it.key == key)
                }.toList()
                check(matches.size == 1 && matches.single().isEnabled)
                owner.scrollToPreference(matches.single())
            }
            clickVisibleText(title) // Existing genuine accessibility activation, not performClick().
        }

        private fun monitorExternalIntents() {
            check(externalIntentMonitors.isEmpty())
            val filters = listOf(
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addDataScheme("http")
                    addDataScheme("https")
                },
                IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
                IntentFilter(Intent.ACTION_GET_CONTENT).apply { addDataType("*/*") },
            )
            filters.forEach { filter ->
                // An Intent's categories must all occur in its matching filter.
                // Additional filter categories do not require the Intent to carry them.
                for (category in listOf(Intent.CATEGORY_DEFAULT, Intent.CATEGORY_OPENABLE, Intent.CATEGORY_BROWSABLE)) {
                    filter.addCategory(category)
                }
                externalIntentMonitors += instrumentation.addMonitor(filter, null, false)
            }
            report.put("external_intent_monitor_scope", "Nonblocking public Instrumentation request monitors only: http/https ACTION_VIEW, no-data OPEN_DOCUMENT_TREE and MIME GET_CONTENT; not global Activity inventory")
        }

        private fun noExternalIntentRequests() {
            check(externalIntentMonitors.size == 3 && externalIntentMonitors.all { it.hits == 0 }) {
                "A cancelled prompt unexpectedly requested a browser or picker"
            }
            report.put("external_intent_monitor_hits", JSONArray(externalIntentMonitors.map { it.hits }))
        }

        private fun profileFlag(fragment: ProfileSettingsFragment, name: String): Boolean = main {
            ProfileSettingsFragment::class.java.getDeclaredField(name).run {
                isAccessible = true
                getBoolean(fragment)
            }
        }

        private fun profilePromptCancel(activity: MainActivity, fragment: ProfileSettingsFragment) {
            val profile = AppPrefs.defaultInstance().profile
            check(profile.dataStorageMode.getValue() == DataStorageMode.APP_STORAGE && !UserDataManager.hasExternalAccess()) {
                "UNSATISFIED: requires actual APP_STORAGE without external access; never rewrite an existing profile"
            }
            val before = main { preferenceSnapshot() }
            val text = activity.getString(R.string.external_sync_select_folder_message)
            marker("profile_storage_list_begin")
            clickPreference(activity, activity.getString(R.string.data_storage_mode), AppPrefs.Profile.DATA_STORAGE_MODE)
            val localLabel = activity.getString(DataStorageMode.APP_STORAGE.stringRes)
            val externalLabel = activity.getString(DataStorageMode.EXTERNAL_SYNC.stringRes)
            awaitVisibleText(externalLabel)
            val localChoice = checkNotNull(accessibleState(localLabel))
            val externalChoice = checkNotNull(accessibleState(externalLabel))
            check(localChoice.getBoolean("checkable") && localChoice.getBoolean("checked"))
            check(externalChoice.getBoolean("checkable") && !externalChoice.getBoolean("checked"))
            val storageListWindowId = localChoice.getInt("window_id")
            check(externalChoice.getInt("window_id") == storageListWindowId)
            report.put("storage_list_initial_checked", localChoice).put("storage_list_initial_unchecked", externalChoice)
                .put("storage_list_action_window_id", clickVisibleTextInWindow(externalLabel, listOf(localLabel), storageListWindowId))
            marker("profile_external_prompt_await")
            val cancel = activity.getString(android.R.string.cancel)
            val promptWindow = dialogScreen(
                "profile-external-prompt",
                listOf(text, cancel),
                rejectedWindowIds = setOf(storageListWindowId),
            ).put("previous_window_id", storageListWindowId)
            report.put("profile_prompt_window", promptWindow)
            marker("profile_external_prompt_ready")
            promptWindow.put(
                "cancel_action_window_id",
                clickVisibleTextInWindow(cancel, listOf(text), promptWindow.getInt("window_id")),
            )
            report.put("profile_prompt_window", promptWindow)
            await("Real external-folder Cancel did not restore unchanged Profile") {
                main { active(activity) && activity.hasWindowFocus() && route(activity) == NavigationRoute.Profile.path } &&
                    !profileFlag(fragment, "pendingExternalSyncSetup") && !profileFlag(fragment, "storageBusy") &&
                    main { fragment.findPreference<Preference>(AppPrefs.Profile.DATA_STORAGE_MODE)?.isEnabled == true }
            }
            check(profile.dataStorageMode.getValue() == DataStorageMode.APP_STORAGE)
            main { check(fragment.findPreference<ListPreference>(AppPrefs.Profile.DATA_STORAGE_MODE)?.value == DataStorageMode.APP_STORAGE.name) }
            check(main { preferenceSnapshot() } == before)
            noExternalIntentRequests()
            screenshot(activity, "profile-after-external-cancel")
            marker("profile_external_prompt_cancelled")
            passed("real external storage prompt negative retains APP_STORAGE raw preferences and starts no picker")
        }

        private fun selectedAssetSnapshot(name: String, label: String): JSONObject {
            check(name.isNotEmpty() && File(name).name == name && name !in setOf(".", ".."))
            val base = DataManager.sharedDataDir
            val selected = File(base, name)
            val entries = JSONArray()
            var total = 0L
            var count = 0
            if (selected.exists()) {
                selected.walkTopDown().onFail { _, error -> throw error }.forEach { file ->
                    checkTime()
                    val stat = Os.lstat(file.absolutePath)
                    check(
                        !OsConstants.S_ISLNK(stat.st_mode) &&
                            (file.canonicalPath == base.canonicalPath || file.canonicalPath.startsWith(base.canonicalPath + File.separator)),
                    )
                    check(++count <= 1000)
                    val row = JSONObject().put("path", file.relativeTo(base).path)
                    when {
                        OsConstants.S_ISDIR(stat.st_mode) -> row.put("type", "directory")

                        OsConstants.S_ISREG(stat.st_mode) -> {
                            total += stat.st_size
                            check(total <= 64L * 1024 * 1024)
                            val digest = MessageDigest.getInstance("SHA-256")
                            file.inputStream().use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val size = input.read(buffer)
                                    if (size < 0) break
                                    digest.update(buffer, 0, size)
                                    checkTime()
                                }
                            }
                            row.put("type", "file").put("bytes", stat.st_size)
                                .put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
                        }

                        else -> error("Unsupported selected asset entry")
                    }
                    entries.put(row)
                }
            }
            val file = File(directory, "$label.json")
            file.writeText(entries.toString(2) + "\n")
            return JSONObject().put("selected_asset", name).put("exists", selected.exists())
                .put("entries", entries).put("artifact", binding(file))
                .put("scope", "Selected reset destination bytes only; host must independently guard all config/cache/dictionary bytes")
        }

        private fun configurationBytes(label: String): JSONObject {
            val rows = JSONArray()
            var total = 0L
            var count = 0
            for ((name, base) in listOf("shared" to DataManager.sharedDataDir, "user" to DataManager.userDataDir)) {
                if (!base.isDirectory) continue
                for (file in base.walkTopDown().onFail { _, error -> throw error }.filter { it.isFile && it.extension in setOf("yaml", "yml") }.sortedBy { it.path }) {
                    checkTime()
                    check(++count <= 2000 && !OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode))
                    check(file.canonicalPath.startsWith(base.canonicalPath + File.separator))
                    val length = file.length()
                    check(length >= 0 && length <= 64L * 1024 * 1024 - total)
                    val bytes = file.readBytes()
                    check(bytes.size.toLong() == length)
                    total += bytes.size
                    rows.put(
                        JSONObject().put("root", name).put("path", file.relativeTo(base).path).put("bytes", bytes.size)
                            .put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }),
                    )
                }
            }
            check(count > 0) { "No real configuration bytes available to protect" }
            val file = File(directory, "$label.json").apply { writeText(rows.toString(2) + "\n") }
            return JSONObject().put("rows", rows).put("artifact", binding(file))
                .put("scope", "Actual shared/user YAML and compiled YAML bytes only; not all userdb/log/cache files")
        }

        private fun resetSelectedCancel(activity: MainActivity) {
            val items = checkNotNull(context.assets.list("shared")).toList()
            check(items.isNotEmpty())
            marker("profile_reset_begin")
            clickPreference(activity, activity.getString(R.string.reset))
            awaitVisibleText(activity.getString(android.R.string.cancel))
            var selected: String? = null
            await("No genuine visible unchecked reset asset was available") {
                selected = items.firstOrNull { name -> accessibleState(name)?.let { it.getBoolean("checkable") && !it.getBoolean("checked") } == true }
                selected != null
            }
            val name = checkNotNull(selected)
            val before = selectedAssetSnapshot(name, "reset-selected-before")
            report.put("reset_selected_before", before)
            val cancel = activity.getString(android.R.string.cancel)
            val resetWindowBefore = dialogScreen("reset-selected-before", listOf(name, cancel))
            val resetWindowId = resetWindowBefore.getInt("window_id")
            report.put("reset_window_before", resetWindowBefore)
            resetWindowBefore.put("selection_action_window_id", clickVisibleTextInWindow(name, listOf(cancel), resetWindowId))
            report.put("reset_window_before", resetWindowBefore)
            await("Actual reset checkbox did not change from unchecked to checked") { accessibleState(name)?.getBoolean("checked") == true }
            val resetWindowChecked = dialogScreen("reset-selected-checked", listOf(name, cancel))
            check(resetWindowChecked.getInt("window_id") == resetWindowId)
            report.put("reset_window_checked", resetWindowChecked)
            resetWindowChecked.put("cancel_action_window_id", clickVisibleTextInWindow(cancel, listOf(name), resetWindowId))
            report.put("reset_window_checked", resetWindowChecked)
            await("Reset Cancel did not restore the actual Profile window") { main { active(activity) && activity.hasWindowFocus() && route(activity) == NavigationRoute.Profile.path } }
            val after = selectedAssetSnapshot(name, "reset-selected-after")
            check(before.getBoolean("exists") == after.getBoolean("exists") && before.getJSONArray("entries").toString() == after.getJSONArray("entries").toString())
            report.put("reset_selected_after", after).put("reset_positive_never_activated", true)
                .put("reset_no_deployment_host_review_required", true)
            noExternalIntentRequests()
            marker("profile_reset_cancelled")
            passed("real reset asset checkbox changes locally and Cancel preserves selected resource bytes")
        }

        private fun sharedProfileMode() {
            val activity = launchMain()
            monitorExternalIntents()
            clickPreference(activity, activity.getString(R.string.profile))
            await("Actual Profile route did not open") { main { route(activity) == NavigationRoute.Profile.path && activity.hasWindowFocus() } }
            val fragment = currentPreferences(activity)
            check(fragment is ProfileSettingsFragment)
            // This is the actual replacement process after READY and before either profile action.
            val checksumRoot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext().dataDir
            } else {
                File(context.applicationInfo.dataDir)
            }
            val protectedRoots = listOf(
                "shared" to DataManager.sharedDataDir,
                "rime" to DataManager.userDataDir,
                "userdict-backups" to File(context.filesDir, "userdict-backups"),
                "cache" to context.cacheDir,
                "sync-index" to File(context.filesDir, "rime_sync_index.json"),
                "asset-checksums" to File(checksumRoot, "checksums.json"),
                "direct-boot-checksums" to File(checksumRoot, "checksums.direct-boot.json"),
            )
            check(protectedRoots.take(2).all { it.second.isDirectory })
            check(RimeDaemon.engineState.value == RimeLifecycle.State.READY)
            marker("profile_full_data_before_begin")
            val completeBefore = PublicProfileDataSnapshot.capture(protectedRoots, File(directory, "profile-data-before"), ::checkTime)
            report.put("profile_full_data_before", completeBefore)
            check(RimeDaemon.engineState.value == RimeLifecycle.State.READY)
            marker("profile_full_data_before_end")
            val before = configurationBytes("profile-config-before")
            report.put("profile_configuration_before", before)
            profilePromptCancel(activity, fragment)
            resetSelectedCancel(activity)
            val after = configurationBytes("profile-config-after")
            report.put("profile_configuration_after", after)
            check(before.getJSONArray("rows").toString() == after.getJSONArray("rows").toString()) { "Prompt/reset cancellation changed original configuration bytes" }
            check(RimeDaemon.engineState.value == RimeLifecycle.State.READY)
            marker("profile_full_data_after_begin")
            val completeAfter = PublicProfileDataSnapshot.capture(protectedRoots, File(directory, "profile-data-after"), ::checkTime)
            report.put("profile_full_data_after", completeAfter)
            check(RimeDaemon.engineState.value == RimeLifecycle.State.READY)
            check(File(directory, "profile-data-before/inventory.json").readBytes().contentEquals(File(directory, "profile-data-after/inventory.json").readBytes())) {
                "Prompt/reset cancellation changed protected profile data; original before/after bytes retained"
            }
            report.put("profile_full_data_unchanged", true)
                .put("profile_full_data_scope", "Entire shared and rime trees including hidden/compiled/userdb/WAL/log files; entire app cache; userdict-backups, sync index and both DataManager checksum files including absence. No per-file exclusions; native copies require independent host verification.")
            marker("profile_full_data_after_end")
            check(checks.length() == 2)
        }

        private fun licenseCancelMode() {
            val activity = launchMain()
            monitorExternalIntents()
            val raw = context.resources.openRawResource(R.raw.aboutlibraries).use { it.readBytes() }
            val original = File(directory, "installed-aboutlibraries.json").apply { writeBytes(raw) }
            val libraries = Libs.Builder().withJson(raw.toString(Charsets.UTF_8)).build().libraries
            val inventory = JSONArray()
            libraries.forEach { library ->
                inventory.put(
                    JSONObject().put("unique_id", library.uniqueId)
                        .put("artifact_version", library.artifactVersion).put(
                            "licenses",
                            JSONArray(
                                library.licenses.map {
                                    JSONObject().put("name", it.spdxId ?: it.name).put("url", it.url ?: JSONObject.NULL)
                                },
                            ),
                        ),
                )
            }
            report.put("installed_license_metadata", binding(original)).put("installed_license_inventory", inventory)
            val candidate = libraries.sortedBy { if (it.tag == "native") it.uniqueId.uppercase() else it.uniqueId.lowercase() }
                .firstOrNull { it.licenses.size > 1 }
            check(candidate != null) { "UNSATISFIED: installed metadata contains no genuine multi-license library; no substitute selector" }
            check(
                candidate.licenses.all { license ->
                    license.url.isNullOrBlank() ||
                        license.url?.let { android.net.Uri.parse(it).scheme in setOf("http", "https") } == true
                },
            ) { "UNSATISFIED: installed candidate URL scheme is outside the explicit nonblocking browser monitor" }
            val title = "${candidate.uniqueId}:${candidate.artifactVersion}"
            val names = candidate.licenses.map { it.spdxId ?: it.name }
            clickMenu(activity, R.id.action_about)
            await("Actual About route did not open") { main { route(activity) == NavigationRoute.About.path && activity.hasWindowFocus() } }
            clickPreference(activity, activity.getString(R.string.open_source_licenses))
            await("Actual License route and metadata preferences did not load") {
                main { route(activity) == NavigationRoute.License.path && activity.hasWindowFocus() } &&
                    currentPreferences(activity) is LicenseFragment
            }
            marker("license_selector_begin")
            clickPreference(activity, title)
            val cancel = activity.getString(android.R.string.cancel)
            report.put("license_selector_window", dialogScreen("license-selector", listOf(candidate.uniqueId, cancel) + names))
                .put("license_selector_expected_labels", JSONArray(names)).put("license_selector_unique_id", candidate.uniqueId)
            marker("license_selector_ready")
            clickVisibleText(cancel) // Producer deliberately uses a positive-button Cancel.
            await("License selector Cancel did not restore its real product route") {
                main { active(activity) && activity.hasWindowFocus() && route(activity) == NavigationRoute.License.path }
            }
            noExternalIntentRequests()
            screenshot(activity, "license-after-cancel")
            marker("license_selector_cancelled")
            passed("real installed multi-license library selector Cancel retains License and starts no URL")
            check(checks.length() == 1)
        }

        private fun userDictionaryMode() {
            val activity = launchMain()
            val progress = JSONObject()
            report.put("user_dictionary_dialogs", progress)
            PublicUserDictionaryDialogStage.verify(
                instrumentation,
                activity,
                token,
                workDeadline,
                directory,
                progress,
                ::passed,
            )
            check(checks.length() == 4)
            report.put("dictionary_dialog_validation_cancel_tested", true)
        }

        private fun userDictionaryImportErrorMode() {
            val activity = launchMain()
            val progress = JSONObject()
            report.put("user_dictionary_import_error", progress)
            PublicUserDictionaryDialogStage.verifyImportFailure(
                instrumentation,
                activity,
                token,
                workDeadline,
                directory,
                progress,
                ::passed,
            ) { message ->
                awaitVisibleText(activity.getString(R.string.import_error))
                awaitVisibleText(message)
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = File(directory, "userdict-shared-import-error.png")
                try {
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
                check(file.length() in 1L..8_388_608L)
                val screenshot = binding(file)
                clickVisibleText(activity.getString(android.R.string.ok))
                JSONObject().put("title", activity.getString(R.string.import_error)).put("message", message)
                    .put("actual_public_error_OK_activated", true).put("dialog_instance_class_observed", false)
                    .put("screenshot", screenshot).put("atomic_pixels_and_text", false).put("human_reviewed", false)
            }
            check(checks.length() == 2)
        }

        private fun asynchronousOwnerBoundaryMode() {
            val activity = launchMain()
            val progress = JSONObject()
            report.put("async_owner_boundary", progress)
            marker("async_owner_boundary_begin")
            PublicDialogOwnerBoundaryStage.verify(
                instrumentation,
                activity,
                token,
                workDeadline,
                progress,
                ::passed,
            )
            await("The real finishing boundary owner did not reach onDestroy") {
                main { activity.isDestroyed }
            }
            marker("async_owner_boundary_end")
            check(checks.length() == 3)
        }

        private fun preflight() {
            check(disposable && Build.HARDWARE in setOf("ranchu", "goldfish") && Build.VERSION.SDK_INT in setOf(21, 35))
            check(mode in setOf("hosts_main", "hosts_advanced", "hosts_log", "dialogs_userdict_cancel", "dialogs_profile_cancel", "dialogs_license_cancel", "dialogs_owner_boundary", "dialogs_userdict_import_error"))
            check(File(context.getExternalFilesDir(null), "runtime-audit-dedicated").let { it.isFile && it.length() == 0L })
            check(!directory.exists() && directory.mkdirs())
            PublicImeWindowPreflight.synchronize(
                instrumentation,
                workDeadline,
                totalDeadline,
                JSONObject().also { report.put("ime_preflight_window", it) },
                ::sessions,
            )
            await("The selected IME did not bind before host windows") {
                main {
                    val observed = sessions()
                    val state = RimeDaemon.engineState.value
                    report.put("sessions_preflight_observed", JSONArray(observed.sorted()))
                        .put("engine_preflight_observed", state.name)
                    observed.size == 1 &&
                        observed.single().startsWith("com.osfans.trime.ime.core.TrimeInputMethodService#") &&
                        state == RimeLifecycle.State.READY
                }
            }
            val initialOwners = main { sessions() }
            check(initialOwners.none { it == MainViewModel::class.java.name }) { "Existing Main owner must not be reused" }
            check(initialOwners.isNotEmpty() && initialOwners.all { it.startsWith("com.osfans.trime.ime.core.TrimeInputMethodService#") }) {
                "The established driver must bind only the selected candidate IME before host work"
            }
            originalSessions = initialOwners
            await("The existing selected IME did not become READY") { RimeDaemon.engineState.value == RimeLifecycle.State.READY }
            check(runBlocking { withTimeout(30_000) { !SetupActivity.shouldShowUp() } }) { "Completed disposable setup is required" }
            originalIme = ime()
            check(checkNotNull(originalIme).first && checkNotNull(originalIme).second?.startsWith("${context.packageName}/") == true)
            originalPreferences = main { preferenceSnapshot() }
            originalGrants = grants()
            report.put("default_ime_before", JSONObject().put("present", checkNotNull(originalIme).first).put("value", checkNotNull(originalIme).second ?: JSONObject.NULL))
                .put("grants_before", JSONArray(checkNotNull(originalGrants)))
                .put("sessions_before", JSONArray(checkNotNull(originalSessions).sorted()))
                .put("preferences_before", snapshotPreferences("preferences-before", checkNotNull(originalPreferences)))
            stateCaptured = true
            main {
                application.registerActivityLifecycleCallbacks(callbacks)
                registered = true
            }
            marker("phase_begin")
        }

        private fun cleanup() {
            cleaning = true
            cleanupDeadline = minOf(totalDeadline, SystemClock.uptimeMillis() + 60_000L)
            fun clean(block: () -> Unit) {
                try {
                    checkTime()
                    block()
                    checkTime()
                } catch (error: Throwable) {
                    cleanupErrors.put(error.stackTraceToString())
                    val first = failure
                    if (first == null) {
                        failure = error
                    } else if (first !== error) {
                        first.addSuppressed(error)
                    }
                }
            }
            clean { marker("cleanup_begin") }
            clean { main { created.asReversed().filter { !it.isFinishing && !it.isDestroyed }.forEach(Activity::finish) } }
            clean { await("Owned hosts did not reach real onDestroy", 30_000) { main { created.all { it.isDestroyed } } } }
            originalSessions?.let { initial ->
                clean {
                    await("Owned Main session did not naturally release to the original selected IME owners", 30_000) {
                        main { sessions() == initial } && RimeDaemon.engineState.value == RimeLifecycle.State.READY
                    }
                    report.put("sessions_after", JSONArray(main { sessions().sorted() }))
                }
            }
            if (stateCaptured) {
                clean {
                    val after = ime()
                    report.put("default_ime_after", JSONObject().put("present", after.first).put("value", after.second ?: JSONObject.NULL))
                    check(after == originalIme) { "Default IME changed" }
                }
                clean {
                    val after = grants()
                    report.put("grants_after", JSONArray(after))
                    check(after == originalGrants) { "Persisted URI grants changed" }
                }
                clean {
                    val after = main { preferenceSnapshot() }
                    report.put("preferences_after", snapshotPreferences("preferences-after", after))
                    check(after == originalPreferences) { "Host navigation changed persistent preferences" }
                }
            }
            if (registered) {
                clean {
                    main {
                        application.unregisterActivityLifecycleCallbacks(callbacks)
                        registered = false
                    }
                }
            }
            clean {
                main {
                    check(created.all { it.isDestroyed }) { "An owned Activity survived final cleanup" }
                    report.put(
                        "owned_activities",
                        JSONArray(
                            created.map { activity ->
                                JSONObject().put("class", activity.javaClass.name).put("identity", System.identityHashCode(activity))
                                    .put("destroyed", activity.isDestroyed)
                            },
                        ),
                    )
                    created.clear()
                    resumed.clear()
                }
            }
            if (externalIntentMonitors.isNotEmpty()) {
                clean {
                    val finalHits = externalIntentMonitors.map { it.hits }
                    report.put("external_intent_monitor_final_hits", JSONArray(finalHits))
                    check(externalIntentMonitors.size == 3 && finalHits.all { it == 0 }) {
                        "A delayed browser or picker request occurred before monitor removal"
                    }
                }
                clean {
                    val removed = externalIntentMonitors.toList()
                    removed.forEach(instrumentation::removeMonitor)
                    externalIntentMonitors.clear()
                    report.put("external_intent_monitors_removed", true)
                    val afterRemoval = removed.map { it.hits }
                    report.put("external_intent_monitor_hits_after_removal", JSONArray(afterRemoval))
                    check(afterRemoval.size == 3 && afterRemoval.all { it == 0 }) {
                        "A delayed browser or picker request occurred during monitor removal"
                    }
                }
            }
            clean { marker("cleanup_end") }
        }

        private fun terminal() {
            val terminalDeadline = minOf(totalDeadline, cleanupDeadline)
            if (SystemClock.uptimeMillis() > terminalDeadline) failure = failure ?: IllegalStateException("Public host exceeded cleanup/total native deadline")
            report.put("checks", checks).put("windows", windows).put("markers", markers).put("screenshots", screenshots)
                .put("cleanup_errors", cleanupErrors).put("cleanup_complete", cleanupErrors.length() == 0)
                .put("work_budget_ms", 240_000).put("cleanup_budget_ms", 60_000).put("total_budget_ms", 300_000)
                .put("elapsed_ms", SystemClock.uptimeMillis() - started).put("work_elapsed_ms", workElapsed)
                .put("terminal_deadline_respected", SystemClock.uptimeMillis() <= terminalDeadline)
                .put("passed", failure == null).put("automatic_reuse_allowed", failure == null && cleanupErrors.length() == 0)
                .put("warning_interval_host_reviewed", false)
                .put("cleanup_scope", "Owned Activity/session/default AppPrefs store/IME/grants; not all process-global initialized caches")
            failure?.let { report.put("error", it.stackTraceToString()) }
            val result = Bundle()
            try {
                val file = File(directory, "report.json")
                file.writeText(report.toString(2) + "\n")
                check(file.length() <= 1_048_576L) { "Native public host report exceeded1MiB; full original retained" }
                val row = binding(file)
                if (SystemClock.uptimeMillis() > terminalDeadline) error("Native report/hash completed after cleanup/total deadline")
                result.putString("public_host_report_path", row.getString("path"))
                result.putString("public_host_report_sha256", row.getString("sha256"))
                result.putLong("public_host_report_bytes", row.getLong("bytes"))
            } catch (error: Throwable) {
                failure = failure ?: error
                report.put("passed", false).put("automatic_reuse_allowed", false).put("error", checkNotNull(failure).stackTraceToString())
                    .put("terminal_deadline_respected", SystemClock.uptimeMillis() <= terminalDeadline)
                runCatching {
                    val file = File(directory, "report.json")
                    if (file.isFile) file.copyTo(File(directory, "report-before-terminal-failure.json"), overwrite = false)
                    file.writeText(report.toString(2) + "\n")
                    val row = binding(file)
                    result.putString("public_host_report_path", row.getString("path"))
                    result.putString("public_host_report_sha256", row.getString("sha256"))
                    result.putLong("public_host_report_bytes", row.getLong("bytes"))
                }
            }
            if (SystemClock.uptimeMillis() > terminalDeadline && failure == null) {
                failure = IllegalStateException("Native acceptance crossed cleanup/total deadline")
                report.put("passed", false).put("automatic_reuse_allowed", false).put("terminal_deadline_respected", false).put("error", checkNotNull(failure).stackTraceToString())
                runCatching {
                    val file = File(directory, "report.json")
                    file.copyTo(File(directory, "report-before-final-deadline.json"), overwrite = false)
                    file.writeText(report.toString(2) + "\n")
                    val row = binding(file)
                    result.putString("public_host_report_sha256", row.getString("sha256"))
                    result.putLong("public_host_report_bytes", row.getLong("bytes"))
                }
            }
            result.putBoolean("public_host_passed", failure == null)
            result.putBoolean("public_host_cleanup_complete", cleanupErrors.length() == 0)
            result.putInt("public_host_checks", checks.length())
            result.putString("public_host_run_id", token)
            result.putLong("public_host_accepted_elapsed_ms", SystemClock.uptimeMillis() - started)
            result.putLong("public_host_total_budget_ms", 300_000L)
            instrumentation.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }

        fun run() {
            try {
                preflight()
                when (mode) {
                    "hosts_main" -> mainMode()
                    "hosts_advanced" -> advancedMode()
                    "hosts_log" -> logMode()
                    "dialogs_userdict_cancel" -> userDictionaryMode()
                    "dialogs_profile_cancel" -> sharedProfileMode()
                    "dialogs_license_cancel" -> licenseCancelMode()
                    "dialogs_owner_boundary" -> asynchronousOwnerBoundaryMode()
                    "dialogs_userdict_import_error" -> userDictionaryImportErrorMode()
                }
                main { check(created.none { it is AppCompatActivity }) }
                marker("phase_end")
            } catch (error: Throwable) {
                failure = error
            } finally {
                workElapsed = SystemClock.uptimeMillis() - started
                cleanup()
                terminal()
            }
        }
    }
}
