/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.GeneralStyle
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * GeneralStyle decode baseline: field-level values from the built-in trime.yaml
 * (golden values taken from the file itself), and graceful fallback to defaults
 * for malformed values (empty / wrong types / unknown enums) in incorrect.yaml.
 */
class GeneralStyleTest :
    BehaviorSpec({
        Given("the built-in trime.yaml") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")

            When("its style section is decoded") {
                val style = theme.generalStyle

                Then("plain scalar values from the file are preserved") {
                    style shouldNotBe null
                    style.autoCaps shouldBe false
                    style.candidatePadding shouldBe 5
                    style.candidateSpacing shouldBe 0f
                    style.candidateTextSize shouldBe 22f
                    style.candidateTextVerticalBias shouldBe 1f
                    style.candidateViewHeight shouldBe 28
                    style.commentHeight shouldBe 12
                    style.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    style.commentTextSize shouldBe 10f
                    style.horizontalGap shouldBe 1
                    style.keyHeight shouldBe 44
                    style.keyLongTextSize shouldBe 14f
                    style.keyTextSize shouldBe 22f
                    style.keyWidth shouldBe 10f
                    style.labelTextSize shouldBe 22f
                    style.keyboardHeight shouldBe 250
                    style.keyboardHeightLand shouldBe 200
                    style.keyboardPaddingRight shouldBe 40
                    style.keyboardPaddingLand shouldBe 40
                }

                Then("fonts declared as a single scalar decode to an empty list") {
                    // By design (since 2bcdf382) fonts must be lists, e.g. `candidate_font: [han.ttf]`;
                    // a scalar like `candidate_font: han.ttf` decodes to an empty list (system font).
                    style.candidateFont shouldBe emptyList()
                    style.keyFont shouldBe emptyList()
                }

                Then("theme header is decoded") {
                    theme.name shouldBe "預設"
                }
            }
        }

        Given("a theme with empty/incorrect style values") {
            val theme = ThemeTestSupport.decodeThemeFile("src/test/assets/incorrect.yaml")

            When("its style section is decoded") {
                val style = theme.generalStyle

                Then("malformed values fall back to defaults without exception") {
                    style.autoCaps shouldBe false
                    style.candidateTextSize shouldBe 15f
                    style.candidateBorder shouldBe 0
                    style.candidateFont shouldBe emptyList()
                    style.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    style.enterLabel shouldNotBe null
                    style.enterLabel.go shouldBe "go"
                    style.enterLabel.default shouldBe "default"
                }
            }
        }
    })
