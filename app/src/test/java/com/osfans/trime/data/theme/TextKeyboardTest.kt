/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import com.osfans.trime.data.theme.model.orAbsent
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * TextKeyboard / TextKey decoding baseline: an empty section decodes to the
 * constructor defaults, and explicit keys override single fields while the
 * rest keep their defaults. Behavior entries stay optional: an absent
 * [KeyBehavior.CLICK] slot is kept (null action), other absent behaviors are
 * dropped, matching the pre-refactor decode.
 */
class TextKeyboardTest :
    BehaviorSpec({
        Given("an empty keyboard section") {
            val keyboard = ThemeTestSupport.yaml.decodeFromYamlNode<TextKeyboard>(yamlMapOf())

            Then("decode equals the constructor defaults") {
                keyboard shouldBe TextKeyboard.DEFAULTS
            }

            Then("its text keys are empty") {
                keyboard.keys shouldBe emptyList()
            }

            Then("its default text key equals the constructor defaults") {
                val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(yamlMapOf())
                key shouldBe TextKey.DEFAULTS
                key.click shouldBe null
            }
        }

        Given("a keyboard with explicit keys") {
            val keyboard =
                ThemeTestSupport.yaml.decodeFromYamlNode<TextKeyboard>(
                    yamlMapOf(
                        "columns" to yamlScalarOf("12"),
                        "round_corner" to yamlScalarOf("4"),
                        "key_border" to yamlScalarOf("1"),
                        "ascii_mode" to yamlScalarOf("0"),
                    ),
                )

            Then("explicit keys are preserved") {
                keyboard.columns shouldBe 12
                keyboard.roundCorner shouldBe 4f
                keyboard.keyBorder shouldBe 1
                keyboard.asciiMode shouldBe 0
            }

            Then("the rest keep their defaults") {
                keyboard.horizontalGap shouldBe TextKeyboard.DEFAULTS.horizontalGap
                keyboard.height shouldBe TextKeyboard.DEFAULTS.height
            }
        }

        Given("a key with a click action") {
            val key =
                ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                    yamlMapOf(
                        "round_corner" to yamlScalarOf("0"),
                        "click" to yamlScalarOf("a"),
                    ),
                )

            Then("explicit keys are preserved") {
                key.roundCorner shouldBe 0f
                key.click shouldNotBe null
            }

            Then("unset keys keep their sentinel defaults") {
                key.keyBorder shouldBe -1
                key.height shouldBe 0f
            }

            Then("a blank token counts as no action") {
                val blank = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(yamlMapOf("click" to yamlScalarOf("")))
                blank.click shouldBe KeyActionToken.Plain("")
                blank.click.orAbsent shouldBe null
                key.click.orAbsent shouldBe KeyActionToken.Plain("a")
            }
        }

        Given("a key whose popup is written as a scalar") {
            val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                yamlMapOf("popup" to yamlScalarOf("!popup")),
            )

            Then("it is read as an empty list, the way it used to be read") {
                key.popup shouldBe emptyList()
            }
        }

        Given("a key whose popup has no value") {
            val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                ThemeTestSupport.yaml.parseToYamlNode("popup:\n"),
            )

            Then("it is read as an empty list instead of failing") {
                key.popup shouldBe emptyList()
            }
        }

        Given("a key with per-key highlight overrides") {
            val key =
                ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                    yamlMapOf(
                        "hilited_key_text_color" to yamlScalarOf("0xffff0000"),
                        "hilited_key_back_color" to yamlScalarOf("0xff00ff00"),
                        "hilited_key_border_color" to yamlScalarOf("0xff0000ff"),
                        "hilited_key_symbol_color" to yamlScalarOf("0xff00ffff"),
                    ),
                )

            Then("all four are read under their snake case names") {
                key.hilitedKeyTextColor shouldBe "0xffff0000"
                key.hilitedKeyBackColor shouldBe "0xff00ff00"
                key.hilitedKeyBorderColor shouldBe "0xff0000ff"
                key.hilitedKeySymbolColor shouldBe "0xff00ffff"
            }
        }
    })
