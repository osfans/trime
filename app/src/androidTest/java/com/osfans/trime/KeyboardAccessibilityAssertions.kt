/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.children
import com.osfans.trime.core.RimeApi
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.ime.keyboard.KeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardView
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.kodein.di.direct
import org.kodein.di.instance

/** Real KeyView nodes and original callbacks; the final space uses the production IME listener. */
object KeyboardAccessibilityAssertions {
    private data class EditorRestorationState(
        val token: Long,
        val textLength: Int,
        val selectionStart: Int,
        val selectionEnd: Int,
        val focused: Boolean,
        val inputType: Int?,
        val imeOptions: Int?,
        val enterLabel: String?,
    )

    data class Outcome(
        val checks: Int,
        val fixturePresses: Int,
        val fixtureActions: Int,
        val fixtureRepeats: Int,
        val actualEditorCommit: String,
        val stateRestored: Boolean,
        val realTalkBackServiceVerified: Boolean = false,
        val scrollingKeys: List<Map<String, Any>> = emptyList(),
    )

    private class Recorder : KeyboardActionListener {
        var presses = 0
        var repeats = 0
        val actions = mutableListOf<KeyAction>()
        override fun onPress(keyEventCode: Int) {
            presses++
        }
        override fun onAction(action: KeyAction) {
            actions.add(action)
        }
        override fun onRepeat(action: KeyAction): kotlinx.coroutines.Job? {
            repeats++
            return null
        }
        override fun onKey(keyEventCode: Int, metaState: Int) = Unit
        override fun onText(input: String) = Unit
    }

    private fun <T> main(instrumentation: Instrumentation, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    private fun input(service: TrimeInputMethodService): InputView = descendants(checkNotNull(service.window.window).decorView)
        .filterIsInstance<InputView>().first { it.isShown }

    private suspend fun <T> engine(instrumentation: Instrumentation, service: TrimeInputMethodService, block: suspend RimeApi.() -> T): T {
        var result: Result<T>? = null
        val job = main(instrumentation) { service.postRimeJob { result = runCatching { block() } } }
        job.join()
        check(!job.isCancelled) { "Accessibility probe lost its original editor job" }
        return checkNotNull(result).getOrThrow()
    }

    @Suppress("DEPRECATION") // AccessibilityNodeInfo was pooled on API21; recycle only on those releases.
    private fun <T> node(view: View, block: (AccessibilityNodeInfo) -> T): T {
        val info = view.createAccessibilityNodeInfo()
        return try {
            block(info)
        } finally {
            if (android.os.Build.VERSION.SDK_INT < 33) info.recycle()
        }
    }

    private fun event(view: View, action: Int, down: Long = SystemClock.uptimeMillis(), hover: Boolean = false) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, view.width / 2f, view.height / 2f, 0)
        motion.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            if (hover) view.dispatchGenericMotionEvent(motion) else check(view.dispatchTouchEvent(motion))
        } finally {
            motion.recycle()
        }
    }

    suspend fun verify(instrumentation: Instrumentation, service: TrimeInputMethodService, field: EditText): Outcome {
        check(main(instrumentation) { field.text.isEmpty() && field.hasFocus() }) { "Probe needs its owned empty editor" }
        val original = engine(instrumentation, service) { selectedSchemaId() to getRuntimeOption("ascii_mode") }
        val originalEditor = main(instrumentation) { checkNotNull(service.currentInputEditorInfo) }
        var host: FrameLayout? = null
        var enterDelegate: EnterKeyDisplayDelegate? = null
        var savedEnterLabel: String? = null
        val recorder = Recorder()
        var checks = 0
        var actualCommit = ""
        val scrollingKeys = mutableListOf<Map<String, Any>>()
        try {
            engine(instrumentation, service) {
                clearComposition()
                setRuntimeOption("ascii_mode", true)
            }
            delay(150)
            main(instrumentation) {
                val input = input(service)
                val liveTheme = input.di.direct.instance<Theme>()
                val theme = liveTheme.copy(
                    presetKeys = liveTheme.presetKeys + mapOf(
                        "ownedT13Shift" to PresetKey(send = "Shift_L", label = "ic@arrow-up-bold", shiftLock = "long"),
                        "ownedT13Repeat" to PresetKey(send = "BackSpace", label = "ic@backspace", repeatable = true),
                    ),
                )
                val config = TextKeyboard(
                    columns = 5,
                    width = 20f,
                    height = 48f,
                    keys = listOf(
                        TextKeyboard.TextKey(width = 20f, click = KeyActionToken.Inline(commit = "x", label = "x"), swipeUp = KeyActionToken.Inline(commit = "u", label = "u")),
                        TextKeyboard.TextKey(width = 20f, click = KeyActionToken.Inline(commit = "a", label = "a"), longClick = KeyActionToken.Inline(commit = "l", label = "l")),
                        TextKeyboard.TextKey(width = 20f, click = KeyActionToken.Plain("ownedT13Repeat")),
                        TextKeyboard.TextKey(width = 20f, click = KeyActionToken.Plain("ownedT13Shift"), longClick = KeyActionToken.Plain("ownedT13Shift")),
                        TextKeyboard.TextKey(width = 20f, click = KeyActionToken.Plain("Return")),
                    ),
                )
                val width = input.width.coerceAtLeast(320)
                val keyboard = Keyboard(input.context, theme, width, config)
                val display = input.di.direct.instance<EnterKeyDisplayDelegate>()
                enterDelegate = display
                savedEnterLabel = display.keyLabel
                val view = KeyboardView(input.context, theme, keyboard, input.di.direct.instance<PopupDelegate>(), service, recorder, display)
                val container = FrameLayout(input.context).apply {
                    tag = "owned-t13-keyboard-accessibility"
                    addView(view, FrameLayout.LayoutParams(width, keyboard.height))
                }
                host = container
                input.addView(container, ViewGroup.LayoutParams(width, keyboard.height))
                container.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(keyboard.height, View.MeasureSpec.EXACTLY))
                val scrollDistance = 13
                // API 21 rounds negative window coordinates with (int)(value + .5f).
                // Keep this visible-key scroll fixture positive on both sides of the
                // exact displacement check, rather than crossing that platform boundary.
                val fixtureTop = scrollDistance + 1
                container.layout(0, fixtureTop, width, keyboard.height + fixtureTop)
                val keys = view.children.filterIsInstance<KeyView>().toList()
                check(keys.size == 5 && keys.all { it.isAttachedToWindow && it.isShown })
                keys.forEach { key ->
                    node(key) { info ->
                        check(info.className == "android.widget.Button" && info.contentDescription.isNotEmpty())
                        check(info.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                    }
                }
                checks++

                val primary = keys[0]
                val originalBounds = Rect(primary.bounds)
                check(originalBounds.top >= scrollDistance) { "Scroll fixture starts outside its positive window range: $originalBounds" }
                try {
                    container.scrollTo(0, scrollDistance)
                    val expectedBounds = Rect(originalBounds).apply { offset(0, -scrollDistance) }
                    val actualBounds = Rect(primary.bounds)
                    instrumentation.sendStatus(
                        0,
                        Bundle().apply {
                            putString("keyboard_popup_scroll_original", originalBounds.toShortString())
                            putString("keyboard_popup_scroll_expected", expectedBounds.toShortString())
                            putString("keyboard_popup_scroll_actual", actualBounds.toShortString())
                            putInt("keyboard_popup_scroll_distance", container.scrollY)
                        },
                    )
                    check(actualBounds == expectedBounds) { "Scrolled key popup coordinates differ: original=$originalBounds expected=$expectedBounds actual=$actualBounds scroll=${container.scrollY}" }
                } finally {
                    container.scrollTo(0, 0)
                }
                check(primary.bounds == originalBounds) { "Key popup coordinates did not return after scrolling" }
                checks++
                val beforeHover = recorder.presses to recorder.actions.size
                event(primary, MotionEvent.ACTION_HOVER_ENTER, hover = true)
                event(primary, MotionEvent.ACTION_HOVER_MOVE, hover = true)
                event(primary, MotionEvent.ACTION_HOVER_EXIT, hover = true)
                check(beforeHover == (recorder.presses to recorder.actions.size)) { "Hover exploration entered text" }
                checks++
                check(primary.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                check(recorder.presses == 1 && recorder.actions.map { it.commit } == listOf("x") && !primary.isPressed)
                checks++
                val down = SystemClock.uptimeMillis()
                event(primary, MotionEvent.ACTION_DOWN, down)
                check(!primary.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                check(!keys[1].performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)) { "Another held key turned accessibility activation into a combo" }
                check(recorder.actions.size == 1)
                event(primary, MotionEvent.ACTION_UP, down)
                check(recorder.presses == 2 && recorder.actions.map { it.commit } == listOf("x", "x")) { "Physical release double-dispatched or lost its action" }
                checks++
                val cancelDown = SystemClock.uptimeMillis()
                event(primary, MotionEvent.ACTION_DOWN, cancelDown)
                event(primary, MotionEvent.ACTION_CANCEL, cancelDown)
                check(recorder.actions.size == 2 && !primary.isPressed)
                checks++
                check(primary.performAccessibilityAction(R.id.t13_key_action_swipe_up, null))
                check(recorder.actions.last().commit == "u" && recorder.actions.size == 3)
                check(!primary.performAccessibilityAction(R.id.t13_key_action_swipe_down, null) && recorder.actions.size == 3)
                checks++
                check(keys[1].performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
                check(recorder.actions.last().commit == "l" && recorder.actions.size == 4 && !keys[1].isPressed)
                checks++
                check(keys[2].performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                check(recorder.actions.size == 5 && recorder.actions.last().code == KeyEvent.KEYCODE_DEL && recorder.repeats == 0)
                check(!keys[2].performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
                checks++
                primary.isEnabled = false
                node(primary) { check(!it.isClickable) }
                check(!primary.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null) && recorder.actions.size == 5)
                primary.isEnabled = true
                checks++

                val shift = keys[3]
                node(shift) { check(it.isCheckable && !it.isCheckedForTest()) }
                check(shift.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                node(shift) { check(it.isCheckable && it.isCheckedForTest()) }
                check(shift.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                node(shift) { check(!it.isCheckedForTest()) }
                check(shift.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null))
                node(shift) { check(it.isCheckedForTest()) }
                check(view.isCapsOn && recorder.actions.size == 5 && recorder.repeats == 0)
                checks++

                for (action in listOf(EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_SEARCH)) {
                    display.updateLabelOnEditorInfo(EditorInfo().apply { imeOptions = action })
                    view.updateEnterLabel()
                    node(keys[4]) { check(it.contentDescription.toString().startsWith(display.keyLabel)) }
                    checks++
                }
                display.updateLabelOnEditorInfo(originalEditor)
                check(display.keyLabel == savedEnterLabel)

                // Attached non-T9 keys reproduce narrow symbol cells, using actual fallback fonts.
                // A tablet must not make this deliberately narrow fixture wide enough
                // to display the complete caption without exercising ellipsis.
                val narrowWidth = minOf(width, (320f * input.resources.displayMetrics.density).toInt().coerceAtLeast(1))
                val longCaption = "朙月拼音九键配置A\u0301\uD83D\uDC69\u200D\uD83D\uDCBB"
                val captions = listOf(longCaption, "返回", "符号", "\uD83D\uDC69\u200D\uD83D\uDCBB", "A\u0301", "\uD840\uDC00", "ic@backspace", "7", "8", "9")
                val narrowConfig = TextKeyboard(
                    columns = 10, width = 10f, height = 50f,
                    keyTextOffsetX = 999f, keyTextOffsetY = -999f,
                    keySymbolOffsetX = -999f, keySymbolOffsetY = 999f,
                    keyHintOffsetX = 999f, keyHintOffsetY = -999f,
                    keys = captions.map { caption ->
                        TextKeyboard.TextKey(
                            width = 10f,
                            keyTextSize = 28f,
                            symbolTextSize = 18f,
                            labelSymbol = "上",
                            hint = "下",
                            click = KeyActionToken.Inline(commit = "x", label = caption),
                        )
                    },
                )
                val narrowKeyboard = Keyboard(input.context, theme, narrowWidth, narrowConfig)
                val narrow = KeyboardView(input.context, theme, narrowKeyboard, input.di.direct.instance<PopupDelegate>(), service, recorder, display)
                container.addView(narrow, FrameLayout.LayoutParams(narrowWidth, narrowKeyboard.height))
                try {
                    narrow.measure(View.MeasureSpec.makeMeasureSpec(narrowWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(narrowKeyboard.height, View.MeasureSpec.EXACTLY))
                    narrow.layout(0, 0, narrowWidth, narrowKeyboard.height)
                    val narrowKeys = narrow.children.filterIsInstance<KeyView>().toList()
                    check(narrowKeys.size == captions.size && narrowKeys.all { it.isAttachedToWindow && it.isShown })
                    for (narrowKey in narrowKeys) {
                        val plans = narrowKey.visualContentLayout()
                        check(plans.size == 3)
                        for (plan in plans) {
                            val area = plan.area
                            val drawn = plan.bounds
                            check(drawn.left >= area.left && drawn.top >= area.top && drawn.right <= area.right && drawn.bottom <= area.bottom)
                            check(plan.text.lines.size <= 2)
                            if (plan.icon) continue
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = plan.text.size
                                typeface = if (plan.isMain) theme.fonts.key else theme.fonts.symbol
                            }
                            val ink = Rect()
                            for ((lineIndex, line) in plan.text.lines.withIndex()) {
                                paint.getTextBounds(line, 0, line.length, ink)
                                val originX = (drawn.left + drawn.right - paint.measureText(line)) / 2f
                                val baseline = drawn.top - plan.text.top + lineIndex * plan.text.lineHeight
                                check(originX + ink.left >= area.left - 0.5f && originX + ink.right <= area.right + 0.5f)
                                check(baseline + ink.top >= area.top - 0.5f && baseline + ink.bottom <= area.bottom + 0.5f)
                            }
                        }
                        val mainArea = plans.first { it.isMain }.area
                        val hints = plans.filterNot { it.isMain }.map { it.area }
                        check(hints.all { it.bottom <= mainArea.top || it.top >= mainArea.bottom })
                    }
                    node(narrowKeys.first()) { check(it.contentDescription.toString().contains(longCaption)) }
                    val captionPlan = narrowKeys.first().visualContentLayout().first { it.isMain }
                    check(captionPlan.text.lines.joinToString("") != longCaption) {
                        "Narrow fixture did not ellipsize: fixtureWidth=$narrowWidth, keyWidth=${narrowKeys.first().width}, area=${captionPlan.area}, text=${captionPlan.text}"
                    }
                    checks++
                } finally {
                    container.removeView(narrow)
                }

                // Actual child positions must support ancestor scrolling and parent hit testing.
                // The app disables RTL. Enable it only on this independent context's copy
                // so the RTL fixture actually resolves RTL without changing the application.
                val fixtureInfo = ApplicationInfo(input.context.applicationInfo).apply { flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL }
                val fixtureContext = object : ContextWrapper(input.context) {
                    override fun getApplicationInfo(): ApplicationInfo = fixtureInfo
                }
                for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                    val scrollRecorder = Recorder()
                    val scrollConfig = TextKeyboard(
                        columns = 2,
                        width = 50f,
                        height = 48f,
                        keyboardHeight = 144,
                        keyboardHeightLand = 144,
                        keys = (0..5).map { index -> TextKeyboard.TextKey(width = 50f, click = KeyActionToken.Inline(commit = "owned-scroll-$index", label = "key $index")) },
                    )
                    val model = Keyboard(fixtureContext, theme, width, scrollConfig)
                    val scrollKeyboard = KeyboardView(fixtureContext, theme, model, input.di.direct.instance<PopupDelegate>(), service, scrollRecorder, display)
                    val viewportHeight = minOf(model.height - model.keys.last().height / 3, input.height)
                    check(viewportHeight >= model.keys.last().height && viewportHeight < model.height)
                    val viewport = ScrollView(fixtureContext).apply {
                        layoutDirection = direction
                        isFocusable = false
                        isFillViewport = false
                        addView(scrollKeyboard, FrameLayout.LayoutParams(width, model.height))
                    }
                    input.addView(viewport, ViewGroup.LayoutParams(width, viewportHeight))
                    try {
                        viewport.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY))
                        viewport.layout(0, 0, width, viewportHeight)
                        check(viewport.layoutDirection == direction && scrollKeyboard.layoutDirection == direction) { "Fixture direction $direction resolved as ${viewport.layoutDirection}/${scrollKeyboard.layoutDirection}" }
                        val actualKeys = scrollKeyboard.children.filterIsInstance<KeyView>().toList()
                        check(actualKeys.size == 6 && model.keys.map { it.row }.distinct().size == 3)
                        val geometry = actualKeys.mapIndexed { index, actual ->
                            val expected = model.keys[index]
                            check(actual.isAttachedToWindow && actual.isShown && actual.translationX == 0f && actual.translationY == 0f)
                            check(
                                actual.left == expected.x - expected.extraWidthLeft && actual.top == expected.y &&
                                    actual.width == expected.width + expected.extraWidthLeft + expected.extraWidthRight && actual.height == expected.height,
                            ) {
                                "Key geometry direction=$direction index=$index actual=${actual.left},${actual.top},${actual.width},${actual.height} model=${expected.x},${expected.y},${expected.width},${expected.height} extra=${expected.extraWidthLeft},${expected.extraWidthRight}"
                            }
                            listOf(actual.id, actual.left, actual.top, actual.width, actual.height)
                        }
                        checks++
                        val lower = actualKeys.last()
                        fun fullyVisible(): Boolean {
                            val rect = Rect()
                            return lower.getGlobalVisibleRect(rect) && rect.width() == lower.width && rect.height() == lower.height
                        }
                        fun scrollDetails(): String = "direction=$direction scroll=${viewport.scrollY} viewport=${viewport.width}x${viewport.height} key=${lower.left},${lower.top},${lower.width},${lower.height} visible=${Rect().also { lower.getGlobalVisibleRect(it) }} presses=${scrollRecorder.presses} commits=${scrollRecorder.actions.map { it.commit }}"
                        check(viewport.scrollY == 0 && !fullyVisible()) { "Scroll fixture must begin with the lower key clipped" }
                        val requested = lower.requestRectangleOnScreen(Rect(0, 0, lower.width, lower.height), true)
                        val requestedScroll = viewport.scrollY
                        check(requested && requestedScroll > 0 && fullyVisible() && scrollRecorder.presses == 0 && scrollRecorder.actions.isEmpty()) { "Original key request returned=$requested: ${scrollDetails()}" }
                        checks++
                        var showOnScreenScroll = -1
                        if (Build.VERSION.SDK_INT >= 23) {
                            viewport.scrollTo(0, 0)
                            check(!fullyVisible())
                            val show = AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                            node(lower) { info -> check(info.actionList.any { it.id == show }) }
                            check(lower.performAccessibilityAction(show, null)) { "SHOW_ON_SCREEN returned false: ${scrollDetails()}" }
                            showOnScreenScroll = viewport.scrollY
                            check(showOnScreenScroll > 0 && fullyVisible() && scrollRecorder.presses == 0 && scrollRecorder.actions.isEmpty()) { "SHOW_ON_SCREEN result: ${scrollDetails()}" }
                            checks++
                        }
                        val keyPosition = IntArray(2).also(lower::getLocationOnScreen)
                        val parentPosition = IntArray(2).also(viewport::getLocationOnScreen)
                        val touchX = keyPosition[0] - parentPosition[0] + lower.width / 2f
                        val touchY = keyPosition[1] - parentPosition[1] + lower.height / 2f
                        check(touchX in 0f..viewport.width.toFloat() && touchY in 0f..viewport.height.toFloat())
                        val touchDown = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val motion = MotionEvent.obtain(touchDown, SystemClock.uptimeMillis(), action, touchX, touchY, 0)
                            try {
                                motion.source = InputDevice.SOURCE_TOUCHSCREEN
                                check(viewport.dispatchTouchEvent(motion))
                            } finally {
                                motion.recycle()
                            }
                        }
                        check(scrollRecorder.presses == 1 && scrollRecorder.actions.map { it.commit } == listOf("owned-scroll-5") && scrollRecorder.repeats == 0 && !lower.isPressed) { "Parent touch at $touchX,$touchY: ${scrollDetails()}" }
                        checks++
                        scrollingKeys += mapOf(
                            "fixture_only_rtl_support" to true, "application_rtl_support_changed" to false,
                            "layout_direction" to direction, "resolved_layout_direction" to scrollKeyboard.layoutDirection, "key_geometry" to geometry,
                            "request_scroll_y" to requestedScroll, "show_on_screen_tested" to (Build.VERSION.SDK_INT >= 23),
                            "show_on_screen_scroll_y" to showOnScreenScroll, "parent_touch_xy" to listOf(touchX, touchY),
                            "presses" to scrollRecorder.presses, "commits" to scrollRecorder.actions.map { it.commit }, "repeats" to scrollRecorder.repeats,
                        )
                    } finally {
                        input.removeView(viewport)
                    }
                }
            }
            main(instrumentation) {
                host?.let { (it.parent as ViewGroup).removeView(it) }
                check(host?.parent == null)
                host = null
            }
            // This final action goes through the installed keyboard's CommonKeyboardActionListener,
            // the existing serial Rime worker and this original owned editor connection.
            main(instrumentation) {
                val input = input(service)
                val window = input.di.direct.instance<KeyboardWindow>()
                val keyboard = descendants(input).filterIsInstance<KeyboardView>().first { it.isShown }
                val space = window.currentKeyboard.keys.indexOfFirst { it.click?.code == KeyEvent.KEYCODE_SPACE }
                check(space >= 0)
                val key = keyboard.getChildAt(space) as KeyView
                check(key.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            }
            withTimeout(5000) {
                while (!main(instrumentation) { field.text.toString() == " " }) delay(20)
            }
            engine(instrumentation, service) { check(getRawInput().isEmpty()) }
            delay(100)
            check(main(instrumentation) { field.text.toString() == " " }) { "Production accessibility key committed twice" }
            actualCommit = " "
            checks++
        } finally {
            val beforeNative = engine(instrumentation, service) { selectedSchemaId() to getRuntimeOption("ascii_mode") }
            fun editorState(): EditorRestorationState = main(instrumentation) {
                EditorRestorationState(
                    service.editorToken,
                    field.length(),
                    field.selectionStart,
                    field.selectionEnd,
                    field.hasFocus(),
                    service.currentInputEditorInfo?.inputType,
                    service.currentInputEditorInfo?.imeOptions,
                    enterDelegate?.keyLabel,
                )
            }
            val beforeEditor = editorState()
            main(instrumentation) {
                host?.let { (it.parent as? ViewGroup)?.removeView(it) }
                enterDelegate?.updateLabelOnEditorInfo(originalEditor)
                // Clear this owned fictional buffer without TextView.setText restarting the IME.
                field.editableText.clear()
                field.setSelection(0)
            }
            engine(instrumentation, service) {
                clearComposition()
                if (selectedSchemaId() != original.first) check(selectSchema(original.first))
                setRuntimeOption("ascii_mode", original.second)
            }
            val afterNative = engine(instrumentation, service) { selectedSchemaId() to getRuntimeOption("ascii_mode") }
            val afterEditor = editorState()
            val restoration = "expected=$original; beforeNative=$beforeNative; afterNative=$afterNative; beforeEditor=$beforeEditor; afterEditor=$afterEditor"
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "T13 keyboard restoration: $restoration\n") })
            check(afterNative == original) { "Keyboard native restoration differs: $restoration" }
            check(afterEditor.textLength == 0 && afterEditor.enterLabel == savedEnterLabel && afterEditor.token == beforeEditor.token) {
                "Keyboard editor restoration differs: $restoration"
            }
        }
        return Outcome(checks, recorder.presses, recorder.actions.size, recorder.repeats, actualCommit, stateRestored = true, scrollingKeys = scrollingKeys)
    }
}
