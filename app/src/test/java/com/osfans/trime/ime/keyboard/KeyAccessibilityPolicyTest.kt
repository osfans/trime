/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyAccessibilityPolicyTest :
    StringSpec({
        "accessibility cannot overlap a physical touch, deferred touch action or disabled key" {
            val gate = GestureAccessibilityActivation()
            gate.begin(enabled = false, touchActive = false, pendingTouchAction = false) shouldBe false
            gate.begin(enabled = true, touchActive = true, pendingTouchAction = false) shouldBe false
            gate.begin(enabled = true, touchActive = false, pendingTouchAction = true) shouldBe false
            gate.begin(enabled = true, touchActive = false, pendingTouchAction = false) shouldBe true
            gate.begin(enabled = true, touchActive = false, pendingTouchAction = false) shouldBe false
            gate.finish()
            gate.begin(enabled = true, touchActive = false, pendingTouchAction = false) shouldBe true
        }

        "visible digit and letter hints are retained once without icon implementation labels" {
            KeyAccessibilityLabelPolicy.label("2", "abc", "abc", null, "Key") shouldBe "2 abc"
            KeyAccessibilityLabelPolicy.label("ic@arrow", "", "ic@arrow", "Enter", "Key") shouldBe "Enter"
            KeyAccessibilityLabelPolicy.label("ic@custom", "", "", null, "Key") shouldBe "Key"
        }

        "dynamic enter meaning wins over the theme sentinel and raw macro labels are not spoken" {
            KeyAccessibilityLabelPolicy.label("enter_labels", "", "", "Send", "Key") shouldBe "Send"
            KeyAccessibilityLabelPolicy.label("enter_labels", "", "", "Search", "Key") shouldBe "Search"
            KeyAccessibilityLabelPolicy.label("{Control+a}", "{clipboard}", "", null, "Key") shouldBe "Key"
        }

        "keyboard destinations and current toggle captions remain distinguishable" {
            listOf("123", "符号", "EN", "中文", "返回").forEach { caption ->
                KeyAccessibilityLabelPolicy.label(caption, "", "", "Switch keyboard", "Key", preferDisplay = true) shouldBe caption
            }
            listOf("上一输入法", "下一输入法", "半角", "全角", "漢字", "汉字").forEach { caption ->
                KeyAccessibilityLabelPolicy.label(caption, "", "", "Switch language", "Key", preferDisplay = true) shouldBe caption
            }
            KeyAccessibilityLabelPolicy.label("ic@keyboard", "", "", "Switch keyboard", "Key", preferDisplay = true) shouldBe "Switch keyboard"
        }

        "functional enter action keeps priority over a readable static caption" {
            KeyAccessibilityLabelPolicy.label("Return", "", "", "Send", "Key") shouldBe "Send"
            KeyAccessibilityLabelPolicy.label("Return", "", "", "Search", "Key") shouldBe "Search"
        }

        "literal braces and paired punctuation remain available to symbol-page users" {
            listOf("{", "}", "{}", "{ }").forEach { symbol ->
                KeyAccessibilityLabelPolicy.label(symbol, "", "", null, "Key") shouldBe symbol
            }
        }
    })
