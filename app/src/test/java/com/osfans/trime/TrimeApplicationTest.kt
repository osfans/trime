/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TrimeApplicationTest :
    BehaviorSpec({
        Given("the direct boot state") {
            When("the device is locked") {
                Then("WorkManager startup is skipped") {
                    TrimeApplication.shouldStartWorkManager(isDirectBootMode = true) shouldBe false
                }
            }
            When("the device is unlocked") {
                Then("WorkManager startup is allowed") {
                    TrimeApplication.shouldStartWorkManager(isDirectBootMode = false) shouldBe true
                }
            }
        }
    })
