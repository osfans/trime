/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlNode
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.MaybeStringList
import com.osfans.trime.util.yamlListOf
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.SerializationException

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
                val style = theme.style

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

                Then("fonts declared as a single scalar decode to a single element") {
                    style.candidateFont shouldBe MaybeStringList.Scalar("han.ttf")
                    style.keyFont shouldBe MaybeStringList.Scalar("symbol.ttf")
                }

                Then("legacy preview fields produce visible popup dimensions with the bundled system font") {
                    style.popupWidth shouldBe 38
                    style.popupHeight shouldBe 60
                    style.popupKeyHeight shouldBe 60
                    style.popupTextSize shouldBe 40f
                    style.popupFont shouldBe MaybeStringList.Sequence(emptyList())
                    // The obsolete preview_offset is absent; popupBottomMargin keeps its own default.
                    style.popupBottomMargin shouldBe 68
                }

                Then("theme header is decoded") {
                    theme.name shouldBe "預設"
                }
            }
        }

        Given("a style whose fonts are written in the other supported shapes") {
            val nullFont = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                ThemeTestSupport.yaml.parseToYamlNode("candidate_font:\n"),
            )
            val listFont = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                ThemeTestSupport.yaml.parseToYamlNode("candidate_font: [a.ttf, b.ttf]\n"),
            )

            Then("a font without a value decodes to no font instead of failing") {
                nullFont.candidateFont shouldBe MaybeStringList.Empty
            }

            Then("a font declared as a list decodes to a sequence") {
                listFont.candidateFont shouldBe MaybeStringList.Sequence(listOf("a.ttf", "b.ttf"))
            }
        }

        Given("a theme with empty/incorrect style values") {
            Then("malformed values raise serialization exception") {
                shouldThrow<SerializationException> {
                    ThemeTestSupport.decodeThemeFile("src/test/assets/incorrect.yaml")
                }
            }
        }

        Given("an empty style section") {
            val style = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(yamlMapOf())

            Then("decode equals the constructor defaults") {
                style shouldBe GeneralStyle()
                style.popupBottomMargin shouldBe 68
                style.popupWidth shouldBe 38
                style.popupHeight shouldBe 48
                style.popupKeyHeight shouldBe 48
                style.popupTextSize shouldBe 23f
            }
            Then("decode fills explicit keys but keeps the defaults for the rest") {
                val style =
                    ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                        yamlMapOf(
                            "candidate_text_size" to yamlScalarOf("20"),
                        ),
                    )
                style.candidateTextSize shouldBe 20f
                style.keyHeight shouldBe 0
            }
        }

        Given("a theme that mixes modern popup fields and legacy preview fields") {
            fun style(vararg popup: Pair<String, YamlNode>): GeneralStyle = ThemeTestSupport.yaml.decodeFromYamlNode(
                yamlMapOf(
                    mapOf(
                        "preview_height" to yamlScalarOf("60"),
                        "preview_text_size" to yamlScalarOf("40"),
                        "preview_font" to yamlScalarOf("legacy.ttf"),
                    ) + popup.toMap(),
                ),
            )

            Then("modern popup dimensions and font lists take precedence") {
                val decoded = style(
                    "popup_bottom_margin" to yamlScalarOf("70"),
                    "popup_width" to yamlScalarOf("42"),
                    "popup_height" to yamlScalarOf("52"),
                    "popup_key_height" to yamlScalarOf("46"),
                    "popup_text_size" to yamlScalarOf("25"),
                    "popup_font" to yamlListOf(yamlScalarOf("modern.ttf")),
                )
                decoded.popupBottomMargin shouldBe 70
                decoded.popupWidth shouldBe 42
                decoded.popupHeight shouldBe 52
                decoded.popupKeyHeight shouldBe 46
                decoded.popupTextSize shouldBe 25f
                decoded.popupFont shouldBe MaybeStringList.Sequence(listOf("modern.ttf"))
            }

            Then("explicit zero dimensions and empty fonts do not reactivate legacy previews") {
                val decoded = style(
                    "popup_bottom_margin" to yamlScalarOf("0"),
                    "popup_width" to yamlScalarOf("0"),
                    "popup_height" to yamlScalarOf("0"),
                    "popup_key_height" to yamlScalarOf("0"),
                    "popup_text_size" to yamlScalarOf("0"),
                    "popup_font" to yamlListOf(),
                )
                decoded.popupBottomMargin shouldBe 0
                decoded.popupWidth shouldBe 0
                decoded.popupHeight shouldBe 0
                decoded.popupKeyHeight shouldBe 0
                decoded.popupTextSize shouldBe 0f
                decoded.popupFont shouldBe MaybeStringList.Sequence(emptyList())
            }

            Then("malformed modern values reject source decoding instead of reviving legacy settings") {
                shouldThrow<SerializationException> {
                    style("popup_height" to yamlScalarOf("invalid"))
                }
            }
        }

        Given("a third-party legacy theme with preview fonts") {
            Then("the popup retains scalar fonts, font order and explicit legacy zero values") {
                val style = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                    yamlMapOf(
                        "preview_height" to yamlScalarOf("0"),
                        "preview_text_size" to yamlScalarOf("0"),
                        "preview_font" to yamlListOf(yamlScalarOf("a.ttf"), yamlScalarOf("b.ttf")),
                    ),
                )
                style.popupHeight shouldBe 0
                style.popupKeyHeight shouldBe 0
                style.popupTextSize shouldBe 0f
                style.popupFont shouldBe MaybeStringList.Sequence(listOf("a.ttf", "b.ttf"))
                val scalarStyle = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                    yamlMapOf("preview_font" to yamlScalarOf("latin.ttf")),
                )
                scalarStyle.popupFont shouldBe MaybeStringList.Scalar("latin.ttf")
            }
        }
    })
