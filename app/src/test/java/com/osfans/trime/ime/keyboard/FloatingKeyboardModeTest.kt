/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FloatingKeyboardModeTest :
    BehaviorSpec({
        Given("the floating keyboard mode") {
            When("the window is landscape") {
                Then("it floats even when the portrait preference is off") {
                    FloatingKeyboardMode.shouldFloat(
                        landscape = true,
                        dockedForSession = false,
                        portraitPreference = false,
                    ) shouldBe true
                }

                Then("a session dock wins over the preference") {
                    FloatingKeyboardMode.shouldFloat(
                        landscape = true,
                        dockedForSession = true,
                        portraitPreference = true,
                    ) shouldBe false
                }
            }

            When("the window is portrait") {
                Then("it is docked by default") {
                    FloatingKeyboardMode.shouldFloat(
                        landscape = false,
                        dockedForSession = false,
                        portraitPreference = false,
                    ) shouldBe false
                }

                Then("the persisted preference can float it") {
                    FloatingKeyboardMode.shouldFloat(
                        landscape = false,
                        dockedForSession = true,
                        portraitPreference = true,
                    ) shouldBe true
                }
            }
        }
    })
