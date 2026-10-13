// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import com.osfans.trime.core.KeyModifier
import com.osfans.trime.core.KeyModifiers
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Virtual Return with a chord modifier is forwarded by sendDownUpKeyEvent.
 * An empty mask, or Caps Lock / Num Lock alone, still calls handleReturnKey.
 * The service cannot be constructed here, so this pins that branch condition.
 */
class VirtualReturnDispatchTest :
    BehaviorSpec({
        Given("a virtual Return modifier mask") {
            When("it carries shift, control, alt, meta, or control+shift") {
                Then("it is a chord and takes sendDownUpKeyEvent") {
                    KeyModifiers(KeyModifier.Shift).isReturnChord() shouldBe true
                    KeyModifiers(KeyModifier.Control).isReturnChord() shouldBe true
                    KeyModifiers(KeyModifier.Alt).isReturnChord() shouldBe true
                    KeyModifiers(KeyModifier.Meta).isReturnChord() shouldBe true
                    KeyModifiers(KeyModifier.Control, KeyModifier.Shift).isReturnChord() shouldBe true
                }
            }
            When("the mask is empty") {
                Then("it is not a chord and still calls handleReturnKey") {
                    KeyModifiers.Empty.isReturnChord() shouldBe false
                }
            }
            When("only caps lock or num lock is set") {
                Then("it is not a chord and still calls handleReturnKey") {
                    KeyModifiers(KeyModifier.Lock).isReturnChord() shouldBe false
                    KeyModifiers(KeyModifier.Mod2).isReturnChord() shouldBe false
                }
            }
        }
    })
