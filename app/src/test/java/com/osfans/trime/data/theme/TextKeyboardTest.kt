/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * TextKeyboard / TextKey decode baseline: an empty section decodes to the
 * constructor defaults, and explicit keys override single fields while the
 * rest keep their defaults. Behavior entries stay node-driven: an absent
 * [KeyBehavior.CLICK] slot is kept (null action), other absent behaviors are
 * dropped, matching the pre-refactor decode.
 */
class TextKeyboardTest :
    BehaviorSpec({
        Given("an empty keyboard section") {
            val keyboard = TextKeyboard.decode(yamlMapOf())

            Then("decode equals the constructor defaults") {
                keyboard shouldBe TextKeyboard.DEFAULTS
            }

            Then("its text keys are empty") {
                keyboard.keys shouldBe emptyList()
            }

            Then("its default text key equals the constructor defaults") {
                val key = TextKey.decode(yamlMapOf())
                key shouldBe TextKey.DEFAULTS
                key.click shouldBe null
            }
        }

        Given("a keyboard with explicit keys") {
            val keyboard =
                TextKeyboard.decode(
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
                keyboard.asciiMode shouldBe false
            }

            Then("the rest keep their defaults") {
                keyboard.horizontalGap shouldBe TextKeyboard.DEFAULTS.horizontalGap
                keyboard.height shouldBe TextKeyboard.DEFAULTS.height
            }
        }

        Given("a key with a click action") {
            val key =
                TextKey.decode(
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
        }
    })
