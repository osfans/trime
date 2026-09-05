/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yaml.Node
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
            val keyboard = TextKeyboard.decode(Node.Mapping())

            Then("decode equals the constructor defaults") {
                keyboard shouldBe TextKeyboard.DEFAULTS
            }

            Then("its text keys are empty") {
                keyboard.keys shouldBe emptyList()
            }

            Then("its default text key equals the constructor defaults") {
                val key = TextKey.decode(Node.Mapping())
                key.copy(behaviors = emptyMap()) shouldBe TextKey.DEFAULTS
                key.hasClickAction shouldBe false
            }
        }

        Given("a keyboard with explicit keys") {
            val keyboard =
                TextKeyboard.decode(
                    Node.Mapping(
                        Node.Scalar("columns") to Node.Scalar("12"),
                        Node.Scalar("round_corner") to Node.Scalar("4"),
                        Node.Scalar("key_border") to Node.Scalar("1"),
                        Node.Scalar("ascii_mode") to Node.Scalar("0"),
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
                    Node.Mapping(
                        Node.Scalar("round_corner") to Node.Scalar("0"),
                        Node.Scalar("click") to Node.Scalar("a"),
                    ),
                )

            Then("explicit keys are preserved") {
                key.roundCorner shouldBe 0f
                key.behaviors[KeyBehavior.CLICK] shouldNotBe null
                key.hasClickAction shouldBe true
            }

            Then("unset keys keep their sentinel defaults") {
                key.keyBorder shouldBe -1
                key.height shouldBe 0f
            }
        }
    })
