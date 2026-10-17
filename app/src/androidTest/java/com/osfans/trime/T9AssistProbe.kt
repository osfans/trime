/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.T9Action
import com.osfans.trime.core.T9StateProto
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyCode
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.keyboard.Keyboard
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Uses the read-only emulator's disposable app layer; restores every changed preference. */
class T9AssistProbe(
    private val instrumentation: Instrumentation,
    private val service: TrimeInputMethodService,
    private val editor: EditText,
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

    private suspend fun api(block: suspend RimeApi.() -> Unit) {
        var outcome: Result<Unit>? = null
        val job = main { service.postRimeJob { outcome = runCatching { block() } } }
        job.join()
        check(!job.isCancelled) { "Engine/editor job cancelled" }
        checkNotNull(outcome) { "Editor changed before operation ran" }.getOrThrow()
        delay(200)
    }

    private fun bounds() = descendants(input()).filterIsInstance<KeyView>().filter { it.isShown }.map { key ->
        val point = IntArray(2)
        key.getLocationOnScreen(point)
        Rect(point[0], point[1], point[0] + key.width, point[1] + key.height)
    }.toList()

    private fun phase(text: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "T05: $text\n") })

    private suspend fun measureExactCorpus() {
        val pinyin = listOf(
            "ni", "li", "zao", "zhao", "ca", "cha", "sa", "sha", "zen", "zeng", "lin", "ling",
            "nv er", "nue dai", "lve duo", "xi'an", "xian", "ni hao", "bei jing", "shang hai",
            "chong qing", "wo men ming tian jian", "qing wen di tie zhan zai na li", "ban ben kong zhi",
        )
        val digits = "22233344455566677778889999"
        val inputs = pinyin.map { spelling -> spelling.filter { it != ' ' }.map { if (it == '\'') it else digits[it - 'a'] }.joinToString("") }
        val baseline = mutableListOf<Array<CandidateProto>>()
        val groups = JSONArray()
        for (enabled in listOf(false, true)) {
            // Keep option-change callbacks from prebuilding an index on the last corpus item.
            api { clearComposition() }
            prefs.t9AssistOptions.forEach { it.setValue(enabled) }
            delay(500)
            val records = JSONArray()
            for ((index, keys) in inputs.withIndex()) {
                api {
                    clearComposition()
                    val firstBegin = SystemClock.elapsedRealtimeNanos()
                    processKey(keys.first().code)
                    val firstElapsed = (SystemClock.elapsedRealtimeNanos() - firstBegin) / 1_000_000.0
                    if (enabled && index == 0) check(firstElapsed < 500) { "First all-rules key stalled: $firstElapsed ms" }
                    keys.drop(1).dropLast(1).forEach { processKey(it.code) }
                    val begin = SystemClock.elapsedRealtimeNanos()
                    processKey(keys.last().code)
                    val candidates = getCandidates(0, 80)
                    val elapsed = (SystemClock.elapsedRealtimeNanos() - begin) / 1_000_000.0
                    if (enabled) {
                        check(candidates.contentEquals(baseline[index])) { "Exact candidate regression: ${pinyin[index]}" }
                    } else {
                        baseline.add(candidates)
                    }
                    records.put(
                        JSONObject().put("pinyin", pinyin[index]).put("input", keys)
                            .put("first_key_ms", firstElapsed)
                            .put("last_key_and_80_candidates_ms", elapsed)
                            .put("suggestions", t9Cached.choices.count { it.sources != 0 })
                            .put("candidates", JSONArray(candidates.map { it.text })),
                    )
                }
                phase("exact all_rules=$enabled ${index + 1}/${inputs.size}")
            }
            val memory = Debug.MemoryInfo()
            Debug.getMemoryInfo(memory)
            groups.put(JSONObject().put("all_rules", enabled).put("records", records).put("app_pss_kib", memory.totalPss))
        }
        val recovery = JSONArray()
        val hanzi = mapOf(
            "ni" to "你", "li" to "李", "zao" to "早", "zhao" to "找", "ca" to "擦", "cha" to "茶",
            "sa" to "撒", "sha" to "沙", "zen" to "怎", "zeng" to "增", "lin" to "林", "ling" to "零",
            "hao" to "好", "ning" to "宁",
        )
        suspend fun RimeApi.resolve(keys: String, target: String): Pair<Boolean, Int> {
            val choice = t9Cached.choices.firstOrNull { it.start == 0 && it.end == keys.length && it.spelling == target }
                ?: return false to 0
            check(t9Action(t9Cached.revision, T9Action.Lock, choice.start, choice.end, choice.spelling))
            val found = getCandidates(0, 80).any { it.text == hanzi.getValue(target) }
            check(getRawInput() == keys)
            check(t9Action(t9Cached.revision, T9Action.Undo))
            return found to choice.sources
        }
        val mistakes = listOf(
            Triple("54", "ni", 0), Triple("64", "li", 0), Triple("926", "zhao", 1), Triple("9426", "zao", 1),
            Triple("22", "cha", 2), Triple("242", "ca", 2), Triple("72", "sha", 3), Triple("742", "sa", 3),
            Triple("936", "zeng", 4), Triple("9364", "zen", 4), Triple("546", "ling", 5), Triple("5464", "lin", 5),
            Triple("54", "ni", 6), Triple("6", "ni", 7), Triple("644", "ni", 8),
            Triple("526", "hao", 6), Triple("46", "hao", 7), Triple("4226", "hao", 8),
            Triple("826", "zao", 6), Triple("96", "zao", 7), Triple("9226", "zao", 8),
            Triple("6465", "ning", 6), Triple("644", "ning", 7), Triple("64664", "ning", 8),
        )
        prefs.t9AssistOptions.forEach { it.setValue(false) }
        for ((keys, target, rule) in mistakes) {
            var before = false
            api {
                clearComposition()
                keys.forEach { processKey(it.code) }
                before = resolve(keys, target).first
            }
            prefs.t9AssistOptions[rule].setValue(true)
            api {
                refreshT9Options()
                val resolved = resolve(keys, target)
                recovery.put(
                    JSONObject().put("input", keys).put("target", target).put("rule", rule)
                        .put("hanzi", hanzi.getValue(target)).put("before", before).put("after", resolved.first).put("sources", resolved.second),
                )
            }
            phase("recovery rule=$rule $keys -> $target")
            prefs.t9AssistOptions[rule].setValue(false)
        }
        api { clearComposition() }
        File(instrumentation.targetContext.cacheDir, "t05-metrics.json").writeText(
            JSONObject().put("version", BuildConfig.VERSION_NAME).put("version_code", BuildConfig.VERSION_CODE)
                .put("scope", "24 exact inputs; first key, final key plus first 80 candidates; sampled whole-app PSS; debug emulator")
                .put("groups", groups).put("recovery", recovery).toString(2),
        )
        prefs.t9AssistOptions.forEach { it.setValue(false) }
    }

    suspend fun run(measure: Boolean = true) {
        val saved = prefs.t9AssistOptions.map { it.getValue() }
        val theme = ThemeManager.prefs.selectedTheme.getValue()
        var simplified = false
        api {
            simplified = getRuntimeOption("simplification")
            setRuntimeOption("simplification", true)
        }
        try {
            main {
                val theme = ThemeManager.activeTheme
                val keyboard = Keyboard(instrumentation.targetContext, theme, 720, theme.presetKeyboards["luna_pinyin_t9"])
                for (macro in listOf("(){Left}", "[]{Left}", "{}{Left}", "{Control+a}")) {
                    val action = KeyAction(KeyActionToken.Plain(macro), theme.presetKeys)
                    check(action.code == KeyEvent.KEYCODE_UNKNOWN && action.getText(keyboard) == macro)
                }
                val plus = KeyCode.parse("+")
                check(plus.first != 0 && plus.second == 0)
                check(KeyCode.parse("Control++") == (plus.first to KeyEvent.META_CTRL_ON))
                check(KeyCode.parse("A") == (KeyEvent.KEYCODE_A to KeyEvent.META_SHIFT_ON))
                for (literal in listOf("你好", "abc+xyz")) {
                    check(KeyAction(KeyActionToken.Plain(literal), theme.presetKeys).getText(keyboard) == literal)
                }
            }
            phase("literal key, chord and text macro contracts passed")
            prefs.t9AssistOptions.forEach { it.setValue(false) }
            check(prefs.t9AssistMask() == 0)
            prefs.t9AssistOptions.forEachIndexed { index, preference ->
                preference.setValue(true)
                check(prefs.t9AssistMask() == (1 shl index))
                preference.setValue(false)
            }
            if (measure) measureExactCorpus()
            for (selectedTheme in listOf("trime", "tongwenfeng.trime")) {
                phase("theme=$selectedTheme")
                check(ThemeManager.selectTheme(selectedTheme) == selectedTheme) { "Unexpected theme fallback: $selectedTheme" }
                check(ThemeManager.prefs.selectedTheme.getValue() == selectedTheme)
                delay(800)
                var baseline = emptyArray<CandidateProto>()
                api {
                    clearComposition()
                    "54".forEach { processKey(it.code) }
                    check(t9Cached.choices.all { it.sources == 0 })
                    baseline = getCandidates(0, 80)
                }
                val keys = main { bounds() }
                prefs.t9AssistOptions[6].setValue(true)
                delay(500)
                var state = T9StateProto()
                api {
                    refreshT9Options()
                    check(getCandidates(0, 80).contentEquals(baseline))
                    state = t9Cached
                }
                val index = state.choices.indexOfFirst { it.spelling == "ni" && it.end == 2 && it.sources and 64 != 0 }
                check(index >= 0) { "No adjacent-key ni suggestion" }
                val list = main {
                    descendants(input()).filterIsInstance<RecyclerView>().first {
                        it.adapter?.javaClass?.name?.contains("T9DisambiguationView") == true
                    }.apply { scrollToPosition(index) }
                }
                delay(400)
                main {
                    check(bounds() == keys) { "Suggestions moved the main keys" }
                    val choice = checkNotNull(list.findViewHolderForAdapterPosition(index)).itemView as TextView
                    check(choice.contentDescription.contains(instrumentation.targetContext.getString(R.string.t9_source_adjacent)))
                    val layout = checkNotNull(choice.layout)
                    check(layout.lineCount == 2 && layout.height <= choice.height - choice.paddingTop - choice.paddingBottom) { "Source label clipped vertically" }
                    for (line in 0 until layout.lineCount) {
                        check(layout.getLineWidth(line) <= choice.width - choice.paddingLeft - choice.paddingRight + 1) { "Source label clipped horizontally" }
                    }
                }
                checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Unable to capture $selectedTheme" }.let { bitmap ->
                    try {
                        File(instrumentation.targetContext.cacheDir, "t05-${selectedTheme.removeSuffix(".trime")}.png").outputStream().use {
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                main { checkNotNull(list.findViewHolderForAdapterPosition(index)).itemView.performClick() }
                delay(400)
                api {
                    check(t9Cached.input == "54")
                    check(t9Cached.segments.any { it.locked && it.spelling == "ni" && it.sources == 64 })
                    check(t9Action(t9Cached.revision, T9Action.Undo))
                    check(t9Cached.input == "54" && t9Cached.segments.none { it.locked })
                    val suggestion = t9Cached.choices.first { it.spelling == "ni" && it.sources == 64 }
                    check(t9Action(t9Cached.revision, T9Action.Lock, suggestion.start, suggestion.end, suggestion.spelling))
                }
                check(main { editor.text.toString().startsWith("P:") && !editor.text.toString().contains("你") }) { "Repair selection committed a Hanzi" }
                api {
                    val hanzi = getCandidates(0, 80).indexOfFirst { it.text == "你" }
                    check(hanzi >= 0 && selectCandidate(hanzi, true))
                }
                check(main { editor.text.toString() } == "P:你") { "Incorrect editor commit after repair" }
                api { clearComposition() }
                val previousEditor = main {
                    val token = service.editorToken
                    editor.setText("P:")
                    editor.setSelection(2)
                    val imm = editor.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.restartInput(editor)
                    token
                }
                // setText/restartInput invalidates the old connection asynchronously.
                // Wait before cleanup or the next theme issues an editor-owned job.
                withTimeout(10_000) {
                    while (!main {
                            service.editorToken != previousEditor && service.isCurrentEditor(service.editorToken) &&
                                service.currentInputEditorInfo?.initialSelStart == 2 &&
                                service.currentInputEditorInfo?.initialSelEnd == 2 && editor.hasWindowFocus()
                        }
                    ) {
                        delay(50)
                    }
                }
                api { }
                phase("editor reset acknowledged after $selectedTheme")
                prefs.t9AssistOptions[6].setValue(false)
            }
        } finally {
            prefs.t9AssistOptions.forEachIndexed { index, preference -> preference.setValue(saved[index]) }
            withContext(NonCancellable) {
                withTimeout(30_000) {
                    api {
                        clearComposition()
                        setRuntimeOption("simplification", simplified)
                    }
                    ThemeManager.selectTheme(theme)
                }
            }
        }
    }
}
