/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.matchKeyboard
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CompatibleKeyboardsTest :
    StringSpec({
        val builtin = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")
        val thirdParty = builtin.copy(
            name = "third-party without T9",
            presetKeyboards = builtin.presetKeyboards - "luna_pinyin_t9" - "number" - "letter" - "symbols",
            presetKeys = builtin.presetKeys + ("T9_digit_2" to PresetKey(commit = "wrong")) + ("2" to PresetKey(commit = "wrong")),
        )
        val patched = thirdParty.withCompatibleKeyboards(builtin)
        "third-party style and every original key and keyboard remain untouched" {
            patched.style shouldBe thirdParty.style
            patched.presetColorSchemes shouldBe thirdParty.presetColorSchemes
            thirdParty.presetKeyboards.forEach { (id, value) -> patched.presetKeyboards[id] shouldBe value }
            thirdParty.presetKeys.forEach { (id, value) -> patched.presetKeys[id] shouldBe value }
        }
        "mixed T9 alphabet with metadata uses the compatible T9 instead of QWERTY" {
            val id = matchKeyboard("luna_pinyin_t9", "luna_pinyin_t9", true, "abcABC123~", patched.presetKeyboards, patched.fallbackKeyboards)
            id shouldBe patched.fallbackKeyboards["luna_pinyin_t9"]
            patched.presetKeyboards.getValue(id).t9Layout shouldBe true
        }
        "numeric stroke schemas are never inferred to be pinyin T9" {
            matchKeyboard("stroke", "", false, "12345", patched.presetKeyboards, patched.fallbackKeyboards) shouldBe "stroke"
            matchKeyboard("unknown_numeric", "", false, "12345", patched.presetKeyboards, patched.fallbackKeyboards) shouldBe "qwerty0"
        }
        "full pinyin shuangpin and empty alphabet keep established defaults" {
            matchKeyboard("luna_pinyin", "", false, "abc", patched.presetKeyboards, patched.fallbackKeyboards) shouldBe "qwerty"
            matchKeyboard("shuangpin", "", false, "abc;", patched.presetKeyboards, patched.fallbackKeyboards) shouldBe "qwerty_"
            matchKeyboard(".default", "", false, "", patched.presetKeyboards, patched.fallbackKeyboards) shouldBe "default"
        }
        "explicit schema layout takes priority over alphabet inference" {
            matchKeyboard("custom", "symbols", false, "12345", patched.presetKeyboards, patched.fallbackKeyboards) shouldBe
                patched.fallbackKeyboards["symbols"]
        }
        "compatible literal digits and long presses cannot be hijacked by same-named third-party keys" {
            val key = patched.presetKeyboards.getValue(patched.fallbackKeyboards.getValue("luna_pinyin_t9")).keys[1]
            val click = (key.click as KeyActionToken.Plain).token
            val long = (key.longClick as KeyActionToken.Plain).token
            patched.presetKeys.getValue(click).send shouldBe "2"
            patched.presetKeys.getValue(long).command shouldBe "t9_digit"
            patched.presetKeys.getValue(long).option shouldBe "2"
        }
        "compatible language actions preserve semantic targets despite third-party name collisions" {
            val conflicts = mapOf(
                "Keyboard_ascii" to PresetKey(commit = "wrong-ascii"),
                "Keyboard_native" to PresetKey(commit = "wrong-native"),
                "Keyboard_return" to PresetKey(commit = "wrong-return"),
            )
            val source = thirdParty.copy(presetKeys = thirdParty.presetKeys + conflicts)
            val compatible = source.withCompatibleKeyboards(builtin)
            fun action(token: KeyActionToken?): PresetKey = compatible.presetKeys.getValue((token as KeyActionToken.Plain).token)
            val t9 = compatible.presetKeyboards.getValue(compatible.fallbackKeyboards.getValue("luna_pinyin_t9"))
            action(t9.keys.single { it.label == "123" }.longClick).select shouldBe ".ascii"
            val letter = compatible.presetKeyboards.getValue(compatible.fallbackKeyboards.getValue("letter"))
            val chinese = letter.keys.single { action(it.click).toggle == "ascii_mode" }
            action(chinese.click).send shouldBe "SWITCH_CHARSET"
            action(chinese.ascii).select shouldBe ".native"
            chinese.sendBindings shouldBe true
            listOf("number", "symbols").forEach { layout ->
                val keyboard = compatible.presetKeyboards.getValue(compatible.fallbackKeyboards.getValue(layout))
                val back = keyboard.keys.single { action(it.click).select == ".return" }
                action(back.click).send shouldBe "Eisu_toggle"
                back.composing shouldBe null
                back.hasMenu shouldBe null
                back.paging shouldBe null
            }
            conflicts.forEach { (id, original) -> compatible.presetKeys.getValue(id) shouldBe original }
        }
        "compatible literal auxiliary targets use the private namespace while language targets stay semantic" {
            val t9 = patched.presetKeyboards.getValue(patched.fallbackKeyboards.getValue("luna_pinyin_t9"))
            val number = t9.keys.single { it.label == "123" }
            patched.presetKeys.getValue((number.click as KeyActionToken.Plain).token).select shouldBe
                patched.fallbackKeyboards.getValue("number")
            patched.presetKeys.getValue((number.longClick as KeyActionToken.Plain).token).select shouldBe ".ascii"
            t9.asciiKeyboard shouldBe patched.fallbackKeyboards.getValue("letter")
            val symbols = patched.presetKeyboards.getValue(patched.fallbackKeyboards.getValue("symbols"))
            val actions = symbols.keys.map { patched.presetKeys.getValue((it.click as KeyActionToken.Plain).token) }
            actions.single { it.label == "返回" }.select shouldBe ".return"
            actions.single { it.label == "數字" }.select shouldBe patched.fallbackKeyboards.getValue("number")
        }
        "reserved namespace collisions select a new namespace without overwriting user content" {
            val twice = patched.withCompatibleKeyboards(builtin)
            patched.presetKeyboards.forEach { (id, value) -> twice.presetKeyboards[id] shouldBe value }
            (twice.fallbackKeyboards != patched.fallbackKeyboards) shouldBe true
        }
        "unmapped literal parentheses stay text while mapped punctuation keeps key semantics" {
            val keys = patched.presetKeyboards.getValue(patched.fallbackKeyboards.getValue("symbols")).keys
            fun TextKeyboard.TextKey.actions() = listOfNotNull(
                click,
                doubleClick,
                lazyDoubleClick,
                longClick,
                swipeUp,
                swipeDown,
                swipeLeft,
                swipeRight,
                composing,
                hasMenu,
                paging,
                combo,
                ascii,
            )
            val actions = keys.flatMap { it.actions() }.filterIsInstance<KeyActionToken.Plain>()
                .map { patched.presetKeys.getValue(it.token) }
            listOf("(", ")").forEach { symbol ->
                actions.any { it.text == symbol && it.label == symbol && it.send.isEmpty() } shouldBe true
            }
            actions.any { it.send == "." && it.text.isEmpty() } shouldBe true
        }
    })
