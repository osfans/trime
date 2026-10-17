/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.navigation.fragment.NavHostFragment
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.ChineseVariant
import com.osfans.trime.data.prefs.InputLayout
import com.osfans.trime.data.prefs.InputSetupSelection
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.ui.setup.SetupAdvancedSettingsActivity
import com.osfans.trime.ui.setup.SetupFragment
import com.osfans.trime.ui.setup.SetupPage
import com.osfans.trime.ui.setup.SetupTrialEditText
import com.osfans.trime.util.InputMethodUtils
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.kodein.di.direct
import org.kodein.di.instance
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Disposable-emulator UI/state regression. Real keyboard touch acceptance is a separate test. */
object SetupClosedLoopProbe {
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    @SuppressLint("PrivateApi")
    private fun windowRoots(): List<View> {
        if (Build.VERSION.SDK_INT >= 29) return WindowInspector.getGlobalWindowViews()
        val type = Class.forName("android.view.WindowManagerGlobal")
        val instance = type.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        return (type.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>).toList()
    }

    /** Inspect only the actual active public accessibility window, including on API21. */
    private fun skipNoBounds(instrumentation: Instrumentation, activity: SetupActivity): Rect? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val owned = mutableListOf(root)
        try {
            if (root.packageName?.toString() != activity.packageName) return null
            val prompts = root.findAccessibilityNodeInfosByText(activity.getString(R.string.setup__skip_hint))
            owned.addAll(prompts)
            val matchingPrompts = prompts.filter {
                it.isVisibleToUser && it.text?.toString() == activity.getString(R.string.setup__skip_hint)
            }
            check(matchingPrompts.size <= 1) { "Actual Skip prompt is ambiguous" }
            if (matchingPrompts.isEmpty()) return null
            val buttons = root.findAccessibilityNodeInfosByText(activity.getString(R.string.setup__skip_hint_no))
            owned.addAll(buttons)
            val matchingButtons = buttons.filter {
                it.isVisibleToUser && it.isEnabled && it.isClickable && it.windowId == root.windowId &&
                    it.text?.toString() == activity.getString(R.string.setup__skip_hint_no)
            }
            check(matchingButtons.size <= 1) { "Actual Skip No button is ambiguous" }
            val button = matchingButtons.singleOrNull() ?: return null
            return Rect().also { bounds ->
                button.getBoundsInScreen(bounds)
                val window = Rect().also(root::getBoundsInScreen)
                check(!bounds.isEmpty && window.contains(bounds)) { "Skip No is not fully inside its real window" }
            }
        } finally {
            // API21-32 return these public nodes to the platform pool; API33+ no longer pools them.
            @Suppress("DEPRECATION")
            fun recycle(node: AccessibilityNodeInfo) {
                if (Build.VERSION.SDK_INT < 33) node.recycle()
            }
            owned.forEach(::recycle)
        }
    }

    /** Native unfinished input and the real bound candidate labels, without selecting a word. */
    private fun verifyAppearanceDuringComposition(
        instrumentation: Instrumentation,
        activity: SetupActivity,
        trial: SetupTrialEditText,
        session: RimeSession,
        deployments: AtomicInteger,
        passed: (String) -> Unit,
    ) {
        fun <T> main(block: () -> T): T {
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            return checkNotNull(outcome).getOrThrow()
        }
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 30_000
            while (!condition()) {
                check(SystemClock.uptimeMillis() < deadline) { message }
                SystemClock.sleep(50)
            }
        }
        fun input() = windowRoots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }

        main {
            check(
                activity.supportFragmentManager.fragments.filterIsInstance<SetupFragment>()
                    .first { it.setupPage == SetupPage.Try }.requireView()
                    .findViewById<Button>(R.id.action_button).performClick(),
            )
        }
        await("The real Open Trial button did not bind a visible Trime input view") {
            main {
                input()?.let { view ->
                    val service = view.di.direct.instance<TrimeInputMethodService>()
                    trial.hasFocus() && service.currentInputEditorInfo?.fieldId == trial.id &&
                        service.isCurrentEditor(service.editorToken) && service.currentInputConnection != null
                } == true
            }
        }
        val service = main { checkNotNull(input()).di.direct.instance<TrimeInputMethodService>() }
        val token = main { service.editorToken }
        check(main { service.isPrivacyModeActive }) { "The trial editor must prohibit personalized learning" }
        suspend fun <T> api(block: suspend RimeApi.() -> T): T {
            var outcome: Result<T>? = null
            val job = main { service.postRimeJob { outcome = runCatching { block() } } }
            withTimeout(30_000) { job.join() }
            check(!job.isCancelled) { "The editor-owned appearance operation was cancelled" }
            return checkNotNull(outcome) { "The trial editor changed before the appearance operation" }.getOrThrow()
        }
        val prefs = AppPrefs.defaultInstance()
        val preferences = prefs.candidates.textSize.sharedPreferences
        val keys = listOf(prefs.candidates.textSize.key, prefs.keyboard.keyboardHeight.key, prefs.candidates.mode.key)
        val previousPreferences = keys.associateWith { preferences.all[it] }
        val originalText = main { trial.text.toString().also { check(it.isEmpty()) } }
        val originalAscii = runBlocking {
            api {
                check(getRawInput().isEmpty())
                getRuntimeOption("ascii_mode")
            }
        }
        val initialDeployments = deployments.get()
        val commits = AtomicInteger()
        val commitObserver = session.lifecycleScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            session.run { messageFlow }.collect {
                if (it is RimeMessage.CommitTextMessage && !it.data.text.isNullOrEmpty()) commits.incrementAndGet()
            }
        }
        fun candidate(view: InputView): CandidateViewHolder? = view.di.direct.instance<CompactCandidateDelegate>().view
            .findViewHolderForAdapterPosition(0) as? CandidateViewHolder
        fun label(holder: CandidateViewHolder): TextView? = descendants(holder.itemView).filterIsInstance<TextView>()
            .firstOrNull { it.isShown && it.text.toString() == holder.text }
        fun checkUncommitted(raw: String, expectedText: String) {
            check(runBlocking { api { getRawInput() } } == raw) { "Appearance preference changed unfinished Rime input" }
            main {
                check(service.isCurrentEditor(token) && service.editorToken == token && service.currentInputEditorInfo?.fieldId == trial.id)
                val view = checkNotNull(input())
                val holder = checkNotNull(candidate(view))
                check(holder.itemView.isShown && holder.text == expectedText)
                check(holder.snapshot == session.run { candidateSnapshotCached } && holder.snapshot.editorToken == token)
                val value = checkNotNull(trial.text)
                val start = BaseInputConnection.getComposingSpanStart(value)
                val end = BaseInputConnection.getComposingSpanEnd(value)
                val committedText = if (start >= 0 && end >= start) value.removeRange(start, end).toString() else value.toString()
                check(committedText == originalText) {
                    "Appearance preference committed trial text outside the composing span"
                }
                check(!activity.isPageDone(SetupPage.Try))
            }
            instrumentation.waitForIdleSync()
            check(commits.get() == 0 && deployments.get() == initialDeployments) { "Appearance preference committed text or deployed configuration" }
        }
        try {
            main {
                prefs.candidates.mode.setValue(PopupCandidatesMode.DISABLED)
                prefs.candidates.textSize.setValue(12)
                prefs.keyboard.keyboardHeight.setValue(200)
            }
            runBlocking {
                api {
                    clearComposition()
                    setRuntimeOption("ascii_mode", false)
                    simulateKeySequence("hanyu")
                }
            }
            await("Native hanyu did not reach an actual visible 12sp compact candidate") {
                main {
                    input()?.let(::candidate)?.let { holder ->
                        holder.text.any { it in '\u3400'..'\u9fff' } && label(holder)?.let {
                            kotlin.math.abs(it.textSize - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, it.resources.displayMetrics)) < 0.5f &&
                                holder.snapshot == session.run { candidateSnapshotCached }
                        } == true
                    } == true
                }
            }
            val raw = runBlocking { api { getRawInput().also { check(it == "hanyu") } } }
            val expected = main { checkNotNull(candidate(checkNotNull(input()))).text }
            checkUncommitted(raw, expected)
            instrumentation.sendStatus(0, SetupTrialFocusProbe.verify(instrumentation, activity, trial, service))
            checkUncommitted(raw, expected)
            val beforeFont = main { checkNotNull(input()) }
            main { prefs.candidates.textSize.setValue(36) }
            await("The rebuilt input view did not restore its real 36sp compact candidate") {
                main {
                    input()?.takeIf { it !== beforeFont }?.let(::candidate)?.let { holder ->
                        holder.text == expected && label(holder)?.let {
                            kotlin.math.abs(it.textSize - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 36f, it.resources.displayMetrics)) < 0.5f &&
                                it.height >= it.lineHeight + it.compoundPaddingTop + it.compoundPaddingBottom &&
                                holder.snapshot == session.run { candidateSnapshotCached }
                        } == true
                    } == true
                }
            }
            checkUncommitted(raw, expected)
            passed("changing candidate size 12 to 36sp restores the actual visible candidate without committing or losing unfinished hanyu")
            val beforeHeight = main { checkNotNull(input()) }
            val smallHeight = main { beforeHeight.di.direct.instance<KeyboardWindow>().currentKeyboard.height }
            main { prefs.keyboard.keyboardHeight.setValue(400) }
            await("The rebuilt taller keyboard did not restore its real compact candidate") {
                main {
                    input()?.takeIf { it !== beforeHeight }?.let { view ->
                        val holder = candidate(view)
                        view.di.direct.instance<KeyboardWindow>().currentKeyboard.height > smallHeight &&
                            holder != null && holder.text == expected && label(holder) != null &&
                            holder.snapshot == session.run { candidateSnapshotCached }
                    } == true
                }
            }
            checkUncommitted(raw, expected)
            passed("changing keyboard height 200 to 400dp retains the same editor, visible candidate and pending input without deployment")
        } finally {
            try {
                runBlocking {
                    try {
                        api {
                            clearComposition()
                            setRuntimeOption("ascii_mode", originalAscii)
                        }
                    } finally {
                        commitObserver.cancelAndJoin()
                    }
                }
            } finally {
                main {
                    val editor = preferences.edit()
                    previousPreferences.forEach { (key, value) ->
                        when (value) {
                            null -> editor.remove(key)
                            is Int -> editor.putInt(key, value)
                            is String -> editor.putString(key, value)
                            else -> error("Unexpected appearance preference type")
                        }
                    }
                    check(editor.commit())
                    trial.setText(originalText)
                }
            }
        }
    }

    /** Global system touch must reach Advanced, rather than a footer drawn over its centre. */
    private fun verifyUnfinishedTrialFooterTouch(
        instrumentation: Instrumentation,
        activity: SetupActivity,
        trial: SetupTrialEditText,
        session: RimeSession,
        passed: (String) -> Unit,
    ): Bundle {
        fun <T> main(block: () -> T): T {
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            return checkNotNull(outcome).getOrThrow()
        }
        fun await(message: String, deadline: Long = SystemClock.uptimeMillis() + 60_000, condition: () -> Boolean) {
            while (!condition()) {
                check(SystemClock.uptimeMillis() < deadline) { message }
                SystemClock.sleep(50)
            }
        }
        fun bounds(view: View): Rect {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        fun isFullyVisible(view: View): Boolean {
            val visible = Rect()
            // A dialog's root is offset from the screen origin. Compare local
            // clipping with local bounds; pointer injection still uses screen bounds.
            return view.width > 0 && view.height > 0 && view.getLocalVisibleRect(visible) &&
                visible == Rect(0, 0, view.width, view.height)
        }
        fun owner(context: Context): Activity? {
            var current = context
            val visited = mutableSetOf<Context>()
            while (visited.add(current)) {
                if (current is Activity) return current
                current = (current as? ContextWrapper)?.baseContext ?: return null
            }
            return null
        }
        var scrolls = 0
        var backs = 0
        var notificationCancels = 0
        var geometryChecks = 0
        var lastPager = Rect()
        var lastFooterTop = 0
        var lastAdvanced = Rect()
        var lastTapX = 0
        var lastTapY = 0
        var lastScrollY = 0
        var lastGestureDistance = 0
        var settledSamples = 0
        var focusedOwner = ""
        var route = ""
        var checkpointEqual = false
        var inputEqual = false
        var trialEqual = false
        fun record(stage: String) {
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("setup_footer_touch_stage", stage)
                    putString("setup_footer_touch_pager_rect", lastPager.flattenToString())
                    putInt("setup_footer_touch_footer_top", lastFooterTop)
                    putString("setup_footer_touch_advanced_rect", lastAdvanced.flattenToString())
                    putInt("setup_footer_touch_tap_x", lastTapX)
                    putInt("setup_footer_touch_tap_y", lastTapY)
                    putInt("setup_footer_touch_scroll_y", lastScrollY)
                    putInt("setup_footer_touch_gesture_distance", lastGestureDistance)
                    putInt("setup_footer_touch_settled_samples", settledSamples)
                    putInt("setup_footer_touch_swipes", scrolls)
                    putInt("setup_footer_touch_backs", backs)
                    putInt("setup_footer_touch_notification_cancel_touches", notificationCancels)
                    putString("setup_footer_touch_focused_owner", focusedOwner)
                    putString("setup_footer_touch_route", route)
                    putBoolean("setup_footer_touch_checkpoint_equal", checkpointEqual)
                    putBoolean("setup_footer_touch_input_equal", inputEqual)
                    putBoolean("setup_footer_touch_trial_equal", trialEqual)
                },
            )
        }
        fun geometry(stage: String) {
            await("Unfinished Try did not settle for $stage") {
                main {
                    val pager = activity.findViewById<View>(R.id.viewpager)
                    !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus() &&
                        activity.currentSetupPage == SetupPage.Try && pager.isShown && pager.isLaidOut &&
                        !pager.isLayoutRequested && !activity.window.decorView.isLayoutRequested
                }
            }
            main {
                check(!activity.isPageDone(SetupPage.Try) && activity.isPageDone(SetupPage.Input))
                check(activity.findViewById<Button>(R.id.next_button).visibility == View.GONE)
                val footer = listOf(R.id.prev_button, R.id.skip_button, R.id.next_button)
                    .map { activity.findViewById<Button>(it) }
                    .filter { it.visibility == View.VISIBLE && it.isShown }
                check(footer.isNotEmpty())
                check(activity.findViewById<Button>(R.id.prev_button).isShown)
                check(activity.findViewById<Button>(R.id.skip_button).isShown)
                lastPager = bounds(activity.findViewById(R.id.viewpager))
                lastFooterTop = footer.minOf { bounds(it).top }
                val visible = Rect()
                check(activity.findViewById<View>(R.id.viewpager).getGlobalVisibleRect(visible))
                check(lastPager.height() > 0 && lastPager.width() > 0 && visible == lastPager)
                check(lastPager.bottom <= lastFooterTop) {
                    "Unfinished Try content overlaps its visible footer: pager=$lastPager footerTop=$lastFooterTop"
                }
            }
            geometryChecks++
            record(stage)
        }
        fun motion(down: Long, action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
        fun touch(view: View, stage: String) {
            val position = main {
                check(view.isShown && view.isEnabled && view.isClickable)
                val full = bounds(view)
                check(isFullyVisible(view))
                full.exactCenterX() to full.exactCenterY()
            }
            lastTapX = position.first.toInt()
            lastTapY = position.second.toInt()
            record(stage)
            val down = SystemClock.uptimeMillis()
            motion(down, MotionEvent.ACTION_DOWN, position.first, position.second)
            SystemClock.sleep(60)
            motion(down, MotionEvent.ACTION_UP, position.first, position.second)
            instrumentation.waitForIdleSync()
        }
        val checkpoints = instrumentation.targetContext.getSharedPreferences("trime_setup", Context.MODE_PRIVATE)
        val beforeCheckpoints = checkpoints.all.toMap()
        val beforeFreshPending = AppPrefs.defaultInstance().profile.freshInputSetupPending.getValue()
        val beforeInput = runBlocking { session.runOnReady { inspectInputSetup() } }
        val beforeChoice = main { activity.inputChoice }
        val beforeKeepCurrent = main { activity.keepCurrentInput }
        val originalTrialText = main { trial.text.toString() }
        check(originalTrialText.isEmpty()) { "The new Try fixture unexpectedly retained trial text" }
        val fictionalTrialText = "t12 footer draft"
        main {
            trial.setText(fictionalTrialText)
            check(!activity.isPageDone(SetupPage.Try)) { "Programmatic Latin text completed the trial" }
        }
        val beforeTrial = main { trial.text.toString().also { check(it == fictionalTrialText) } }
        check(!checkpoints.getBoolean("trial_completed", false) && !checkpoints.getBoolean("deferred", false))
        val beforeSkipPage = main { activity.currentSetupPage }
        val beforeSkipPreferences = AppPrefs.defaultInstance().profile.freshInputSetupPending.sharedPreferences.all.toMap()
        val skipDeadline = SystemClock.uptimeMillis() + 30_000
        instrumentation.sendStatus(0, Bundle().apply { putString("setup_skip_no_stage", "before-real-skip-touch") })
        touch(main { activity.findViewById<Button>(R.id.skip_button) }, "actual-skip-centre-down-up")
        var noBounds: Rect? = null
        await("The actual Skip confirmation and unique No action did not appear", skipDeadline) {
            skipNoBounds(instrumentation, activity).also { noBounds = it } != null
        }
        val actualNoBounds = checkNotNull(noBounds)
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("setup_skip_no_stage", "actual-no-centre-down-up")
                putString("setup_skip_no_actual_button_rect", actualNoBounds.flattenToString())
            },
        )
        val skipDown = SystemClock.uptimeMillis()
        motion(skipDown, MotionEvent.ACTION_DOWN, actualNoBounds.exactCenterX(), actualNoBounds.exactCenterY())
        SystemClock.sleep(60)
        motion(skipDown, MotionEvent.ACTION_UP, actualNoBounds.exactCenterX(), actualNoBounds.exactCenterY())
        instrumentation.waitForIdleSync()
        await("Skip No did not return the same unfinished Setup window", skipDeadline) {
            main { !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus() }
        }
        check(main { activity.currentSetupPage == beforeSkipPage }) { "Skip No changed the current page" }
        check(main { trial.text.toString() == beforeTrial }) { "Skip No changed the nonempty trial draft" }
        check(checkpoints.all == beforeCheckpoints) { "Skip No changed setup checkpoints" }
        check(AppPrefs.defaultInstance().profile.freshInputSetupPending.sharedPreferences.all == beforeSkipPreferences) {
            "Skip No changed preference key presence, runtime types or values"
        }
        check(runBlocking { session.runOnReady { inspectInputSetup() } } == beforeInput) { "Skip No changed the active input configuration" }
        check(
            main { activity.inputChoice == beforeChoice && activity.keepCurrentInput == beforeKeepCurrent } &&
                AppPrefs.defaultInstance().profile.freshInputSetupPending.getValue() == beforeFreshPending,
        ) {
            "Skip No changed input choices or fresh-setup state"
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("setup_skip_no_stage", "draft-checkpoints-input-page-preserved") })
        geometry("unfinished-try-before-advanced")
        passed("unfinished Try keeps its real viewport above every visible navigation button when Next is gone")
        val advanced = main {
            activity.supportFragmentManager.fragments.filterIsInstance<SetupFragment>()
                .first { it.setupPage == SetupPage.Try }.requireView().findViewById<Button>(R.id.advanced_button)
        }
        val scroll = main {
            var parent = advanced.parent
            while (parent != null && parent !is ScrollView) parent = parent.parent
            check(parent is ScrollView)
            parent
        }
        val touchSlop = main { ViewConfiguration.get(activity).scaledTouchSlop }
        val advancedDeadline = SystemClock.uptimeMillis() + 60_000
        fun settleScroll() {
            settledSamples = 0
            var previous: Pair<Int, Rect>? = null
            while (settledSamples < 4) {
                check(SystemClock.uptimeMillis() < advancedDeadline) { "Advanced scroll did not settle before its original deadline" }
                val sample = AtomicReference<Result<Triple<Int, Rect, Boolean>>?>(null)
                main {
                    scroll.postOnAnimation {
                        sample.set(
                            runCatching {
                                Triple(
                                    scroll.scrollY,
                                    bounds(advanced),
                                    activity.hasWindowFocus() && scroll.isShown && scroll.isLaidOut && !scroll.isLayoutRequested &&
                                        advanced.isShown && advanced.isLaidOut && !advanced.isLayoutRequested &&
                                        !activity.window.decorView.isLayoutRequested,
                                )
                            },
                        )
                    }
                }
                await("Advanced scroll frame did not arrive", advancedDeadline) { sample.get() != null }
                val observed = checkNotNull(sample.get()).getOrThrow()
                lastScrollY = observed.first
                lastAdvanced = observed.second
                val current = lastScrollY to lastAdvanced
                settledSamples = if (observed.third) {
                    if (current == previous) settledSamples + 1 else 1
                } else {
                    0
                }
                previous = current
            }
            record("advanced-scroll-settled")
        }
        var fullyVisible = false
        for (attempt in 0..8) {
            settleScroll()
            fullyVisible = main {
                lastAdvanced = bounds(advanced)
                val visible = Rect()
                advanced.isShown && advanced.isLaidOut && !advanced.isLayoutRequested &&
                    advanced.getGlobalVisibleRect(visible) && visible == lastAdvanced &&
                    lastPager.contains(lastAdvanced) && lastAdvanced.bottom <= lastFooterTop
            }
            if (fullyVisible) break
            check(attempt < 8) { "Advanced never became fully visible above the actual footer" }
            val viewport = main {
                val visible = Rect()
                check(scroll.getGlobalVisibleRect(visible))
                visible.top = maxOf(visible.top, lastPager.top)
                visible.bottom = minOf(visible.bottom, lastPager.bottom, lastFooterTop)
                check(visible.height() > 64)
                visible
            }
            // Cover only the clipped bottom plus drag slop and a small visible margin.
            // A full-page swipe can overshoot this short ScrollView and leave its edge effect active.
            val gap = (lastAdvanced.bottom - viewport.bottom).coerceAtLeast(0)
            lastGestureDistance = (gap + touchSlop * 2).coerceAtMost((viewport.height() * 0.6f).toInt())
            check(lastGestureDistance > touchSlop)
            val x = viewport.exactCenterX()
            val start = viewport.top + viewport.height() * 0.8f
            val end = start - lastGestureDistance
            val down = SystemClock.uptimeMillis()
            settledSamples = 0
            motion(down, MotionEvent.ACTION_DOWN, x, start)
            for (step in 1..10) {
                SystemClock.sleep(35)
                motion(down, MotionEvent.ACTION_MOVE, x, start + (end - start) * step / 10f)
            }
            // Keep real stationary MOVE samples for at least 200 ms before releasing;
            // lifting immediately after the last moving sample can start another fling.
            val stationaryStart = SystemClock.uptimeMillis()
            repeat(4) {
                SystemClock.sleep(50)
                motion(down, MotionEvent.ACTION_MOVE, x, end)
            }
            check(SystemClock.uptimeMillis() - stationaryStart >= 200)
            motion(down, MotionEvent.ACTION_UP, x, end)
            scrolls++
            main {
                lastScrollY = scroll.scrollY
                lastAdvanced = bounds(advanced)
            }
            record("actual-scroll-to-advanced")
        }
        check(fullyVisible)
        check(SystemClock.uptimeMillis() < advancedDeadline) { "Advanced touch preparation exceeded its original deadline" }
        val monitor = instrumentation.addMonitor(SetupAdvancedSettingsActivity::class.java.name, null, false)
        var host: SetupAdvancedSettingsActivity? = null
        var returnedThroughBack = false
        try {
            touch(advanced, "actual-advanced-centre-down-up")
            await("Advanced touch did not open the standard schema settings host and its real toolbar", advancedDeadline) {
                (monitor.waitForActivityWithTimeout(50) as? SetupAdvancedSettingsActivity)?.let { host = it }
                main {
                    val seen = windowRoots().asSequence().filter { it.isShown }
                        .mapNotNull { owner(it.context) }.filterIsInstance<SetupAdvancedSettingsActivity>().distinct().toList()
                    check(seen.size <= 1) { "Ambiguous standard advanced settings Activity" }
                    seen.singleOrNull()?.let { host = it }
                    val current = host ?: return@main false
                    check(current.javaClass == SetupAdvancedSettingsActivity::class.java)
                    check(!activity.isFinishing && !activity.isDestroyed) { "Advanced launch discarded unfinished Setup" }
                    route = (current.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
                        ?.navController?.currentDestination?.route.orEmpty()
                    route == NavigationRoute.SchemaList.path && descendants(current.window.decorView)
                        .filterIsInstance<Toolbar>().any { it.menu.findItem(R.id.action_deploy) != null }
                }
            }
            // Dialog.show() adds the prompt during menu installation, but its
            // first layout and window focus arrive asynchronously. A prompt
            // without focus yet must not be mistaken for an absent prompt.
            val promptAndFocusDeadline = SystemClock.uptimeMillis() + 60_000
            val expectsNotificationPrompt = main {
                !XXPermissions.isGranted(checkNotNull(host), Permission.POST_NOTIFICATIONS)
            }
            if (expectsNotificationPrompt) {
                var notificationRoot: View? = null
                await("The required exact notification-permission prompt did not appear", promptAndFocusDeadline) {
                    main {
                        val matches = windowRoots().filter { root ->
                            root.isShown && owner(root.context) === host &&
                                descendants(root).filterIsInstance<TextView>()
                                    .any { it.isShown && it.text.toString() == activity.getString(R.string.notification_permission_title) }
                        }
                        check(matches.size <= 1) { "Ambiguous notification-permission prompt" }
                        notificationRoot = matches.singleOrNull()
                        notificationRoot != null
                    }
                }
                val prompt = checkNotNull(notificationRoot)
                val cancel = main {
                    val matches = descendants(prompt).filterIsInstance<Button>()
                        .filter {
                            it.id == android.R.id.button2 && it.isShown && it.isEnabled &&
                                it.text.toString().equals(activity.getString(android.R.string.cancel), ignoreCase = true)
                        }
                        .toList()
                    check(matches.size == 1) { "The exact notification-permission Cancel button is missing" }
                    matches.single()
                }
                try {
                    await("The exact notification-permission Cancel button did not become focused and fully visible", promptAndFocusDeadline) {
                        main {
                            prompt.isShown && prompt.hasWindowFocus() && cancel.isShown && cancel.isEnabled &&
                                cancel.isLaidOut && !cancel.isLayoutRequested && isFullyVisible(cancel)
                        }
                    }
                } finally {
                    val geometry = main {
                        val local = Rect()
                        val global = Rect()
                        Bundle().apply {
                            putBoolean("setup_notification_prompt_window_focus", prompt.hasWindowFocus())
                            putBoolean("setup_notification_cancel_local_visible", cancel.getLocalVisibleRect(local))
                            putBoolean("setup_notification_cancel_global_visible", cancel.getGlobalVisibleRect(global))
                            putString("setup_notification_cancel_local_rect", local.flattenToString())
                            putString("setup_notification_cancel_global_rect", global.flattenToString())
                            putString("setup_notification_cancel_screen_rect", bounds(cancel).flattenToString())
                            putString("setup_notification_root_screen_rect", bounds(prompt).flattenToString())
                        }
                    }
                    instrumentation.sendStatus(0, geometry)
                }
                touch(cancel, "actual-notification-permission-cancel-centre-down-up")
                notificationCancels++
            }
            await("Schema settings did not own a focused window after settling the notification prompt", promptAndFocusDeadline) {
                main {
                    val current = checkNotNull(host)
                    route = (current.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
                        ?.navController?.currentDestination?.route.orEmpty()
                    !current.isFinishing && !current.isDestroyed && current.hasWindowFocus() &&
                        current.window.decorView.hasWindowFocus() && route == NavigationRoute.SchemaList.path
                }
            }
            focusedOwner = main { checkNotNull(host).javaClass.name }
            record("actual-focused-standard-schema-settings")
            for (attempt in 1..2) {
                if (main { activity.hasWindowFocus() && !activity.isFinishing && !activity.isDestroyed }) break
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                backs++
                instrumentation.waitForIdleSync()
                await("Actual Back did not settle the settings stack") {
                    main {
                        activity.hasWindowFocus() || host?.let { !it.isDestroyed && it.hasWindowFocus() } == true
                    }
                }
                record("actual-back-$attempt")
            }
            await("Two actual Back keys did not return the same unfinished Setup Activity") {
                main {
                    !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus() &&
                        activity.window.decorView.hasWindowFocus() && activity.currentSetupPage == SetupPage.Try
                }
            }
            checkpointEqual = checkpoints.all == beforeCheckpoints &&
                AppPrefs.defaultInstance().profile.freshInputSetupPending.getValue() == beforeFreshPending
            inputEqual = runBlocking { session.runOnReady { inspectInputSetup() } } == beforeInput &&
                main { activity.inputChoice == beforeChoice && activity.keepCurrentInput == beforeKeepCurrent }
            trialEqual = main { trial.text.toString() == beforeTrial }
            check(checkpointEqual && inputEqual && trialEqual) { "Advanced/Back changed unfinished wizard state" }
            check(!checkpoints.getBoolean("trial_completed", false) && !checkpoints.getBoolean("deferred", false))
            geometry("unfinished-try-after-real-back")
            main {
                trial.setText(originalTrialText)
                check(trial.text.isNullOrEmpty() && !activity.isPageDone(SetupPage.Try))
            }
            focusedOwner = main { activity.javaClass.name }
            returnedThroughBack = true
            record("actual-back-checkpoints-input-trial-equal")
            passed("actual Advanced centre touch opens schema settings and real Back preserves unfinished Setup checkpoints, input and trial text")
            return Bundle().apply {
                putInt("setup_footer_touch_checks", 2)
                putInt("setup_skip_no_checks", 1)
                putInt("setup_footer_touch_geometry_checks", geometryChecks)
                putInt("setup_footer_touch_swipes", scrolls)
                putInt("setup_footer_touch_backs", backs)
                putInt("setup_footer_touch_notification_cancel_touches", notificationCancels)
                putBoolean("setup_footer_touch_return_verified", returnedThroughBack)
                putBoolean("setup_footer_touch_checkpoint_equal", checkpointEqual)
                putBoolean("setup_footer_touch_input_equal", inputEqual)
                putBoolean("setup_footer_touch_trial_equal", trialEqual)
                putBoolean("setup_footer_touch_human_PNG_review", false)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            // Failed navigation cleanup cannot supply the required real-Back success.
            if (!returnedThroughBack) {
                main {
                    host?.takeIf { !it.isFinishing && !it.isDestroyed }?.finish()
                }
            }
        }
    }

    private fun restore(preferences: SharedPreferences, values: Map<String, *>) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unsupported setup preference value")
            }
        }
        check(editor.commit())
    }

    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        var passed = false
        var checks = 0
        val activities = mutableListOf<SetupActivity>()
        val context = instrumentation.targetContext
        val checkpoints = context.getSharedPreferences("trime_setup", Context.MODE_PRIVATE)
        val checkpointBackup = checkpoints.all
        val profile = AppPrefs.defaultInstance().profile
        val freshBackup = profile.freshInputSetupPending.getValue()
        val cacheEntryHadNoSession = RimeDaemon.readCached { true } == null
        var cachedKeyboard: RimeCachedReadTeardownAssertions.CachedKeyboard? = null
        val owner = "setup-closed-loop-${UUID.randomUUID()}"
        val session = RimeDaemon.createSession(owner)
        var originalSchema: String? = null
        var originalOptions = emptyMap<String, Boolean>()
        val deployments = AtomicInteger()
        var observer: kotlinx.coroutines.Job? = null

        fun <T> main(block: () -> T): T {
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            return checkNotNull(outcome).getOrThrow()
        }
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 60_000
            while (!condition()) {
                check(SystemClock.uptimeMillis() < deadline) { message }
                SystemClock.sleep(50)
            }
        }
        fun passed(name: String) {
            checks++
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS setup closed loop $checks: $name\n") })
        }
        fun fragment(activity: SetupActivity, page: SetupPage): SetupFragment = activity.supportFragmentManager.fragments
            .filterIsInstance<SetupFragment>().first { it.setupPage == page && it.view != null }
        fun launch(): SetupActivity {
            val monitor = instrumentation.addMonitor(SetupActivity::class.java.name, null, false)
            val handler = Handler(context.mainLooper)
            val failure = AtomicReference<Throwable?>()
            val start = Runnable {
                runCatching {
                    context.startActivity(
                        Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }.exceptionOrNull()?.let(failure::set)
            }
            try {
                instrumentation.sendStatus(
                    0,
                    Bundle().apply {
                        putString("debug_setup_launch_stage", if (activities.isEmpty()) "fresh" else "existing")
                        putInt("debug_setup_launch_currentpid", android.os.Process.myPid())
                    },
                )
                check(handler.post(start)) { "Unable to post the public Setup Activity launch" }
                var created: SetupActivity? = null
                await("The public Setup Activity launch did not create an Activity within 60 seconds") {
                    failure.get()?.let { throw it }
                    val seen = monitor.waitForActivityWithTimeout(100) as? SetupActivity
                    if (seen != null) {
                        activities += seen
                        created = seen
                    }
                    created != null
                }
                return checkNotNull(created)
            } finally {
                handler.removeCallbacks(start)
                instrumentation.removeMonitor(monitor)
            }
        }
        fun inputReady(activity: SetupActivity) {
            await("Input setup page did not finish reading the current scheme") {
                main { activity.currentSetupPage == SetupPage.Input && !activity.changingInput && activity.inputState != null }
            }
        }
        fun confirmInput(activity: SetupActivity) {
            main { check(fragment(activity, SetupPage.Input).requireView().findViewById<Button>(R.id.action_button).performClick()) }
            await("Applying the selected input scheme failed") { main { activity.isPageDone(SetupPage.Input) && !activity.changingInput } }
        }

        try {
            fun verifySelectedInputAndObserveDeployments() {
                check(Build.HARDWARE in setOf("ranchu", "goldfish"))
                instrumentation.sendStatus(
                    0,
                    Bundle().apply { putInt("setup_ime_selection_waiting_pid", android.os.Process.myPid()) },
                )
                await("The disposable emulator did not actually enable and select this APK after the UI probe started") {
                    InputMethodUtils.checkIsTrimeEnabled() && InputMethodUtils.checkIsTrimeSelected()
                }
                check(InputMethodUtils.checkIsTrimeEnabled() && InputMethodUtils.checkIsTrimeSelected()) {
                    "The disposable emulator must have this APK enabled and selected before the UI probe"
                }
                runBlocking {
                    session.runOnReady {
                        originalSchema = selectedSchemaId()
                        originalOptions = currentSchema().switches.filter { it.name.isNotEmpty() && it.name != "prediction" }
                            .associate { it.name to getRuntimeOption(it.name) }
                        clearComposition()
                    }
                }
                observer = session.lifecycleScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    session.run { messageFlow }.collect {
                        if (it is RimeMessage.DeployMessage && it.data == RimeMessage.DeployMessage.State.Start) deployments.incrementAndGet()
                    }
                }
            }
            verifySelectedInputAndObserveDeployments()
            fun verifyFreshWizardRecommendation(): SetupActivity {
                check(checkpoints.edit().clear().commit())
                profile.freshInputSetupPending.setValue(true)
                val fresh = launch()
                inputReady(fresh)
                main {
                    val view = fragment(fresh, SetupPage.Input).requireView()
                    check(view.findViewById<RadioButton>(R.id.full_pinyin_option).isChecked)
                    check(view.findViewById<RadioButton>(R.id.simplified_option).isChecked)
                    check(!fresh.isPageDone(SetupPage.Input))
                    check(!fresh.findViewById<Button>(R.id.next_button).isEnabled)
                }
                passed("fresh wizard recommends full pinyin and simplified Chinese without pretending activation finished")
                check(runBlocking { session.runOnReady { selectedSchemaId() } } == originalSchema)
                passed("reading a recommendation does not change the current Rime schema")

                return fresh
            }
            val fresh = verifyFreshWizardRecommendation()
            fun verifyFreshTrialAndRecreation(fresh: SetupActivity) {
                confirmInput(fresh)
                val applied = runBlocking { session.runOnReady { inspectInputSetup() } }
                check(applied.schemaId == "luna_pinyin_simp")
                check(applied.selection == InputSetupSelection(InputLayout.FULL_PINYIN, ChineseVariant.SIMPLIFIED))
                check(!profile.freshInputSetupPending.getValue())
                passed("applying the actual wizard button confirms the shared simplified full-pinyin configuration")
                main { check(fresh.findViewById<Button>(R.id.next_button).performClick()) }
                await("The actual trial editor was not shown") {
                    main { fresh.currentSetupPage == SetupPage.Try && fragment(fresh, SetupPage.Try).trialInput()?.isShown == true }
                }
                val trial = main { fragment(fresh, SetupPage.Try).requireView().findViewById<SetupTrialEditText>(R.id.trial_input) }
                result.putAll(verifyUnfinishedTrialFooterTouch(instrumentation, fresh, trial, session, ::passed))
                verifyAppearanceDuringComposition(instrumentation, fresh, trial, session, deployments, ::passed)
                main {
                    trial.setText("你好")
                    check(!fresh.isPageDone(SetupPage.Try))
                    trial.text?.clear()
                    val connection = checkNotNull(trial.onCreateInputConnection(EditorInfo()))
                    check(connection.commitText("nihao", 1))
                    check(connection.setComposingText("你好", 1))
                    check(connection.finishComposingText())
                }
                SystemClock.sleep(150)
                main { check(!fresh.isPageDone(SetupPage.Try)) }
                check(!checkpoints.getBoolean("trial_completed", false))
                passed("programmatic text, Latin commits and unfinished composition cannot pass the trial")
                main {
                    trial.text?.clear()
                    check(checkNotNull(trial.onCreateInputConnection(EditorInfo())).commitText("你好", 1))
                }
                await("A successful committed Han result did not complete the trial") { main { fresh.isPageDone(SetupPage.Try) } }
                check(checkpoints.all.values.none { it is String && it.contains("你好") })
                passed("successful Chinese commit completes the trial while wizard preferences keep no trial text")

                val monitor = instrumentation.addMonitor(SetupActivity::class.java.name, null, false)
                try {
                    var lastFocusState: List<Any?>? = null
                    var focusDiagnostics = 0
                    fun reportTrialFocus(stage: String, activity: SetupActivity) {
                        val input = activity.supportFragmentManager.fragments.filterIsInstance<SetupFragment>()
                            .firstOrNull { it.setupPage == SetupPage.Try }?.trialInput()
                        val focused = activity.currentFocus
                        val state = listOf(
                            stage,
                            activity.isTrialFocusRestorationPending,
                            activity.currentSetupPage.name,
                            activity.lifecycle.currentState.name,
                            activity.hasWindowFocus(),
                            input?.isAttachedToWindow,
                            input?.isShown,
                            input?.isEnabled,
                            input?.isLaidOut,
                            input?.isLayoutRequested,
                            input?.hasFocus(),
                            focused?.id,
                            focused?.javaClass?.name,
                        )
                        if (state == lastFocusState || focusDiagnostics >= 16) return
                        lastFocusState = state
                        focusDiagnostics++
                        val diagnostic = Bundle().apply {
                            putString("setup_trial_focus_stage", stage)
                            putBoolean("setup_trial_focus_restore_pending", activity.isTrialFocusRestorationPending)
                            putString("setup_trial_focus_page", activity.currentSetupPage.name)
                            putString("setup_trial_focus_lifecycle", activity.lifecycle.currentState.name)
                            putBoolean("setup_trial_focus_window_focused", activity.hasWindowFocus())
                            putBoolean("setup_trial_focus_input_present", input != null)
                            putBoolean("setup_trial_focus_attached", input?.isAttachedToWindow == true)
                            putBoolean("setup_trial_focus_shown", input?.isShown == true)
                            putBoolean("setup_trial_focus_enabled", input?.isEnabled == true)
                            putBoolean("setup_trial_focus_laid_out", input?.isLaidOut == true)
                            putBoolean("setup_trial_focus_layout_requested", input?.isLayoutRequested == true)
                            putBoolean("setup_trial_focus_actual", input?.hasFocus() == true)
                            putInt("setup_trial_focus_owner_id", focused?.id ?: View.NO_ID)
                            putString("setup_trial_focus_owner_class", focused?.javaClass?.name.orEmpty())
                            putInt("setup_trial_focus_diagnostic_count", focusDiagnostics)
                        }
                        result.putAll(diagnostic)
                        instrumentation.sendStatus(0, diagnostic)
                    }
                    main { reportTrialFocus("before-recreate", fresh) }
                    val restoreTrialFocus = main { trial.hasFocus() }
                    main { fresh.recreate() }
                    var replacement: SetupActivity? = null
                    await("Setup recreation did not produce a replacement Activity") {
                        val seen = monitor.waitForActivityWithTimeout(100) as? SetupActivity
                        if (seen != null && seen !== fresh && main { !seen.isDestroyed && !seen.isFinishing }) replacement = seen
                        replacement != null
                    }
                    val recreated = checkNotNull(replacement).also { activities += it }
                    await("Completed trial state was lost after recreation") {
                        main { fresh.isDestroyed && recreated.currentSetupPage == SetupPage.Try && recreated.isPageDone(SetupPage.Try) }
                    }
                    main { check(fragment(recreated, SetupPage.Try).trialInput()?.text.isNullOrEmpty()) }
                    if (restoreTrialFocus) {
                        await("The recreated trial did not recover its actual view focus after the Fragment attached") {
                            main {
                                reportTrialFocus("recreated-focus-wait", recreated)
                                recreated.hasWindowFocus() && fragment(recreated, SetupPage.Try).trialInput()?.hasFocus() == true
                            }
                        }
                    }
                    result.putBoolean("setup_trial_focus_recreation_restored", restoreTrialFocus)
                    passed("recreation keeps completion checkpoints without restoring trial text")
                    main {
                        val done = recreated.findViewById<Button>(R.id.next_button)
                        check(done.isShown && done.isEnabled && done.performClick())
                    }
                    await("The completed wizard was not destroyed after its Done button") {
                        main { recreated.isDestroyed }
                    }
                    check(!SetupPage.hasUndonePage())
                    check(runBlocking { session.runOnReady { inspectInputSetup() } } == applied)
                    passed("the real Done button exits the completed wizard and preserves the applied input selection")
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
            }
            verifyFreshTrialAndRecreation(fresh)
            fun verifyExistingInputAndDeferredTrial() {
                runBlocking {
                    session.runOnReady {
                        configureInput(InputSetupSelection(InputLayout.T9, ChineseVariant.TRADITIONAL))
                    }
                }
                check(checkpoints.edit().clear().commit())
                profile.freshInputSetupPending.setValue(false)
                val previous = runBlocking { session.runOnReady { inspectInputSetup() } }
                val existing = launch()
                inputReady(existing)
                main { check(fragment(existing, SetupPage.Input).requireView().findViewById<RadioButton>(R.id.keep_current_input_option).isChecked) }
                confirmInput(existing)
                val retained = runBlocking { session.runOnReady { inspectInputSetup() } }
                check(retained.schemaId == previous.schemaId && retained.selection == previous.selection)
                passed("existing traditional nine-key input stays unchanged when keeping the current scheme")
                main { check(existing.findViewById<Button>(R.id.next_button).performClick()) }
                await("Existing user could not open the trial page") { main { existing.currentSetupPage == SetupPage.Try } }
                main { check(fragment(existing, SetupPage.Try).requireView().findViewById<Button>(R.id.trial_later_button).performClick()) }
                await("Explicitly deferring the trial was not accepted") { main { existing.isPageDone(SetupPage.Try) } }
                check(checkpoints.getBoolean("deferred", false) && !checkpoints.getBoolean("trial_completed", false))
                check(!SetupPage.hasUndonePage())
                passed("trying later is explicit, does not fake a successful trial, and does not reopen completed system steps")
                check(deployments.get() == 0) { "Layout/charset selection unexpectedly started deployment" }
                passed("wizard schema and charset choices do not trigger unrelated deployment")
            }
            verifyExistingInputAndDeferredTrial()
            cachedKeyboard = main {
                val input = windowRoots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().first()
                RimeCachedReadTeardownAssertions.capture(input)
            }
            passed = true
            result.putString("stream", "PASS: $checks setup UI/state checks; real keyboard touch acceptance is separate\n")
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
        } finally {
            main { activities.asReversed().forEach { if (!it.isDestroyed && !it.isFinishing) it.finish() } }
            runCatching {
                await("Setup probe Activity cleanup did not finish") { main { activities.all { it.isDestroyed } } }
                runBlocking {
                    observer?.cancelAndJoin()
                    session.runOnReady {
                        clearComposition()
                        originalSchema?.let { check(selectSchema(it)) }
                        originalOptions.forEach { (name, value) -> setRuntimeOption(name, value) }
                    }
                }
                restore(checkpoints, checkpointBackup)
                profile.freshInputSetupPending.setValue(freshBackup)
            }.onFailure {
                passed = false
                result.putString("stream", result.getString("stream", "") + "FAIL cleanup: ${it.stackTraceToString()}\n")
            }
            RimeDaemon.destroySession(owner)
        }
        if (passed) {
            runCatching {
                RimeCachedReadTeardownAssertions.verify(instrumentation, checkNotNull(cachedKeyboard), cacheEntryHadNoSession)
            }.onSuccess(result::putAll).onFailure {
                passed = false
                result.putString("stream", result.getString("stream", "") + "FAIL cached read teardown: ${it.stackTraceToString()}\n")
            }
        }
        result.putBoolean("passed", passed)
        result.putInt("setup_closed_loop_checks", checks)
        result.putInt("setup_deploy_start_count", deployments.get())
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
