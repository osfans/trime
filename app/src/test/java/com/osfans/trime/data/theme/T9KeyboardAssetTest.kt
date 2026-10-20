/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class T9KeyboardAssetTest :
    StringSpec({
        for (file in listOf("trime.yaml", "tongwenfeng.trime.yaml")) {
            "$file provides the nine-key layout and literal-number hold actions" {
                val theme = ThemeTestSupport.decodeBuiltinTheme(file)
                val keyboard = theme.presetKeyboards.getValue("luna_pinyin_t9")
                keyboard.t9Layout shouldBe true
                keyboard.asciiMode shouldBe 0
                keyboard.keys.mapNotNull { (it.click as? KeyActionToken.Plain)?.token }
                    .filter { it.length == 1 && it[0] in '2'..'9' } shouldBe ('2'..'9').map(Char::toString)
                val rows = mutableListOf<Float>()
                var used = 0f
                for (key in keyboard.keys) {
                    used += if (key.width > 0f) key.width else keyboard.width
                    if (used >= 100f) {
                        rows.add(used)
                        used = 0f
                    }
                    for (token in listOfNotNull(key.click, key.longClick).filterIsInstance<KeyActionToken.Plain>()) {
                        if (token.token.startsWith("T9_")) theme.presetKeys.containsKey(token.token) shouldBe true
                    }
                }
                rows shouldBe listOf(100f, 100f, 100f, 100f)
                used shouldBe 0f
                for (digit in '1'..'9') {
                    keyboard.keys.any { it.longClick == KeyActionToken.Plain("T9_digit_$digit") } shouldBe true
                }
                for (digit in '0'..'9') {
                    val action = theme.presetKeys.getValue("T9_digit_$digit")
                    action.command shouldBe "t9_digit"
                    action.option shouldBe digit.toString()
                }
            }
            "$file switches numbers and English to real ASCII layouts" {
                val theme = ThemeTestSupport.decodeBuiltinTheme(file)
                for (name in listOf("T9_numbers", "T9_ascii")) {
                    val target = theme.presetKeys.getValue(name).select
                    val keyboard = theme.presetKeyboards.getValue(target)
                    keyboard.asciiMode shouldBe 1
                    keyboard.keys.isNotEmpty() shouldBe true
                    keyboard.lock shouldBe false
                }
                theme.presetKeys.getValue("Keyboard_default").select shouldBe ".default"
            }
            "$file preserves the inherited symbol layout input mode" {
                val theme = ThemeTestSupport.decodeBuiltinTheme(file)
                val original = theme.presetKeyboards.getValue("symbols")
                val target = theme.presetKeys.getValue("T9_symbols").select
                val symbols = theme.presetKeyboards.getValue(target)
                symbols.keys shouldBe original.keys
                symbols.asciiMode shouldBe original.asciiMode
                symbols.resetAsciiMode shouldBe true
                symbols.lock shouldBe false

                if (file == "tongwenfeng.trime.yaml") {
                    // These category macros need the schema recognizer. ASCII mode
                    // rejects their keys before it, leaving no input or candidates.
                    symbols.asciiMode shouldBe 0
                    symbols.keys.any { it.click == KeyActionToken.Plain("标点") } shouldBe true
                    theme.presetKeys.getValue("标点").text shouldBe "{Escape}/bd"
                } else {
                    // The default theme's symbol page contains literal keys.
                    symbols.asciiMode shouldBe 1
                }
            }
        }
    })
