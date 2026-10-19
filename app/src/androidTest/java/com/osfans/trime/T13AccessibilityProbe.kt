/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.typeface.IconicsHolder
import com.osfans.trime.core.RimeApi
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.icons.CompactCommunityMaterial
import com.osfans.trime.ime.bar.ui.ToolButton
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.composition.PreeditTextView
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.keyboard.KeyboardView
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ui.main.EditorProbeActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import com.mikepenz.iconics.typeface.library.community.R as CommunityMaterialR

/** Dedicated emulator only. Actual nodes/actions and editor output; not a TalkBack substitute. */
object T13AccessibilityProbe {
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

    private fun <T> node(view: View, block: (AccessibilityNodeInfo) -> T): T {
        val info = view.createAccessibilityNodeInfo()
        return try {
            block(info)
        } finally {
            // API 21 still pools these nodes.
            @Suppress("DEPRECATION")
            info.recycle()
        }
    }

    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        val result = Bundle()
        val report = JSONObject().put("api", Build.VERSION.SDK_INT)
            .put("scope", "Production View accessibility actions and emulator geometry; no physical TalkBack claim")
        val checks = JSONArray()
        val screenshots = JSONArray()
        report.put("screenshots", screenshots)
        val directory = File(context.filesDir, "runtime-audit/t13")
        var activity: Activity? = null
        var session: RimeSession? = null
        var originalSchema: String? = null
        var originalAscii = false
        val originalTheme = ThemeManager.prefs.selectedTheme.getValue()
        val prefs = AppPrefs.defaultInstance()
        val originalPrivacy = prefs.general.manualPrivacyMode.getValue()
        val manual = prefs.keyboard.manualLanguageMode
        val store = manual.sharedPreferences
        val manualPresent = store.contains(manual.key)
        val manualRaw = store.getString(manual.key, null)
        var preferencesChanged = false
        var passed = false
        fun <T> main(block: () -> T): T {
            var value: Result<T>? = null
            instrumentation.runOnMainSync { value = runCatching(block) }
            return checkNotNull(value).getOrThrow()
        }
        fun phase(text: String) {
            checks.put(text)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: T13 $text\n") })
        }
        fun input(): InputView? = roots().asSequence().flatMap(::descendants).filterIsInstance<InputView>().firstOrNull { it.isShown }
        fun screenBounds(view: View, rootBounds: Rect): Rect {
            val rootPosition = IntArray(2).also(view.rootView::getLocationOnScreen)
            return Rect(rootBounds).apply { offset(rootPosition[0], rootPosition[1]) }
        }
        fun screenshot(stage: String, field: EditText) {
            check(stage.matches(Regex("[a-z0-9_.-]+")))
            check(directory.isDirectory || directory.mkdirs())
            val keyboardField = KeyboardView::class.java.getDeclaredField("keyboard").apply { isAccessible = true }
            val keyKeyboardField = KeyView::class.java.getDeclaredField("keyboard").apply { isAccessible = true }
            val keyField = KeyView::class.java.getDeclaredField("key").apply { isAccessible = true }
            fun state(): List<Any?> {
                val view = checkNotNull(input())
                val service = view.di.direct.instance<TrimeInputMethodService>()
                val keyboard = view.di.direct.instance<KeyboardWindow>().currentKeyboard
                val attached = descendants(view).filterIsInstance<KeyboardView>().single { it.isShown && it.isAttachedToWindow }
                check(keyboardField.get(attached) === keyboard) { "Visible keyboard still belongs to the previous model" }
                val keys = descendants(attached).filterIsInstance<KeyView>().toList()
                check(keys.size == keyboard.keys.size)
                keys.forEach { key ->
                    check(keyKeyboardField.get(key) === keyboard && keyField.get(key) === keyboard.keys[key.id])
                }
                val visible = (descendants(view.rootView).filter { it.isShown } + sequenceOf(field)).toList()
                check(visible.all { it.isAttachedToWindow && !it.isLayoutRequested && it.isLaidOut }) { "Screenshot hierarchy has pending layout" }
                val geometry = visible.map { child ->
                    val xy = IntArray(2).also(child::getLocationOnScreen)
                    listOf(child, xy[0], xy[1], child.width, child.height, child.scrollX, child.scrollY)
                }
                val rime = checkNotNull(session)
                return listOf(
                    view, view.rootView, view.windowToken, keyboard, attached, keys, geometry,
                    service.editorToken, field.text.toString(), field.selectionStart, field.selectionEnd,
                    rime.run { candidateSnapshotCached }, rime.run { t9Cached.input },
                    rime.run { t9Cached.segments }, rime.run { compositionCached.preedit },
                )
            }
            val deadline = SystemClock.uptimeMillis() + 5_000
            var frozen: List<Any?>? = null
            var readinessFailure: Throwable? = null
            while (frozen == null && SystemClock.uptimeMillis() < deadline) {
                main { runCatching(::state).onSuccess { frozen = it }.onFailure { readinessFailure = it } }
                if (frozen == null) SystemClock.sleep(16)
            }
            val identity = checkNotNull(frozen) { "Screenshot $stage could not bind a stable hierarchy: $readinessFailure" }
            val root = main { checkNotNull(input()).rootView }
            val (image, rendering) = RenderedScreenshotBarrier.capture(instrumentation, root) {
                check(state() == identity) { "Screenshot $stage changed its attached layout, editor or candidate identity" }
            }
            try {
                val file = File(directory, "$stage.png")
                file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                screenshots.put(
                    JSONObject().put("stage", stage).put("path", file.absolutePath).put("bytes", file.length())
                        .put("sha256", digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) })
                        .put("width_px", image.width).put("height_px", image.height)
                        .put("rendering", rendering)
                        .put("personally_reviewed", false),
                )
            } finally {
                image.recycle()
            }
        }
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            check(File(context.getExternalFilesDir(null), "runtime-audit-dedicated").isFile) { "Requires dedicated test installation" }
            main {
                preferencesChanged = true
                prefs.general.manualPrivacyMode.setValue(false)
                check(store.edit().remove(manual.key).commit())
                session = RimeDaemon.createSession(javaClass.name)
            }
            runBlocking {
                withTimeout(300_000) {
                    checkNotNull(session).runOnReady {
                        originalSchema = selectedSchemaId()
                        originalAscii = getRuntimeOption("ascii_mode")
                    }
                    ThemeManager.init(context.resources.configuration)
                }
            }
            val editor = instrumentation.startActivitySync(Intent(context, EditorProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = editor
            val field = main {
                val field = object : EditText(editor) {
                    override fun isSuggestionsEnabled(): Boolean = false
                }.apply {
                    id = View.generateViewId()
                    hint = "T13 fictional input"
                    inputType = InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                }
                editor.setContentView(
                    LinearLayout(editor).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(field, LinearLayout.LayoutParams(-1, -2))
                    },
                )
                // This dedicated editor exercises the retained API 21 resize behavior too.
                @Suppress("DEPRECATION")
                editor.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                field.requestFocus()
                field
            }
            runBlocking {
                withTimeout(600_000) {
                    suspend fun until(label: String, predicate: () -> Boolean) {
                        withTimeout(30_000) { while (!main(predicate)) delay(50) }
                        phase(label)
                    }
                    val imm = editor.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    until("editor attached and served") { field.isAttachedToWindow && field.isLaidOut && field.hasWindowFocus() && imm.isActive(field) }
                    main { imm.showSoftInput(field, 0) }
                    until("input view visible") { input() != null }
                    val service = main { checkNotNull(input()).di.direct.instance<TrimeInputMethodService>() }
                    suspend fun api(block: suspend RimeApi.() -> Unit) {
                        var outcome: Result<Unit>? = null
                        val job = main { service.postRimeJob { outcome = runCatching { block() } } }
                        job.join()
                        check(!job.isCancelled)
                        checkNotNull(outcome) { "Editor changed before native operation" }.getOrThrow()
                    }
                    val keyboardChecks = KeyboardAccessibilityAssertions.verify(instrumentation, service, field)
                    report.put(
                        "keyboard",
                        JSONObject().apply {
                            put("checks", keyboardChecks.checks)
                            put("fixture_presses", keyboardChecks.fixturePresses)
                            put("fixture_actions", keyboardChecks.fixtureActions)
                            put("fixture_repeats", keyboardChecks.fixtureRepeats)
                            put("actual_editor_commit", keyboardChecks.actualEditorCommit)
                            put("state_restored", keyboardChecks.stateRestored)
                            put("real_talkback_service_verified", keyboardChecks.realTalkBackServiceVerified)
                            put("scrolling_keys", JSONArray(keyboardChecks.scrollingKeys.map { JSONObject(it) }))
                        },
                    )
                    // Observe the normal registry only after actual keyboard creation/actions.
                    // Do not register or look up a glyph before the cold input path has run.
                    main {
                        val active = IconicsHolder.FONTS["cmd"]
                        val font = JSONObject()
                            .put("after_keyboard_action_helper", true)
                            .put("provider_class", active?.javaClass?.name ?: JSONObject.NULL)
                            .put("provider_is_compact", active === CompactCommunityMaterial)
                        report.put("icon_font", font)
                        check(active === CompactCommunityMaterial) { "cmd font registry is not the compact provider: $font" }
                        val provider = checkNotNull(active)
                        val expectedFont = CommunityMaterialR.font.community_material_design_icons_font_v7_0_96
                        val glyph = provider.getIcon("cmd_undo")
                        font.put("font_resource", provider.fontRes)
                            .put("expected_font_resource", expectedFont)
                            .put("glyph_class", glyph.javaClass.name)
                            .put("glyph_is_enum", glyph is Enum<*>)
                            .put("glyph_name", glyph.name)
                            .put("glyph_code_unit", glyph.character.code)
                            .put("glyph_uses_active_provider", glyph.typeface === provider)
                        check(provider.fontRes == expectedFont) { "cmd font resource changed: $font" }
                        check(glyph !is Enum<*> && glyph.name == "cmd_undo" && glyph.character.code == 0xBA2A && glyph.typeface === provider) {
                            "cmd glyph identity changed or still loads an enum: $font"
                        }
                    }
                    phase("keyboard action helper completed")
                    val configurations = JSONArray()
                    report.put("configurations", configurations)
                    val composingGeometry = JSONArray()
                    report.put("composing_geometry", composingGeometry)
                    val auxiliaryGeometry = JSONArray()
                    report.put("auxiliary_geometry", auxiliaryGeometry)
                    fun recordGeometry(view: InputView, theme: String, rows: JSONArray): JSONObject {
                        val row = JSONObject().put("theme", theme).put("font_scale", view.resources.configuration.fontScale)
                            .put("orientation", view.resources.configuration.orientation)
                            .put("ui_mode", view.resources.configuration.uiMode)
                            .put("scrollable_t9_fallback", view.di.direct.instance<KeyboardWindow>().usesScrollableT9Fallback)
                        rows.put(row)
                        val decor = checkNotNull(service.window.window).decorView
                        val viewport = view.keyboardViewport
                        check(view.findViewById<View>(R.id.keyboard_viewport) === viewport)
                        check(view.windowToken == decor.windowToken && viewport.isAttachedToWindow && viewport.isShown)
                        check(viewport.isImportantForAccessibility && !viewport.isFocusable)
                        node(viewport) { info ->
                            check(info.className == "android.widget.ScrollView")
                            check(viewport.isEnabled && viewport.childCount == 1)
                            // ScrollView's node uses the exact range; canScrollVertically
                            // intentionally treats the last pixel differently.
                            val range = (viewport.getChildAt(0).height - viewport.height + viewport.paddingTop + viewport.paddingBottom).coerceAtLeast(0)
                            val backward = range > 0 && viewport.scrollY > 0
                            val forward = range > 0 && viewport.scrollY < range
                            val actions = info.actionList.map { it.id }
                            check(info.isScrollable == (range > 0))
                            check((AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in actions) == backward)
                            check((AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in actions) == forward)
                            row.put("scroll_container_accessible", true).put("scrollable", info.isScrollable)
                                .put("scroll_backward", backward).put("scroll_forward", forward)
                                .put("scroll_range", range).put("can_scroll_backward", viewport.canScrollVertically(-1))
                                .put("can_scroll_forward", viewport.canScrollVertically(1))
                        }
                        val editorVisible = Rect().also { check(field.getGlobalVisibleRect(it)) }
                        val editorRect = screenBounds(field, editorVisible)
                        val hostRect = screenBounds(view, Rect().also { check(view.getGlobalVisibleRect(it)) })
                        val viewportRect = screenBounds(viewport, Rect().also { check(viewport.getGlobalVisibleRect(it)) })
                        val preeditRects = descendants(decor).filterIsInstance<PreeditTextView>().filter { it.isShown }
                            .map { it.parent as View }.distinct().mapNotNull { preedit ->
                                val visible = Rect()
                                if (preedit.getGlobalVisibleRect(visible)) screenBounds(preedit, visible) else null
                            }.toList()
                        val regions = listOf(viewportRect) + preeditRects
                        val inputRect = Rect(viewportRect).apply { preeditRects.forEach { union(it) } }
                        val insets = InputMethodService.Insets().also(service::onComputeInsets)
                        val decorScreen = IntArray(2).also(decor::getLocationOnScreen)
                        val decorWindow = IntArray(2).also(decor::getLocationInWindow)
                        val windowScreenX = decorScreen[0] - decorWindow[0]
                        val windowScreenY = decorScreen[1] - decorWindow[1]
                        val touchableScreen = Region(insets.touchableRegion).apply { translate(windowScreenX, windowScreenY) }
                        fun regionRects(region: Region): JSONArray {
                            val result = JSONArray()
                            val iterator = RegionIterator(region)
                            val rect = Rect()
                            while (iterator.next(rect)) result.put(rect.toShortString())
                            return result
                        }
                        val editorIntersection = Region(editorRect).apply { op(touchableScreen, Region.Op.INTERSECT) }
                        val viewportWindow = IntArray(2).also(viewport::getLocationInWindow)
                        val panelWindow = IntArray(2).also(view.keyboardView::getLocationInWindow)
                        row.put("host_bounds", hostRect.toShortString())
                            .put("viewport_bounds", viewportRect.toShortString())
                            .put("preedit_bounds", JSONArray(preeditRects.map { it.toShortString() }))
                            .put("input_bounds", inputRect.toShortString())
                            .put("input_bounds_scope", "Bounding rectangle of the viewport and visible preedit roots; individual regions checked below")
                            .put("editor_bounds", editorRect.toShortString())
                            .put("viewport_window_position", JSONArray(viewportWindow.toList()))
                            .put("panel_window_position", JSONArray(panelWindow.toList()))
                            .put("viewport_scroll_y", viewport.scrollY)
                            .put("window_screen_offset", JSONArray(listOf(windowScreenX, windowScreenY)))
                            .put("content_top_insets", insets.contentTopInsets)
                            .put("visible_top_insets", insets.visibleTopInsets)
                            .put("touchable_insets", insets.touchableInsets)
                            .put("touchable_region_window", regionRects(insets.touchableRegion))
                            .put("touchable_region_screen", regionRects(touchableScreen))
                            .put("touchable_editor_intersection", regionRects(editorIntersection))
                        check(editorVisible.height() == field.height) { "Editor clipped vertically: $row" }
                        check(regions.none { Rect.intersects(editorRect, it) }) { "Visible input region obscures editor: $row" }
                        check(insets.contentTopInsets == viewportWindow[1] && insets.visibleTopInsets == viewportWindow[1]) { "IME boundary moved away from its viewport: $row" }
                        check(insets.touchableInsets == InputMethodService.Insets.TOUCHABLE_INSETS_REGION)
                        check(editorIntersection.isEmpty) { "IME touchable region intercepts the editor: $row" }
                        check(regions.all { rect -> Region(rect).apply { op(touchableScreen, Region.Op.DIFFERENCE) }.isEmpty }) { "Visible input region is not fully touchable: $row" }
                        row.put("editor_fully_visible", true).put("input_regions_touchable", true).put("touchable_editor_disjoint", true)
                        return row
                    }
                    for (theme in listOf("trime", "tongwenfeng.trime")) {
                        check(ThemeManager.selectTheme(theme) == theme)
                        api {
                            clearComposition()
                            check(selectSchema("luna_pinyin_t9"))
                            setRuntimeOption("ascii_mode", false)
                        }
                        until("$theme live T9 layout") {
                            input()?.let { view ->
                                view.di.direct.instance<ThemeScope>().theme === ThemeManager.activeTheme &&
                                    view.di.direct.instance<KeyboardWindow>().currentKeyboard.isT9Layout
                            } == true
                        }
                        main { field.editableText.clear() }
                        val helper = main {
                            val view = checkNotNull(input())
                            T13CandidateAccessibilityProbe.verify(view, view.di.direct.instance<ThemeScope>())
                        }
                        report.put("candidate_helper_$theme", JSONObject(helper))
                        report.put(
                            "candidate_rebind_events_$theme",
                            JSONObject(
                                main {
                                    T13CandidateAccessibilityProbe.verifyRebindEvents(checkNotNull(input()))
                                },
                            ),
                        )
                        val toolbarOptions = main {
                            descendants(checkNotNull(input())).filterIsInstance<ToolButton>()
                                .mapNotNull { it.option }.distinct().toList()
                        }
                        for ((option, preset) in listOf("ascii_mode" to "Mode_switch", "full_shape" to "Zenkaku_Hankaku")) {
                            if (option !in toolbarOptions) continue
                            val previous = checkNotNull(session).run { getRuntimeOptionCached(option) }
                            try {
                                for (enabled in listOf(!previous, previous)) {
                                    api { setRuntimeOption(option, enabled) }
                                    until("$theme toolbar $option label and state agree ($enabled)") {
                                        val view = checkNotNull(input())
                                        val states = view.di.direct.instance<ThemeScope>().theme.presetKeys.getValue(preset).states
                                        val expectedLabel = states[if (enabled) 1 else 0]
                                        descendants(view).filterIsInstance<ToolButton>().filter { it.option == option }.toList().let { buttons ->
                                            buttons.isNotEmpty() && buttons.all { button ->
                                                node(button) { info -> info.isCheckable && info.isCheckedForTest() == enabled && info.contentDescription == expectedLabel }
                                            }
                                        }
                                    }
                                }
                            } finally {
                                api { setRuntimeOption(option, previous) }
                            }
                        }
                        api { setRuntimeOption("ascii_mode", false) }
                        until("$theme T9 restored after toolbar checks") { input()?.di?.direct?.instance<KeyboardWindow>()?.currentKeyboard?.isT9Layout == true }
                        for ((index, digit) in "64".withIndex()) {
                            main {
                                val view = checkNotNull(input())
                                val keyboard = view.di.direct.instance<KeyboardWindow>().currentKeyboard
                                val keyIndex = keyboard.keys.indexOfFirst { it.click?.code == KeyEvent.KEYCODE_0 + (digit - '0') }
                                check(keyIndex >= 0)
                                val key = descendants(view).filterIsInstance<KeyView>().single { it.id == keyIndex && it.isShown }
                                node(key) { info ->
                                    check(info.className == "android.widget.Button" && info.isClickable && !info.contentDescription.isNullOrBlank())
                                }
                                check(key.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                            }
                            until("$theme digit $digit accepted once") { checkNotNull(session).run { t9Cached.input == "64".take(index + 1) } }
                        }
                        val chooseLabel = context.getString(R.string.a11y_t9_choose_syllable)
                        until("$theme pinyin actions reachable") {
                            descendants(checkNotNull(input())).any { view ->
                                view.isShown && node(view) { info -> info.actionList.any { it.label?.toString() == chooseLabel } }
                            }
                        }
                        main {
                            val choice = descendants(checkNotNull(input())).first { view ->
                                view.isShown && node(view) { info -> info.actionList.any { it.label?.toString() == chooseLabel } }
                            }
                            check(choice.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                        }
                        until("$theme pinyin selection locks without commit") {
                            checkNotNull(session).run { t9Cached.segments.any { it.locked } } && field.text.isEmpty()
                        }
                        var pendingPreedit: String? = null
                        var lockedSegments = 0
                        val pendingEditor = main { service.editorToken }
                        api {
                            check(getRawInput() == "64")
                            pendingPreedit = compositionCached.preedit
                            lockedSegments = t9Cached.segments.count { it.locked }
                        }
                        fun layoutKey(target: String): KeyView {
                            val view = checkNotNull(input())
                            val keyboard = view.di.direct.instance<KeyboardWindow>().currentKeyboard
                            val index = keyboard.keys.single { it.click?.select == target }.index
                            return descendants(view).filterIsInstance<KeyView>().single { it.id == index && it.isShown }
                        }
                        main { check(layoutKey("symbols").performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)) }
                        until("$theme symbol page opened with pending composition") {
                            input()?.di?.direct?.instance<KeyboardWindow>()?.currentKeyboard?.let { keyboard ->
                                !keyboard.isT9Layout && keyboard.keys.any { it.click?.select == ".return" }
                            } == true
                        }
                        api {
                            check(getRawInput() == "64" && compositionCached.preedit == pendingPreedit)
                            check(t9Cached.segments.count { it.locked } == lockedSegments)
                        }
                        val scrollAttempts = JSONArray()
                        report.put("symbol_return_scroll_$theme", scrollAttempts)
                        withTimeout(30_000) {
                            var previous: List<Any?>? = null
                            var lastRequested: List<Any?>? = null
                            var stableFrames = 0
                            var frameCallbacks = 0
                            var fullyReachable = false
                            while (!fullyReachable) {
                                val viewport = main { checkNotNull(input()).keyboardViewport }
                                suspendCancellableCoroutine<Unit> { continuation ->
                                    val callback = Runnable { if (continuation.isActive) continuation.resume(Unit) }
                                    main { viewport.postOnAnimation(callback) }
                                    continuation.invokeOnCancellation { viewport.removeCallbacks(callback) }
                                }
                                frameCallbacks++
                                main {
                                    val key = layoutKey(".return")
                                    val ancestors = generateSequence<View>(key) { it.parent as? View }.take(32).toList()
                                    if (viewport !in ancestors || ancestors.any { !it.isAttachedToWindow || !it.isShown || !it.isLaidOut || it.isLayoutRequested || it.width <= 0 || it.height <= 0 }) {
                                        previous = null
                                        stableFrames = 0
                                        return@main
                                    }
                                    fun state(): List<Any?> = ancestors.map { child ->
                                        val xy = IntArray(2).also(child::getLocationOnScreen)
                                        val visible = Rect().also { child.getGlobalVisibleRect(it) }
                                        listOf(child, child.width, child.height, xy[0], xy[1], child.scrollX, child.scrollY, visible)
                                    }
                                    val current = state()
                                    stableFrames = if (current == previous) stableFrames + 1 else 1
                                    previous = current
                                    if (stableFrames < 2) return@main
                                    val visible = Rect()
                                    val complete = key.getGlobalVisibleRect(visible) && visible.width() == key.width && visible.height() == key.height
                                    if (scrollAttempts.length() > 0 && complete) {
                                        fullyReachable = true
                                        return@main
                                    }
                                    if (current == lastRequested || scrollAttempts.length() >= 8) return@main
                                    val request = Rect(0, 0, key.width, key.height)
                                    val row = JSONObject().put("key_id", key.id).put("key_identity", System.identityHashCode(key))
                                        .put("stable_frames", stableFrames).put("frame_callbacks", frameCallbacks).put("uptime_ms", SystemClock.uptimeMillis())
                                        .put("key_layout", JSONArray(listOf(key.left, key.top, key.width, key.height)))
                                        .put("key_translation", JSONArray(listOf(key.translationX, key.translationY)))
                                        .put("visible_before", visible.toShortString()).put("request_rect", request.toShortString())
                                        .put("viewport_scroll_before", viewport.scrollY)
                                    scrollAttempts.put(row)
                                    row.put("request_returned", key.requestRectangleOnScreen(request, true))
                                        .put("viewport_scroll_after", viewport.scrollY)
                                    val after = Rect()
                                    key.getGlobalVisibleRect(after)
                                    row.put("visible_after", after.toShortString())
                                    lastRequested = state()
                                    previous = null
                                    stableFrames = 0
                                }
                            }
                        }
                        phase("$theme symbol return action is fully reachable")
                        screenshot("$theme-symbols-composition", field)
                        main {
                            val view = checkNotNull(input())
                            recordGeometry(view, theme, auxiliaryGeometry)
                            check(field.text.isEmpty() && service.editorToken == pendingEditor) { "Symbol page changed the pending editor" }
                            val returnKey = layoutKey(".return")
                            val viewport = view.keyboardViewport
                            report.put("return_previous_page_scroll_$theme", viewport.scrollY)
                            // A page change must also stop an in-flight accessibility scroll.
                            // Start it and activate Return in the same main-thread turn.
                            if (viewport.scrollY > 0) {
                                check(viewport.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
                                report.put("return_previous_page_scroll_action_$theme", true)
                            }
                            check(returnKey.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                        }
                        until("$theme T9 returns with locked composition and no commit") {
                            input()?.di?.direct?.instance<KeyboardWindow>()?.currentKeyboard?.isT9Layout == true &&
                                checkNotNull(session).run { t9Cached.input == "64" && t9Cached.segments.count { it.locked } == lockedSegments } &&
                                field.text.isEmpty() && service.editorToken == pendingEditor
                        }
                        api { check(getRawInput() == "64" && compositionCached.preedit == pendingPreedit) }
                        fun firstCandidate(): CandidateViewHolder? = input()?.di?.direct?.instance<CompactCandidateDelegate>()?.view?.findViewHolderForAdapterPosition(0) as? CandidateViewHolder
                        until("$theme current candidate visible") {
                            firstCandidate()?.let { it.itemView.isShown && it.snapshot == checkNotNull(session).run { candidateSnapshotCached } } == true
                        }
                        val (snapshot, expected) = main {
                            val viewportScroll = checkNotNull(input()).keyboardViewport.scrollY
                            report.put("return_viewport_scroll_$theme", viewportScroll)
                            check(viewportScroll == 0) { "Previous keyboard page left the candidate panel scrolled: $viewportScroll" }
                            val holder = checkNotNull(firstCandidate())
                            node(holder.itemView) { info ->
                                check(info.className == "android.widget.Button" && info.isClickable)
                                check(info.contentDescription.toString().contains(holder.text))
                            }
                            val visible = Rect()
                            check(holder.itemView.getGlobalVisibleRect(visible) && visible.height() == holder.itemView.height) { "Candidate clipped vertically" }
                            holder.snapshot to holder.text
                        }
                        screenshot("$theme-before-candidate-click", field)
                        main {
                            val view = checkNotNull(input())
                            recordGeometry(view, theme, composingGeometry)
                            val choices = descendants(view).filter { child ->
                                child.isShown && node(child) { info -> info.actionList.any { it.label?.toString() == chooseLabel } }
                            }.toList()
                            check(choices.isNotEmpty())
                            check(
                                choices.any { choice ->
                                    val visible = Rect()
                                    choice.getGlobalVisibleRect(visible) && visible.width() == choice.width && visible.height() == choice.height
                                },
                            ) { "No complete pinyin choice is visible during composition" }
                        }
                        main {
                            val holder = checkNotNull(firstCandidate())
                            check(holder.snapshot == snapshot && holder.snapshot == checkNotNull(session).run { candidateSnapshotCached } && holder.text == expected)
                            check(holder.itemView.isShown)
                            check(holder.itemView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                        }
                        until("$theme candidate action commits exact displayed text once") { field.text.toString() == expected }
                        // In the small-window fallback the candidate bar and keypad can exceed
                        // the viewport together. Scroll the whole keypad into view using the
                        // same public request available to accessibility services, then retain
                        // the simultaneous, full-size visibility assertion for every key below.
                        if (main { checkNotNull(input()).di.direct.instance<KeyboardWindow>().usesScrollableT9Fallback }) {
                            val view = main { checkNotNull(input()) }
                            val viewport = view.keyboardViewport
                            val keyboardField = KeyboardView::class.java.getDeclaredField("keyboard").apply { isAccessible = true }
                            val keyKeyboardField = KeyView::class.java.getDeclaredField("keyboard").apply { isAccessible = true }
                            val keyField = KeyView::class.java.getDeclaredField("key").apply { isAccessible = true }
                            val body = main { descendants(view).filterIsInstance<KeyboardView>().single { it.isShown && it.isAttachedToWindow } }
                            val keyboard = main { view.di.direct.instance<KeyboardWindow>().currentKeyboard }
                            val keys = main { descendants(body).filterIsInstance<KeyView>().toList() }
                            var frameCallbacks = 0
                            fun identity(): List<Any?> {
                                check(input() === view && keyboardField.get(body) === keyboard)
                                check(view.di.direct.instance<KeyboardWindow>().currentKeyboard === keyboard)
                                check(descendants(body).filterIsInstance<KeyView>().toList() == keys && keys.size == keyboard.keys.size)
                                keys.forEach { key -> check(keyKeyboardField.get(key) === keyboard && keyField.get(key) === keyboard.keys[key.id]) }
                                check(field.text.toString() == expected && service.editorToken == pendingEditor)
                                return listOf(
                                    view.windowToken, service.editorToken, field.text.toString(), field.selectionStart, field.selectionEnd,
                                    checkNotNull(session).run { candidateSnapshotCached }, checkNotNull(session).run { t9Cached.input },
                                    checkNotNull(session).run { t9Cached.segments }, checkNotNull(session).run { compositionCached.preedit },
                                )
                            }
                            suspend fun stableViewport(requireVisible: Boolean): List<Any?> = withTimeout(10_000) {
                                var previous: List<Any?>? = null
                                var stableFrames = 0
                                do {
                                    suspendCancellableCoroutine<Unit> { continuation ->
                                        val callback = Runnable { if (continuation.isActive) continuation.resume(Unit) }
                                        main { viewport.postOnAnimation(callback) }
                                        continuation.invokeOnCancellation { viewport.removeCallbacks(callback) }
                                    }
                                    frameCallbacks++
                                    val current = main {
                                        val ancestors = generateSequence<View>(body) { it.parent as? View }.take(32).toList()
                                        check(viewport in ancestors)
                                        if (ancestors.any { !it.isShown || !it.isAttachedToWindow || !it.isLaidOut || it.isLayoutRequested }) return@main null
                                        check(body.width > 0 && body.height > 0 && body.height <= viewport.height) { "Whole keypad exceeds the scrolling viewport" }
                                        val visible = Rect()
                                        val full = body.getGlobalVisibleRect(visible) && visible.width() == body.width && visible.height() == body.height
                                        if (requireVisible && !full) return@main null
                                        listOf(identity(), body.width, body.height, viewport.width, viewport.height, viewport.scrollY, visible)
                                    }
                                    stableFrames = if (current != null && current == previous) stableFrames + 1 else 0
                                    previous = current
                                } while (stableFrames < 2)
                                checkNotNull(previous)
                            }
                            stableViewport(false)
                            var nativeBefore: List<Any?>? = null
                            api { nativeBefore = listOf(selectedSchemaId(), getRuntimeOption("ascii_mode"), getRawInput()) }
                            val frozen = main { identity() }
                            val scrolling = JSONObject().put("scope", "Whole keypad standard scroll preparation; no TalkBack service claim")
                            report.put("keypad_scroll_preparation_$theme", scrolling)
                            main {
                                scrolling.put("before_scroll_y", viewport.scrollY).put("body_height", body.height).put("viewport_height", viewport.height)
                                    .put("before_visible_bounds", Rect().also { body.getGlobalVisibleRect(it) }.toShortString())
                                val requested = if (Build.VERSION.SDK_INT >= 23) {
                                    body.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id, null)
                                } else {
                                    body.requestRectangleOnScreen(Rect(0, 0, body.width, body.height), true)
                                }
                                scrolling.put("request_returned", requested).put("api23_show_on_screen", Build.VERSION.SDK_INT >= 23)
                                check(identity() == frozen) { "Scrolling the keypad changed input identity" }
                            }
                            stableViewport(true)
                            api { check(listOf(selectedSchemaId(), getRuntimeOption("ascii_mode"), getRawInput()) == nativeBefore) }
                            main {
                                check(identity() == frozen) { "Scrolling the keypad changed editor, candidate or composition state" }
                                scrolling.put("after_scroll_y", viewport.scrollY).put("whole_keypad_visible", true).put("input_unchanged", true)
                                    .put("after_visible_bounds", Rect().also { body.getGlobalVisibleRect(it) }.toShortString())
                                    .put("observed_frame_callbacks", frameCallbacks)
                            }
                        }
                        screenshot("$theme-after-candidate-commit", field)
                        main {
                            val view = checkNotNull(input())
                            val geometry = recordGeometry(view, theme, configurations)
                            val fieldRect = screenBounds(field, Rect().also { check(field.getGlobalVisibleRect(it)) })
                            val keyRects = descendants(view).filterIsInstance<KeyView>().filter { it.isShown }.map { key ->
                                val rect = Rect()
                                check(key.getGlobalVisibleRect(rect) && rect.width() == key.width && rect.height() == key.height) { "Key clipped: ${key.id}" }
                                val screenRect = screenBounds(key, rect)
                                check(!Rect.intersects(fieldRect, screenRect)) { "Keyboard obscures editor" }
                                screenRect
                            }.toList()
                            check(keyRects.isNotEmpty())
                            val rootPosition = IntArray(2).also(view.rootView::getLocationOnScreen)
                            val inputPosition = IntArray(2).also(view::getLocationOnScreen)
                            val safeInsets = ViewCompat.getRootWindowInsets(view)?.getInsets(
                                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
                            )
                            geometry
                                .put("width_px", view.width).put("height_px", view.height)
                                .put("root_screen_position", JSONArray(rootPosition.toList()))
                                .put("input_screen_position", JSONArray(inputPosition.toList()))
                                .put("root_width_px", view.rootView.width)
                                .put("raw_safe_insets", safeInsets?.toString())
                                .put(
                                    "window_bounds",
                                    if (Build.VERSION.SDK_INT >= 30) {
                                        (service.getSystemService(Activity.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds.toShortString()
                                    } else {
                                        "Unavailable before API 30"
                                    },
                                )
                                .put("key_bounds", JSONArray(keyRects.map { it.toShortString() }))
                        }
                        phase("$theme visible key bounds and editor do not overlap")
                    }
                }
            }
            passed = true
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: T13 ${failure.stackTraceToString()}\n")
            report.put("failure", failure.stackTraceToString())
            // Preserve the actual failed hierarchy before cleanup detaches it. This is
            // diagnostic evidence only; it never changes the failed outcome or retries an action.
            fun JSONObject.putDiagnosticText(key: String, value: CharSequence?): JSONObject {
                val text = value?.toString()
                var end = minOf(text?.length ?: 0, 512)
                if (text != null && end > 0 && end < text.length && text[end - 1].isHighSurrogate()) end--
                put(key, text?.substring(0, end) ?: JSONObject.NULL)
                put("${key}_truncated", text != null && end < text.length)
                return this
            }
            runCatching {
                report.put(
                    "failure_ui",
                    main {
                        val view = input()
                        val sampled = view?.let { descendants(it).take(513).toList() }.orEmpty()
                        JSONObject().put("uptime_ms", SystemClock.uptimeMillis())
                            .putDiagnosticText("expected_choose_label", context.getString(R.string.a11y_t9_choose_syllable))
                            .put("input_present", view != null)
                            .put("views_truncated", sampled.size > 512)
                            .put(
                                "views",
                                JSONArray().apply {
                                    sampled.take(512).forEach { child ->
                                        val row = JSONObject()
                                        runCatching {
                                            val rect = Rect()
                                            val visible = child.getGlobalVisibleRect(rect)
                                            row.putDiagnosticText("class", child.javaClass.name).put("id", child.id)
                                                .putDiagnosticText("parent_class", child.parent?.javaClass?.name)
                                                .put("shown", child.isShown).put("enabled", child.isEnabled)
                                                .put("attached", child.isAttachedToWindow)
                                                .put("layout_requested", child.isLayoutRequested)
                                                .put("width", child.width).put("height", child.height)
                                                .put("global_visible", visible).putDiagnosticText("visible_bounds", rect.toShortString())
                                                .putDiagnosticText("delegate_class", ViewCompat.getAccessibilityDelegate(child)?.javaClass?.name)
                                                .putDiagnosticText("local_choose_label", child.context.getString(R.string.a11y_t9_choose_syllable))
                                            node(child) { info ->
                                                val actions = info.actionList
                                                row.putDiagnosticText("node_class", info.className)
                                                    .putDiagnosticText("text", info.text)
                                                    .putDiagnosticText("description", info.contentDescription)
                                                    .put("clickable", info.isClickable)
                                                    .put("action_count", actions.size)
                                                    .put("actions_truncated", actions.size > 32)
                                                    .put(
                                                        "actions",
                                                        JSONArray().apply {
                                                            actions.take(32).forEach { action ->
                                                                put(JSONObject().put("id", action.id).putDiagnosticText("label", action.label))
                                                            }
                                                        },
                                                    )
                                            }
                                            if (child is RecyclerView) {
                                                row.put("pending_adapter_updates", child.hasPendingAdapterUpdates())
                                                    .put("computing_layout", child.isComputingLayout)
                                                    .put("layout_suppressed", child.isLayoutSuppressed)
                                                    .put("child_count", child.childCount)
                                                    .put("item_count", child.adapter?.itemCount)
                                            }
                                        }.onFailure { row.putDiagnosticText("error", it.stackTraceToString()) }
                                        put(row)
                                    }
                                },
                            )
                    },
                )
            }.onFailure { report.putDiagnosticText("failure_ui_collection_error", it.stackTraceToString()) }
        } finally {
            val cleanupFailures = JSONArray()
            fun cleanup(block: () -> Unit) {
                try {
                    block()
                } catch (failure: Throwable) {
                    passed = false
                    cleanupFailures.put(failure.stackTraceToString())
                    result.putString("stream", result.getString("stream", "") + "FAIL: cleanup ${failure.stackTraceToString()}\n")
                }
            }
            cleanup { main { activity?.finish() } }
            cleanup {
                runBlocking {
                    withTimeout(90_000) {
                        session?.runOnReady {
                            clearComposition()
                            originalSchema?.let { check(selectSchema(it)) }
                            setRuntimeOption("ascii_mode", originalAscii)
                        }
                    }
                }
            }
            cleanup { runBlocking { withTimeout(90_000) { ThemeManager.selectTheme(originalTheme) } } }
            cleanup {
                main {
                    if (preferencesChanged) {
                        prefs.general.manualPrivacyMode.setValue(originalPrivacy)
                        val edit = store.edit()
                        if (manualPresent) edit.putString(manual.key, manualRaw) else edit.remove(manual.key)
                        check(edit.commit())
                    }
                }
            }
            cleanup { main { if (session != null) RimeDaemon.destroySession(javaClass.name) } }
            report.put("cleanup_failures", cleanupFailures)
        }
        report.put("passed", passed).put("checks", checks)
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "report.json").writeText(report.toString(2) + "\n")
        result.putBoolean("passed", passed)
        result.putString("t13_report", report.toString())
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
