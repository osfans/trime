/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.RimeApi
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.composition.T9DisambiguationView
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardView
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.keyboard.T9LayoutPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File

/** Runs on the current real emulator viewport. The caller also runs the unchanged T02 probe. */
class T9LayoutProbe(
    private val instrumentation: Instrumentation,
    private val service: TrimeInputMethodService,
    private val field: EditText,
) {
    private val prefs = AppPrefs.defaultInstance().keyboard

    private fun <T> main(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        var value: Result<T>? = null
        instrumentation.runOnMainSync { value = runCatching(block) }
        return checkNotNull(value).getOrThrow()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
    }

    private fun input() = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
        .filterIsInstance<InputView>().first { it.isShown }

    private fun window() = input().di.direct.instance<KeyboardWindow>()

    private fun keyboard() = descendants(input()).filterIsInstance<KeyboardView>().first { it.isShown }

    private suspend fun api(block: suspend RimeApi.() -> Unit) {
        var outcome: Result<Unit>? = null
        val job = main { service.postRimeJob { outcome = runCatching { block() } } }
        job.join()
        check(!job.isCancelled) { "Engine/editor job cancelled" }
        checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
        delay(150)
    }

    private suspend fun settle() {
        withTimeout(30_000) {
            while (!main { runCatching { window().currentKeyboard.isT9Layout && keyboard().width > 0 }.getOrDefault(false) }) delay(100)
        }
        delay(600)
    }

    private fun phase(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$text\n") })

    private fun keys() = keyboard().children.filterIsInstance<KeyView>().toList()

    private fun key(code: Int): KeyView {
        val index = window().currentKeyboard.keys.indexOfFirst { it.click?.code == code }
        check(index >= 0) { "Missing key $code" }
        return keys()[index]
    }

    private fun bounds(): List<Rect> = keys().map { view ->
        val point = IntArray(2)
        view.getLocationOnScreen(point)
        Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
    }

    private fun candidateTextVisible(label: String, candidateBar: View) {
        val recycler = candidateBar.findViewById<RecyclerView>(R.id.candidate_view)
        check(recycler != null && recycler.isShown && recycler.childCount > 0) { "$label: missing visible candidates" }
        fun screenBounds(view: View): Rect {
            val point = IntArray(2)
            view.getLocationOnScreen(point)
            return Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
        }
        val viewport = Rect()
        check(recycler.getLocalVisibleRect(viewport))
        val origin = screenBounds(recycler)
        viewport.offset(origin.left, origin.top)
        var checked = 0
        for (item in recycler.children) {
            val bounds = screenBounds(item)
            if (!Rect.intersects(viewport, bounds)) continue
            // A viewport can expose part of the next horizontal candidate. Vertical
            // clipping is always a failure, including on those partial candidates.
            check(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom) { "$label: candidate row clipped: $bounds in $viewport" }
            val fullyVisibleWidth = bounds.left >= viewport.left && bounds.right <= viewport.right
            val holder = recycler.getChildViewHolder(item) as CandidateViewHolder
            val labels = descendants(holder.ui.root).filterIsInstance<TextView>()
                .filter { it.isShown && it.text.isNotEmpty() }.toList()
            val expected = buildList {
                add(holder.text)
                if (AppPrefs.defaultInstance().candidates.showComments.getValue() && holder.comment.isNotEmpty()) add(holder.comment)
            }
            check(labels.map { it.text.toString() } == expected) { "$label: visible candidate labels differ from native text and comment preference: expected=$expected" }
            for (text in labels) {
                val textBounds = screenBounds(text)
                check(text.width > 0 && text.height > 0 && textBounds.top >= bounds.top && textBounds.bottom <= bounds.bottom) { "$label: candidate label ${text.text} vertically outside its row: $textBounds in $bounds" }
                if (fullyVisibleWidth) {
                    check(bounds.contains(textBounds)) { "$label: candidate label ${text.text} outside its row: $textBounds in $bounds" }
                }
                if (!Rect.intersects(viewport, textBounds)) continue
                val visible = Rect()
                val textVisible = text.getLocalVisibleRect(visible)
                // View reports content coordinates, including this TextView's scroll.
                // Compare clipping in the unscrolled viewport coordinates instead.
                visible.offset(-text.scrollX, -text.scrollY)
                check(textVisible && visible.top == 0 && visible.bottom == text.height) { "$label: candidate label ${text.text} vertically clipped: $visible of ${text.width}x${text.height}" }
                if (fullyVisibleWidth) {
                    check(visible == Rect(0, 0, text.width, text.height)) { "$label: candidate label ${text.text} clipped: $visible of ${text.width}x${text.height}" }
                }
            }
            if (fullyVisibleWidth) checked++
        }
        check(checked > 0) { "$label: no fully visible candidate checked" }
    }

    private suspend fun snapshot(label: String) {
        settle()
        val before = main {
            val boxes = bounds()
            val density = keyboard().resources.displayMetrics.density
            val display = keyboard().resources.displayMetrics
            val style = input().theme.style
            val expected = T9LayoutPolicy.widths(
                (display.widthPixels / density).toInt(),
                if (input().context.isLandscapeMode()) style.keyboardPaddingLand else style.keyboardPadding,
                keyboard().resources.configuration.fontScale,
                prefs.t9OneHand.getValue().ordinal,
                (display.heightPixels / density).toInt(),
            )
            val sidebar = descendants(input()).filterIsInstance<T9DisambiguationView>().single().sidebar
            check((sidebar.width > 0) == (expected.choices > 0)) { "$label: wrong pinyin-list orientation" }
            check(boxes.size == 17)
            boxes.forEach { box ->
                check(box.width() >= 48 * density - 1 && box.height() >= 48 * density - 1) { "$label: touch target $box at density $density" }
                check(box.left >= 0 && box.top >= 0 && box.right <= display.widthPixels && box.bottom <= display.heightPixels) { "$label: offscreen $box (${display.widthPixels}x${display.heightPixels})" }
            }
            boxes.forEachIndexed { i, box ->
                boxes.drop(i + 1).forEach { check(!Rect.intersects(box, it)) { "$label: overlapping keys $box $it" } }
            }
            for (row in 0..2) {
                for (column in 0..2) {
                    check(boxes[row * 4 + column].left == boxes[column].left)
                    check(boxes[row * 4 + column].top == boxes[row * 4].top)
                }
            }
            boxes
        }
        api { "64426".forEach { processKey(it.code) } }
        delay(250)
        check(main { bounds() } == before) { "$label: candidates moved the primary keys" }
        api {
            val ni = t9Cached.choices.first { it.spelling == "ni" && !it.completion }
            check(t9Action(t9Cached.revision, com.osfans.trime.core.T9Action.Lock, ni.start, ni.end, ni.spelling))
        }
        check(main { bounds() } == before) { "$label: locking moved the primary keys" }
        val bar = main { descendants(input()).filterIsInstance<T9DisambiguationView>().single() }
        check(main { bar.isShown && bar.height > 0 })
        main {
            val position = IntArray(2)
            bar.getLocationOnScreen(position)
            check(position[1] >= 0 && position[1] + bar.height <= before.minOf { it.top }) { "$label: composition controls overlap the keyboard" }
            val candidate = input().di.direct.instance<InputBarDelegate>().view
            val candidatePosition = IntArray(2)
            candidate.getLocationOnScreen(candidatePosition)
            val topInset = ViewCompat.getRootWindowInsets(input())?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            check(candidatePosition[1] >= topInset && candidatePosition[1] + candidate.height <= position[1]) { "$label: candidates overlap status or composition controls" }
            candidateTextVisible(label, candidate)
            check(!input().di.direct.instance<PreeditDelegate>().ui.visible) { "$label: duplicated floating preedit" }
            val labelView = descendants(bar).filterIsInstance<AppCompatTextView>().first()
            check(labelView.context.theme.resolveAttribute(androidx.appcompat.R.attr.windowActionBar, TypedValue(), true)) { "$label: pinyin label lacks an AppCompat theme" }
            if (bar.sidebar.width > 0) {
                bar.sidebar.getLocationOnScreen(position)
                val sidebar = Rect(position[0], position[1], position[0] + bar.sidebar.width, position[1] + bar.sidebar.height)
                before.forEach { check(!Rect.intersects(sidebar, it)) { "$label: pinyin sidebar overlaps $it" } }
                if (prefs.t9OneHand.getValue() == AppPrefs.Keyboard.OneHandMode.LEFT) {
                    check(sidebar.left >= before.maxOf { it.right }) { "$label: left-handed keys must stay nearest the left edge" }
                } else {
                    check(sidebar.right <= before.minOf { it.left })
                }
            }
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            try {
                File(instrumentation.targetContext.cacheDir, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        } ?: error("Screenshot unavailable")
        api { clearComposition() }
        check(main { bounds() } == before) { "$label: clearing candidates moved the primary keys" }
        phase("PASS layout $label: 17 targets >=48dp, stable 3x3 grid, no key overlap")
    }

    private fun touch(view: View, action: Int, down: Long, x: Float = view.width / 2f) = main {
        MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, view.height / 2f, 0).let {
            it.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                view.dispatchTouchEvent(it)
            } finally {
                it.recycle()
            }
        }
    }

    private suspend fun reset() {
        api { clearComposition() }
        main {
            field.setText("P:")
            field.setSelection(2)
        }
        delay(200)
    }

    private suspend fun tap(view: View) {
        val down = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, down)
        touch(view, MotionEvent.ACTION_UP, down)
        api { }
    }

    private suspend fun gestures() {
        reset()
        for (digit in 2..9) tap(main { key(KeyEvent.KEYCODE_0 + digit) })
        api { check(getRawInput() == "23456789") { "Ordinary taps no longer enter T9 digits" } }
        reset()
        for (digit in 2..9) {
            val view = main { key(KeyEvent.KEYCODE_0 + digit) }
            val down = SystemClock.uptimeMillis()
            touch(view, MotionEvent.ACTION_DOWN, down)
            delay(prefs.longPressTimeout.getValue().toLong() + 180)
            touch(view, MotionEvent.ACTION_UP, down)
            api { check(getRawInput().isEmpty()) { "Long press entered T9 encoding" } }
            check(main { field.text.toString() } == "P:" + (2..digit).joinToString("")) { "Long press $digit duplicated or selected a candidate" }
        }
        reset()
        api {
            "64426".forEach { processKey(it.code) }
            moveCursorPos(1)
        }
        val digit = main { key(KeyEvent.KEYCODE_2) }
        var down = SystemClock.uptimeMillis()
        touch(digit, MotionEvent.ACTION_DOWN, down)
        delay(prefs.longPressTimeout.getValue().toLong() + 180)
        touch(digit, MotionEvent.ACTION_UP, down)
        api { check(getRawInput().isEmpty()) }
        check(main { field.text.toString() } == "P:644262") { "Middle-caret literal digit lost or selected composition: ${main { field.text }}" }

        reset()
        val density = main { digit.resources.displayMetrics.density }
        down = SystemClock.uptimeMillis()
        touch(digit, MotionEvent.ACTION_DOWN, down)
        touch(digit, MotionEvent.ACTION_MOVE, down, digit.width / 2f + (prefs.swipeTravel.getValue() + 5) * density)
        touch(digit, MotionEvent.ACTION_MOVE, down)
        touch(digit, MotionEvent.ACTION_UP, down)
        api { check(getRawInput().isEmpty()) }
        check(main { field.text.toString() } == "P:") { "Swipe back to origin triggered click" }

        down = SystemClock.uptimeMillis()
        touch(digit, MotionEvent.ACTION_DOWN, down)
        touch(digit, MotionEvent.ACTION_CANCEL, down)
        delay(prefs.longPressTimeout.getValue().toLong() + 100)
        api { check(getRawInput().isEmpty()) }
        check(main { field.text.toString() } == "P:")

        api { "64426".forEach { processKey(it.code) } }
        val space = main { key(KeyEvent.KEYCODE_SPACE) }
        down = SystemClock.uptimeMillis()
        touch(space, MotionEvent.ACTION_DOWN, down)
        touch(space, MotionEvent.ACTION_MOVE, down, space.width / 2f - (prefs.swipeTravel.getValue() + 5) * density)
        touch(space, MotionEvent.ACTION_UP, down)
        api {
            check(getRawInput() == "64426") { "Space swipe committed or changed raw input" }
            check(compositionCached.cursorPos in 0..3) { "Space swipe did not move the caret by multiple steps" }
        }
        phase("PASS gestures: literal 2-9, pending composition, middle caret, swipe-back, cancel, multi-step space slide")
        reset()
        api { "64".forEach { processKey(it.code) } }
        tap(main { keys().first() })
        api { check(getRawInput() == "64'") { "Separator did not delimit the syllable: ${getRawInput()}" } }
        reset()
        tap(main { keys().last() })
        check(main { field.text.toString() } == "P:0") { "0 was treated as a candidate number" }
        tap(main { key(KeyEvent.KEYCODE_COMMA) })
        tap(main { key(KeyEvent.KEYCODE_PERIOD) })
        check(main { field.text.toString() } == "P:0，。") { "Punctuation differs from the T9 schema: ${main { field.text }}" }
        reset()
        api {
            processKey('6'.code)
            processKey(0xff08)
            check(t9Cached.canUndo)
        }
        tap(main { keys().last() })
        api { check(!t9Cached.canUndo && getRawInput().isEmpty()) { "Literal digit retained the previous composition undo history" } }
        reset()
        api {
            "64426".forEach { processKey(it.code) }
            val ni = t9Cached.choices.first { it.spelling == "ni" && !it.completion }
            check(t9Action(t9Cached.revision, com.osfans.trime.core.T9Action.Lock, ni.start, ni.end, ni.spelling))
            moveCursorPos(0)
        }
        tap(main { keys().last() })
        check(main { field.text.toString() } == "P:644260") { "Literal 0 lost locked input or exposed private encoding: ${main { field.text }}" }
        reset()
        api { "64".forEach { processKey(it.code) } }
        main { window().switchKeyboard("number") }
        main { }
        api { check(getRuntimeOption("ascii_mode")) }
        val prefix = main { field.text.toString() }
        val number = main {
            val code = if (window().currentKeyboard.keys.any { it.click?.code == KeyEvent.KEYCODE_NUMPAD_2 }) KeyEvent.KEYCODE_NUMPAD_2 else KeyEvent.KEYCODE_2
            key(code)
        }
        tap(number)
        check(main { field.text.toString() } == prefix + "2") { "Number page selected a candidate instead of inserting 2" }
        main { window().switchKeyboard(".default") }
        main { }
        api { check(!getRuntimeOption("ascii_mode")) }
        settle()
        reset()
        phase("PASS key semantics: separator, literal 0, comma/period, number-page round trip")
    }

    suspend fun run(geometryOnly: Boolean = false) {
        val originalTheme = ThemeManager.prefs.selectedTheme.getValue()
        val originalHand = prefs.t9OneHand.getValue()
        val originalHeight = prefs.t9KeyHeight.getValue()
        val originalTravel = prefs.swipeTravel.getValue()
        try {
            prefs.swipeTravel.setValue(60)
            for (theme in listOf("trime", "tongwenfeng.trime")) {
                val selected = ThemeManager.selectTheme(theme)
                check(selected == theme) { "Expected theme $theme, loaded $selected" }
                prefs.t9OneHand.setValue(AppPrefs.Keyboard.OneHandMode.OFF)
                prefs.t9KeyHeight.setValue(0)
                settle()
                val config = main { keyboard().resources.configuration }
                val prefix = "t03-$theme-${config.screenWidthDp}x${config.screenHeightDp}-font${config.fontScale}"
                snapshot(prefix)
                if (!geometryOnly) gestures()
                for (hand in listOf(AppPrefs.Keyboard.OneHandMode.LEFT, AppPrefs.Keyboard.OneHandMode.RIGHT)) {
                    prefs.t9OneHand.setValue(hand)
                    prefs.t9KeyHeight.setValue(48)
                    snapshot("$prefix-${hand.name.lowercase()}")
                }
                prefs.t9KeyHeight.setValue(80)
                snapshot("$prefix-height80")
            }
            if (geometryOnly) return
            reset()
            api {
                check(selectSchema("luna_pinyin_simp"))
                setRuntimeOption("ascii_mode", false)
            }
            withTimeout(30_000) {
                while (!main { !window().currentKeyboard.isT9Layout }) delay(100)
            }
            for (letter in "nihao") tap(main { key(KeyEvent.KEYCODE_A + (letter - 'a')) })
            check(main { input().di.direct.instance<PreeditDelegate>().ui.visible }) { "Full-pinyin floating preedit was hidden" }
            api {
                check(getRawInput() == "nihao") { "Full-pinyin taps changed after the gesture update" }
                val candidate = getCandidates(0, 100).indexOfFirst { it.text == "你好" }
                check(candidate >= 0 && selectCandidate(candidate, true))
            }
            check(main { field.text.toString() } == "P:你好") { "Full-pinyin candidate commit changed" }
            phase("PASS full pinyin: real letter taps and Hanzi candidate commit")
        } finally {
            prefs.t9OneHand.setValue(originalHand)
            prefs.t9KeyHeight.setValue(originalHeight)
            prefs.swipeTravel.setValue(originalTravel)
            ThemeManager.selectTheme(originalTheme)
        }
    }
}
