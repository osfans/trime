/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Golden tests for the shipped themes: parse + decode, key fields compared against the
 * source files value by value.
 *
 * - tongwenfeng.trime.yaml: no librime DSL; decodes as-is, covering anchors/aliases
 *   (style values via `*hgap`/`*jpgd4`/...) and flow mappings (`preset_keys`/`keys`).
 * - trime.yaml: two `__include` entries (librime DSL, expanded at deploy time); at source
 *   level those keyboards decode as defaults with no keys.
 */
class ThemeGoldenTest :
    BehaviorSpec({
        Given("the built-in tongwenfeng.trime.yaml") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("tongwenfeng.trime.yaml")

            When("the whole file is decoded") {
                Then("theme header and style scalars are preserved") {
                    theme.name shouldBe "标准"
                    val style = theme.style
                    style.autoCaps shouldBe false
                    style.candidateTextSize shouldBe 18f
                    style.keyTextSize shouldBe 24f
                    style.keyWidth shouldBe 10f
                    style.keyboardHeight shouldBe 250
                    style.keyboardHeightLand shouldBe 200
                }

                Then("style values referenced through anchors/aliases resolve to the anchored values") {
                    // File defines height: {4: &jpgd4 48}, 6: &hgap 4, 7: &sgap 12, 1: &round1 6;
                    // style references them via *jpgd4 / *hgap / *sgap / *round1.
                    val style = theme.style
                    style.keyHeight shouldBe 48
                    style.horizontalGap shouldBe 4
                    style.verticalGap shouldBe 12
                    style.roundCorner shouldBe 6f
                }

                Then("enter labels are decoded") {
                    val enterLabel = theme.style.enterLabels
                    enterLabel.go shouldBe "前往"
                    enterLabel.done shouldBe "完成"
                    enterLabel.default shouldBe "Enter"
                }

                Then("all 51 preset keyboards are decoded") {
                    theme.presetKeyboards.size shouldBe 51
                    theme.presetKeyboards shouldContainKey "default"
                    theme.presetKeyboards shouldContainKey "letter"
                    theme.presetKeyboards shouldContainKey "number"
                    theme.presetKeyboards shouldContainKey "bqrw1"
                }

                Then("every preset keyboard decodes at least one key") {
                    theme.presetKeyboards.filterValues { it.keys.isEmpty() }.keys shouldBe emptySet()
                }

                Then("the default keyboard decodes keys incl. inline flow mappings and per-key colors") {
                    val keyboard = theme.presetKeyboards.getValue("default")
                    keyboard.name shouldBe "26键默认布局"
                    keyboard.author shouldBe "暖暖"
                    keyboard.width shouldBe 10f
                    keyboard.asciiMode shouldBe 0
                    keyboard.keys.size shouldBe 37
                    val firstKey = keyboard.keys.first()
                    firstKey.click shouldBe KeyActionToken.Plain("q")
                    firstKey.longClick shouldBe KeyActionToken.Plain("1")
                    firstKey.keyBackColor shouldBe "bh1"
                    firstKey.keyTextColor shouldBe "th1"
                }

                Then("all 46 color schemes are decoded, with the default scheme intact") {
                    theme.presetColorSchemes.size shouldBe 46
                    val defaultScheme = theme.presetColorSchemes.entries.first { it.key == "default" }
                    defaultScheme.value["name"] shouldBe "标准配色！"
                    defaultScheme.value["dark_scheme"] shouldBe "steam"
                }

                Then("fallback colors override table is decoded") {
                    theme.fallbackColors shouldBe mapOf("candidate_text_color" to "text_color")
                }

                Then("preset keys with inline maps are decoded") {
                    theme.presetKeys shouldContainKey "BRIGHTNESS_DOWN"
                    val brightnessDown = theme.presetKeys.getValue("BRIGHTNESS_DOWN")
                    brightnessDown.label shouldBe "亮度-"
                    brightnessDown.send shouldBe "BRIGHTNESS_DOWN"
                }
            }
        }

        Given("the built-in trime.yaml with local YAML aliases") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")

            When("the whole file is decoded") {
                Then("theme header and style scalars are preserved") {
                    theme.name shouldBe "預設"
                    val style = theme.style
                    style.candidateTextSize shouldBe 22f
                    style.keyHeight shouldBe 44
                    style.horizontalGap shouldBe 1
                }

                Then("color schemes and preset keys are decoded") {
                    theme.presetColorSchemes.size shouldBe 37
                    theme.presetKeys.size shouldBe 123
                    val brightnessDown = theme.presetKeys.getValue("BRIGHTNESS_DOWN")
                    brightnessDown.label shouldBe "亮度-"
                    brightnessDown.send shouldBe "BRIGHTNESS_DOWN"
                }

                Then("all 19 keyboards are decoded with their keys") {
                    theme.presetKeyboards.size shouldBe 19
                    theme.presetKeyboards shouldContainKey "default"
                    theme.presetKeyboards shouldContainKey "qwerty0"
                    theme.presetKeyboards shouldContainKey "cangjie5"
                    theme.presetKeyboards shouldContainKey "array30"

                    val default = theme.presetKeyboards.getValue("default")
                    default.name shouldBe "預設40鍵"
                    default.width shouldBe 10f
                    default.height shouldBe 44f
                    default.lock shouldBe true
                    default.asciiMode shouldBe 0
                    default.keys.size shouldBe 47
                    default.keys.first().click shouldBe
                        KeyActionToken.Plain("1")
                    default.keys.first().longClick shouldBe
                        KeyActionToken.Plain("!")

                    val qwerty0 = theme.presetKeyboards.getValue("qwerty0")
                    qwerty0.labelTransform shouldBe TextKeyboard.LabelTransform.UPPERCASE
                }

                Then("the letter keyboard reuses default keys and preserves its mode overrides") {
                    val letter = theme.presetKeyboards.getValue("letter")
                    val default = theme.presetKeyboards.getValue("default")
                    letter.asciiMode shouldBe 1
                    letter.resetAsciiMode shouldBe true
                    letter.lock shouldBe false
                    letter.name shouldBe default.name
                    letter.width shouldBe default.width
                    letter.height shouldBe default.height
                    letter.keys.size shouldBe default.keys.size
                    letter.keys.first().click shouldBe default.keys.first().click
                }

                Then("the scj6 alias preserves the complete cangjie5 keyboard") {
                    val scj6 = theme.presetKeyboards.getValue("scj6")
                    scj6 shouldBe theme.presetKeyboards.getValue("cangjie5")
                }
                Then("every keyboard decodes a non-empty key set") {
                    theme.presetKeyboards
                        .forEach { (_, keyboard) ->
                            keyboard.keys shouldNotBe emptyList<TextKeyboard.TextKey>()
                        }
                }
            }
        }
        Given("the explicit language and auxiliary navigation in both built-in themes") {
            listOf("tongwenfeng.trime.yaml", "trime.yaml").forEach { name ->
                val theme = ThemeTestSupport.decodeBuiltinTheme(name)
                fun key(layout: String, click: String): TextKeyboard.TextKey = theme.presetKeyboards.getValue(layout).keys.single { it.click == KeyActionToken.Plain(click) }

                Then("$name defines three distinct navigation commands without adding layouts") {
                    mapOf("Keyboard_ascii" to ".ascii", "Keyboard_native" to ".native", "Keyboard_return" to ".return")
                        .forEach { (action, target) ->
                            val preset = theme.presetKeys.getValue(action)
                            preset.send shouldBe "Eisu_toggle"
                            preset.select shouldBe target
                            preset.toggle shouldBe ""
                        }
                    theme.presetKeys.getValue("Keyboard_ascii").label shouldBe "EN"
                    theme.presetKeys.getValue("Keyboard_native").label shouldBe "中文"
                    theme.presetKeys.getValue("Keyboard_return").label shouldBe "返回"
                    theme.presetKeyboards.size shouldBe if (name == "trime.yaml") 19 else 51
                    theme.presetKeys.size shouldBe if (name == "trime.yaml") 123 else 257
                }

                Then("$name exposes English on the real T9 number key and preserves Chinese punctuation") {
                    val number = key("luna_pinyin_t9", "Keyboard_number")
                    number.label shouldBe "123"
                    number.width shouldBe 18f
                    number.longClick shouldBe KeyActionToken.Plain("Keyboard_ascii")
                    number.hint shouldBe "EN"
                    key("luna_pinyin_t9", ",").label shouldBe "，"
                    key("luna_pinyin_t9", ".").label shouldBe "。"
                    key("luna_pinyin_t9", ",").width shouldBe 16f
                    key("luna_pinyin_t9", ".").width shouldBe 16f
                }

                Then("$name exposes a schema-native Chinese return on the actual English keyboard") {
                    val letter = theme.presetKeyboards.getValue("letter")
                    letter.asciiMode shouldBe 1
                    letter.keys.size shouldBe if (name == "trime.yaml") 47 else 37
                    val chinese = if (name == "trime.yaml") {
                        val mode = key("letter", "Mode_switch")
                        mode.ascii shouldBe KeyActionToken.Plain("Keyboard_native")
                        mode.longClick shouldBe KeyActionToken.Plain("Menu")
                        theme.presetKeys.getValue("Mode_switch").toggle shouldBe "ascii_mode"
                        theme.presetKeyboards.getValue("default").keys shouldBe letter.keys
                        mode
                    } else {
                        val native = key("letter", "Keyboard_native")
                        native.longClick shouldBe KeyActionToken.Plain("Theme_settings")
                        native.width shouldBe 12.5f
                        native.ascii shouldBe null
                        native
                    }
                    chinese.sendBindings shouldBe true
                    chinese.composing shouldBe null
                    chinese.hasMenu shouldBe null
                    chinese.paging shouldBe null
                }

                Then("$name auxiliary return keys stay usable while composition or candidates are present") {
                    listOf("number", "symbols").forEach { layout ->
                        val back = key(layout, "Keyboard_return")
                        back.sendBindings shouldBe true
                        back.ascii shouldBe null
                        back.composing shouldBe null
                        back.hasMenu shouldBe null
                        back.paging shouldBe null
                    }
                    val number = key("symbols", "Keyboard_number")
                    number.composing shouldBe null
                    number.hasMenu shouldBe null
                    number.paging shouldBe null
                    if (name == "tongwenfeng.trime.yaml") {
                        key("number", "Keyboard_ascii").label shouldBe "EN"
                    }
                }

                Then("$name preserves visible routes for email URL and signed decimal input") {
                    val letter = theme.presetKeyboards.getValue("letter")
                    letter.keys.any { it.longClick == KeyActionToken.Plain("@") } shouldBe true
                    key("letter", ".").click shouldBe KeyActionToken.Plain(".")
                    key("letter", "/").click shouldBe KeyActionToken.Plain("/")
                    val minus = key("letter", if (name == "trime.yaml") "w" else "j")
                    minus.longClick shouldBe KeyActionToken.Plain("-")
                    key("number", "-").click shouldBe KeyActionToken.Plain("-")
                    key("number", ".").click shouldBe KeyActionToken.Plain(".")
                    if (name == "tongwenfeng.trime.yaml") {
                        key("letter", "s").hint shouldBe "@"
                        minus.hint shouldBe "-"
                        minus.swipeDown shouldBe KeyActionToken.Plain("&")
                        key("letter", "/").longClick shouldBe KeyActionToken.Plain("liquid_keyboard_switch")
                        key("letter", "space").longClick shouldBe KeyActionToken.Plain("space_R2")
                    } else {
                        key("letter", "2").longClick shouldBe KeyActionToken.Plain("@")
                        theme.presetKeyboards.getValue("mini").asciiMode shouldBe 0
                    }
                }
            }
        }
        Given("the T9 layouts in both built-in themes") {
            listOf("tongwenfeng.trime.yaml", "trime.yaml").forEach { name ->
                Then("$name preserves numeric T9 input and its alphabetic fallback") {
                    val theme = ThemeTestSupport.decodeBuiltinTheme(name)
                    val keyboard = theme.presetKeyboards.getValue("luna_pinyin_t9")
                    keyboard.asciiMode shouldBe 0
                    keyboard.asciiKeyboard shouldBe "letter"
                    keyboard.keyboardHeightLand shouldBe 200
                    keyboard.t9Layout shouldBe true
                    keyboard.keys.size shouldBe 17
                    val grid = keyboard.keys.take(12).filterIndexed { index, _ -> index % 4 != 3 }
                    grid.map { it.click } shouldBe
                        listOf(KeyActionToken.Plain("T9_separator")) + (2..9).map { KeyActionToken.Plain(it.toString()) }
                    grid.map { it.longClick } shouldBe
                        (1..9).map { KeyActionToken.Plain("T9_digit_$it") }
                    theme.presetKeys.getValue("T9_separator").send shouldBe "apostrophe"
                    (0..9).forEach {
                        theme.presetKeys.getValue("T9_digit_$it").command shouldBe "t9_digit"
                        theme.presetKeys.getValue("T9_digit_$it").option shouldBe it.toString()
                    }
                    theme.presetKeys.getValue("T9_space").slideCursor shouldBe true
                    theme.presetKeys.getValue("T9_backspace").repeatable shouldBe true
                    val rows = keyboard.keys.take(12).chunked(4) + listOf(keyboard.keys.drop(12))
                    rows.forEach { row -> row.sumOf { it.width.toDouble() } shouldBe 100.0 }
                }
            }
        }
    })
