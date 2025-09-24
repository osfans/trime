// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.BuildConfig
import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.model.GeneralStyle
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class GeneralStyleTest :
    BehaviorSpec({
        Given("Correct trime.yaml") {
            val dir = File("src/test/assets")
            Rime.startupRime(
                dir.absolutePath,
                dir.absolutePath,
                BuildConfig.BUILD_VERSION_NAME,
                false,
            )

            When("loaded") {
                val generalStyle = Theme.decodeByConfigId("trime").generalStyle

                Then("it should not be null") {
                    generalStyle shouldNotBe null
                    generalStyle.autoCaps shouldBe "false"

                    generalStyle.candidateFont shouldBe listOf("han.ttf")
                }
            }

            Rime.exitRime()
        }

        Given("Empty trime.yaml") {
            val dir = File("src/test/assets")
            Rime.startupRime(
                dir.absolutePath,
                dir.absolutePath,
                BuildConfig.BUILD_VERSION_NAME,
                false,
            )

            When("loaded") {
                val generalStyle = Theme.decodeByConfigId("incorrect").generalStyle

                Then("with default value without exception") {
                    generalStyle.autoCaps shouldBe ""
                    generalStyle.candidateBorder shouldBe 0
                    generalStyle.candidateFont shouldBe emptyList()
                    generalStyle.commentPosition shouldBe GeneralStyle.CommentPosition.UNKNOWN

                    generalStyle.enterLabel shouldNotBe null
                    generalStyle.enterLabel.go shouldBe "go"
                }
            }

            Rime.exitRime()
        }
    })
