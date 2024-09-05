// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.mapper.GeneralStyleMapper
import com.osfans.trime.ime.symbol.VarLengthAdapter
import com.osfans.trime.util.config.Config
import com.osfans.trime.util.config.ConfigData
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GeneralStyleTest :
    BehaviorSpec({
        Given("Correct trime.yaml") {
            val style =
                Config(
                    ConfigData().apply {
                        loadFromFile("src/test/assets/trime.yaml")
                    },
                ).getMap("style")

            When("loaded") {
                val mapper = GeneralStyleMapper(style)

                val generalStyle = mapper.map()

                Then("it should not be null") {
                    generalStyle shouldNotBe null
                    generalStyle.autoCaps shouldBe "false"
                    generalStyle.backgroundDimAmount shouldBe 0.5

                    generalStyle.candidateFont shouldBe listOf("han.ttf")
                    println("Error: " + mapper.errors.size + ", " + mapper.errors.joinToString(","))
                    mapper.errors.size shouldBe 0
                }
            }
        }

        Given("Empty trime.yaml") {
            val style =
                Config(
                    ConfigData().apply {
                        loadFromFile("src/test/assets/incorrect.yaml")
                    },
                ).getMap("style")

            When("loaded") {
                val mapper = GeneralStyleMapper(style)

                val generalStyle = mapper.map()

                Then("with default value without exception") {
                    generalStyle.autoCaps shouldBe ""
                    generalStyle.backgroundDimAmount shouldBe 0
                    generalStyle.candidateBorder shouldBe 0
                    generalStyle.candidateFont shouldBe emptyList()
                    generalStyle.candidateUseCursor shouldBe false
                    generalStyle.commentPosition shouldBe VarLengthAdapter.SecondTextPosition.UNKNOWN

                    generalStyle.enterLabel shouldNotBe null
                    generalStyle.enterLabel.go shouldBe "go"

                    generalStyle.window shouldNotBe null
                    generalStyle.window.size shouldBe 0

                    generalStyle.layout shouldNotBe null

                    generalStyle.liquidKeyboardWindow shouldNotBe null
                    generalStyle.liquidKeyboardWindow.size shouldBe 0

                    println("Error: " + mapper.errors.size + ", " + mapper.errors.joinToString(","))
                    mapper.errors.size shouldBeGreaterThan 0
                }
            }
        }
    })
