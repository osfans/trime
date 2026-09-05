/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Pure resolvers of the keyboard geometry cascade: conventions A (positive
 * wins, gaps/heights), B (non-negative wins, corners/borders with -1 as the
 * absent sentinel) and C (non-zero wins for offsets) plus the landscape pick.
 * These pin the legacy merge semantics extracted from Keyboard.kt.
 */
class KeyboardResolveTest :
    BehaviorSpec({
        Given("a positive-wins resolver with a doubling unit") {
            val unit: (Int) -> Int = { it * 2 }

            When("the config value is positive") {
                Then("it wins and the unit is applied once") {
                    resolvePositive(3, 10, unit) shouldBe 6
                }
            }

            When("the config value is zero or null") {
                Then("the style value wins") {
                    resolvePositive(0, 10, unit) shouldBe 20
                    resolvePositive(null, 10, unit) shouldBe 20
                }
            }

            When("both values are zero or null") {
                Then("the result is zero") {
                    resolvePositive(0, 0, unit) shouldBe 0
                    resolvePositive(null, 0) shouldBe 0
                }
            }

            When("negative config values are given") {
                Then("they never win") {
                    resolvePositive(-1, 10, unit) shouldBe 20
                }
            }
        }

        Given("a non-negative-wins resolver (Float)") {
            When("the config value is absent (-1)") {
                Then("the style value is used") {
                    resolveNonNegative(-1f, 8f) shouldBe 8f
                }
            }

            When("the config value is zero") {
                Then("zero is a legal override") {
                    resolveNonNegative(0f, 8f) shouldBe 0f
                }
            }

            When("the config value is positive") {
                Then("it wins") {
                    resolveNonNegative(4f, 8f) shouldBe 4f
                }
            }

            When("the config is null") {
                Then("the style value is used") {
                    resolveNonNegative(null, 8f) shouldBe 8f
                }
            }
        }

        Given("a non-negative-wins resolver (Int)") {
            When("the config value is absent (-1) or null") {
                Then("the style value is used") {
                    resolveNonNegative(-1, 2) shouldBe 2
                    resolveNonNegative(null, 2) shouldBe 2
                }
            }

            When("the config value is zero") {
                Then("zero is a legal override") {
                    resolveNonNegative(0, 2) shouldBe 0
                }
            }
        }

        Given("an offset resolver cascading key, keyboard and style") {
            When("the key offset is non-zero") {
                Then("it wins") {
                    resolveOffset(1.5f, 2f, 3f) shouldBe 1.5f
                }
            }

            When("the key offset is zero but the keyboard offset is set") {
                Then("the keyboard offset wins") {
                    resolveOffset(0f, 2f, 3f) shouldBe 2f
                }
            }

            When("only the style offset is set") {
                Then("the style offset is the fallback") {
                    resolveOffset(0f, 0f, 3f) shouldBe 3f
                }
            }

            When("all are zero") {
                Then("the result is zero") {
                    resolveOffset(0f, 0f, 0f) shouldBe 0f
                }
            }

            When("negative offsets are given") {
                Then("they are kept") {
                    resolveOffset(-1f, 2f, 3f) shouldBe -1f
                    resolveOffset(0f, -2f, 3f) shouldBe -2f
                }
            }
        }

        Given("a landscape picker") {
            When("in portrait mode") {
                Then("the landscape value is ignored") {
                    pickLandscape(250, 200, landscape = false) shouldBe 250
                }
            }

            When("in landscape mode with a landscape value") {
                Then("the landscape value wins") {
                    pickLandscape(250, 200, landscape = true) shouldBe 200
                }
            }

            When("in landscape mode without a landscape value") {
                Then("the portrait value is used") {
                    pickLandscape(250, 0, landscape = true) shouldBe 250
                }
            }
        }
    })
