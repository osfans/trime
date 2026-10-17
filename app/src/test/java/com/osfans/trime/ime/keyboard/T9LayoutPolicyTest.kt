/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class T9LayoutPolicyTest :
    StringSpec({
        "narrow widths use a horizontal list, wide widths reserve a sidebar" {
            T9LayoutPolicy.widths(360, 0, 1f, 0).choices shouldBe 0
            T9LayoutPolicy.widths(412, 0, 1f, 0).choices shouldBe 88
            T9LayoutPolicy.widths(412, 0, 2f, 0).choices shouldBe 0
            T9LayoutPolicy.widths(800, 0, 2f, 0).choices shouldBe 132
        }
        "all supported widths leave at least 48dp for the narrowest key" {
            for (width in listOf(360, 412, 600, 800, 900)) {
                for (font in listOf(1f, 1.3f, 2f)) {
                    for (hand in 0..2) {
                        val result = T9LayoutPolicy.widths(width, 12, font, hand)
                        val keyboard = width - result.left - result.right - result.choices
                        (keyboard * 0.16 >= 48) shouldBe true
                        if (hand != 0) keyboard shouldBe 320
                    }
                }
            }
        }
        "left and right handed layouts are mirrored without changing key order" {
            val left = T9LayoutPolicy.widths(412, 4, 1f, 1)
            val right = T9LayoutPolicy.widths(412, 4, 1f, 2)
            left.left shouldBe right.right
            left.right shouldBe right.left
            left.choices shouldBe 0
        }
        "short landscape keeps pinyin beside a 320dp one-handed keyboard" {
            for (height in listOf(360, 412)) {
                for (font in listOf(1f, 2f)) {
                    for (hand in 1..2) {
                        val result = T9LayoutPolicy.widths(800, 0, font, hand, height)
                        (result.choices > 0) shouldBe true
                        (800 - result.left - result.right - result.choices) shouldBe 320
                        val budget = T9LayoutPolicy.heightBudget(height, 63, 0, 0, 54)
                        budget.minimumTargetsFit shouldBe true
                        (T9LayoutPolicy.modelHeight(320, budget.maximum) + 63 + 54 <= height) shouldBe true
                    }
                }
            }
        }
        "height caps preserve controls and minimum targets" {
            T9LayoutPolicy.height(412, 200, 80) shouldBe 196
            T9LayoutPolicy.height(360, 200, 80) shouldBe 192
            T9LayoutPolicy.height(800, 240, 80) shouldBe 320
            T9LayoutPolicy.height(800, 240, 1) shouldBe 192
            T9LayoutPolicy.height(800, 240, 0) shouldBe 240
        }
        "short sidebar includes three 48dp actions without squeezing the keyboard" {
            T9LayoutPolicy.widths(443, 0, 2f, 0, 360).choices shouldBe 0
            val wide = T9LayoutPolicy.widths(444, 0, 2f, 0, 360)
            wide.choices shouldBe 144
            (444 - wide.left - wide.right - wide.choices) shouldBe 300
            for (hand in 1..2) {
                val oneHand = T9LayoutPolicy.widths(800, 0, 2f, hand, 360)
                oneHand.choices shouldBe 144
                (800 - oneHand.left - oneHand.right - oneHand.choices) shouldBe 320
            }
        }
        "the actual font2 header and editor reserve cap user height overrides" {
            val budget = T9LayoutPolicy.heightBudget(336, 63, 0, 0, 54)
            budget.maximum shouldBe 219
            budget.minimumTargetsFit shouldBe true
            T9LayoutPolicy.modelHeight(320, budget.maximum) shouldBe 219
            T9LayoutPolicy.modelHeight(200, budget.maximum) shouldBe 200
            T9LayoutPolicy.modelHeight(320, 0) shouldBe 320
        }
        "an insufficient viewport reports scrolling without shrinking the model targets" {
            val budget = T9LayoutPolicy.heightBudget(280, 63, 84, 0, 54)
            budget.maximum shouldBe 79
            budget.minimumTargetsFit shouldBe false
            budget.panelMaximum shouldBe 226
            budget.editorFits shouldBe true
            T9LayoutPolicy.modelHeight(320, budget.maximum) shouldBe 192
            (budget.maximum + 63 + 84 + 54 <= 280) shouldBe true
        }
        "short landscape leaves room for a larger app editor and scrolls the panel" {
            // API 35 font2: usable height 672px, actual platform editor 123px.
            // The former 16sp estimate reserved only 108px and obscured its bottom.
            val reserve = T9LayoutPolicy.editorReserve(672, 108)
            (reserve >= 123) shouldBe true
            val budget = T9LayoutPolicy.heightBudget(336, 63, 84, 0, reserve / 2)
            (budget.panelMaximum * 2 <= 672 - 123) shouldBe true
            budget.minimumTargetsFit shouldBe false
            T9LayoutPolicy.modelHeight(320, budget.maximum) shouldBe 192
        }
        "editor font requirements can exceed the proportional reserve" {
            T9LayoutPolicy.editorReserve(336, 120) shouldBe 120
            val budget = T9LayoutPolicy.heightBudget(100, 63, 84, 0, T9LayoutPolicy.editorReserve(100, 120))
            budget.editorFits shouldBe false
            budget.minimumTargetsFit shouldBe false
            budget.panelMaximum shouldBe 1
        }
        "symbols retain the originating T9 panel until the route or editor changes" {
            T9LayoutPolicy.usesCompositionPanel(false, true, false, true, true) shouldBe true
            T9LayoutPolicy.usesCompositionPanel(false, true, false, true, false) shouldBe false
            T9LayoutPolicy.usesCompositionPanel(false, true, true, true, true) shouldBe false
            T9LayoutPolicy.usesCompositionPanel(false, false, false, true, true) shouldBe false
            T9LayoutPolicy.usesCompositionPanel(false, true, false, false, true) shouldBe false
            T9LayoutPolicy.usesCompositionPanel(true, false, false, false, true) shouldBe true
        }
        "controls taller than the body budget still have a bounded whole panel viewport" {
            val budget = T9LayoutPolicy.heightBudget(200, 63, 164, 12, 54)
            budget.maximum shouldBe 1
            budget.minimumTargetsFit shouldBe false
            budget.panelMaximum shouldBe 146
            (budget.panelMaximum + 54 <= 200) shouldBe true
            T9LayoutPolicy.modelHeight(320, budget.maximum) shouldBe 192
        }
        "a window smaller than the editor reserve never claims its targets fit" {
            val budget = T9LayoutPolicy.heightBudget(40, 63, 164, 12, 54)
            budget.panelMaximum shouldBe 1
            budget.minimumTargetsFit shouldBe false
            budget.editorFits shouldBe false
        }
    })
