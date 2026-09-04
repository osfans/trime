/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyBehavior
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
                    val style = theme.generalStyle
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
                    val style = theme.generalStyle
                    style.keyHeight shouldBe 48
                    style.horizontalGap shouldBe 4
                    style.verticalGap shouldBe 12
                    style.roundCorner shouldBe 6f
                }

                Then("enter labels are decoded") {
                    val enterLabel = theme.generalStyle.enterLabel
                    enterLabel.go shouldBe "前往"
                    enterLabel.done shouldBe "完成"
                    enterLabel.default shouldBe "Enter"
                }

                Then("all 50 preset keyboards are decoded") {
                    theme.presetKeyboards.size shouldBe 50
                    theme.presetKeyboards shouldContainKey "default"
                    theme.presetKeyboards shouldContainKey "letter"
                    theme.presetKeyboards shouldContainKey "number"
                    theme.presetKeyboards shouldContainKey "bqrw1"
                }

                Then("the default keyboard decodes keys incl. inline flow mappings and per-key colors") {
                    val keyboard = theme.presetKeyboards.getValue("default")
                    keyboard.name shouldBe "26键默认布局"
                    keyboard.author shouldBe "暖暖"
                    keyboard.width shouldBe 10f
                    keyboard.asciiMode shouldBe false
                    keyboard.keys.size shouldBe 37
                    val firstKey = keyboard.keys.first()
                    firstKey.behaviors[KeyBehavior.CLICK] shouldBe KeyActionToken.Plain("q")
                    firstKey.behaviors[KeyBehavior.LONG_CLICK] shouldBe KeyActionToken.Plain("1")
                    firstKey.keyBackColor shouldBe "bh1"
                    firstKey.keyTextColor shouldBe "th1"
                }

                Then("all 46 color schemes are decoded, with the default scheme intact") {
                    theme.colorSchemes.size shouldBe 46
                    val defaultScheme = theme.colorSchemes.first { it.id == "default" }
                    defaultScheme.colors["name"] shouldBe "标准配色！"
                    defaultScheme.colors["dark_scheme"] shouldBe "steam"
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

        Given("the built-in trime.yaml (source level: two __include entries are not expanded)") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")

            When("the whole file is decoded") {
                Then("theme header and style scalars are preserved") {
                    theme.name shouldBe "預設"
                    val style = theme.generalStyle
                    style.candidateTextSize shouldBe 22f
                    style.keyHeight shouldBe 44
                    style.horizontalGap shouldBe 1
                }

                Then("color schemes and preset keys are decoded") {
                    theme.colorSchemes.size shouldBe 37
                    theme.presetKeys.size shouldBe 106
                    val brightnessDown = theme.presetKeys.getValue("BRIGHTNESS_DOWN")
                    brightnessDown.label shouldBe "亮度-"
                    brightnessDown.send shouldBe "BRIGHTNESS_DOWN"
                }

                Then("all 18 plain keyboards are decoded with their keys") {
                    theme.presetKeyboards.size shouldBe 18
                    theme.presetKeyboards shouldContainKey "default"
                    theme.presetKeyboards shouldContainKey "qwerty0"
                    theme.presetKeyboards shouldContainKey "cangjie5"
                    theme.presetKeyboards shouldContainKey "array30"

                    val default = theme.presetKeyboards.getValue("default")
                    default.name shouldBe "預設40鍵"
                    default.width shouldBe 10f
                    default.height shouldBe 44f
                    default.lock shouldBe true
                    default.asciiMode shouldBe false
                    default.keys.size shouldBe 47
                    default.keys.first().behaviors[KeyBehavior.CLICK] shouldBe
                        KeyActionToken.Plain("1")
                    default.keys.first().behaviors[KeyBehavior.LONG_CLICK] shouldBe
                        KeyActionToken.Plain("!")

                    val qwerty0 = theme.presetKeyboards.getValue("qwerty0")
                    qwerty0.labelTransform shouldBe TextKeyboard.LabelTransform.UPPERCASE
                }

                Then("the __include 'letter' keyboard applies its own sibling keys but has no keys at source level") {
                    // `letter` holds __include + its own ascii_mode/reset_ascii_mode/lock; deployment
                    // splices /preset_keyboards/default into it. At source level it has no keys.
                    val letter = theme.presetKeyboards.getValue("letter")
                    letter.asciiMode shouldBe true
                    letter.resetAsciiMode shouldBe true
                    letter.lock shouldBe false
                    letter.keys shouldBe emptyList()
                }

                Then("the pure __include 'scj6' keyboard decodes as a default keyboard at source level") {
                    // `scj6` only has __include (/preset_keyboards/cangjie5); nothing to decode
                    // at source level. ascii_mode defaults to 1 when absent ((?:1)==1).
                    val scj6 = theme.presetKeyboards.getValue("scj6")
                    scj6.keys shouldBe emptyList()
                    scj6.asciiMode shouldBe true
                    scj6.width shouldBe 0f
                }

                Then("every non-include keyboard decodes a non-empty key set") {
                    theme.presetKeyboards
                        .filterKeys { it !in setOf("letter", "scj6") }
                        .forEach { (id, keyboard) ->
                            keyboard.keys shouldNotBe emptyList<TextKeyboard.TextKey>()
                        }
                }
            }
        }
    })
