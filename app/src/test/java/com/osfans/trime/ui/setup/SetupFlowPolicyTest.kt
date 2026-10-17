/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.setup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SetupFlowPolicyTest :
    StringSpec({
        "an upgrade does not require repeating the new wizard steps" {
            needsInputSetup(false, false, false, false, false) shouldBe false
        }

        "a fresh install still needs input choice after system IME steps finish" {
            needsInputSetup(false, true, false, false, false) shouldBe true
        }

        "interrupted setup resumes its unfinished trial" {
            needsInputSetup(true, false, true, false, false) shouldBe true
            needsInputSetup(true, false, true, true, false) shouldBe false
        }

        "explicitly deferring the trial does not open the wizard again" {
            needsInputSetup(true, true, false, false, true) shouldBe false
            needsInputSetup(true, false, true, false, true) shouldBe false
        }
    })
