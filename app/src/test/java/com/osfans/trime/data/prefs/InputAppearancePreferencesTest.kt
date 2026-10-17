/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.prefs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputAppearancePreferencesTest :
    StringSpec({
        "theme sizes remain intact until a user explicitly chooses overrides" {
            candidateTextSize(17.5f, 0) shouldBe 17.5f
            keyboardHeightOverride(800, 0) shouldBe 0
            val prefs = AppPrefs(InMemorySharedPreferences().preferences)
            prefs.candidates.textSize.getValue() shouldBe 0
            prefs.keyboard.keyboardHeight.getValue() shouldBe 0
        }

        "candidate text overrides have readable lower bounds and bounded upper limits" {
            candidateTextSize(17f, 1) shouldBe 12f
            candidateTextSize(17f, 24) shouldBe 24f
            candidateTextSize(17f, Int.MAX_VALUE) shouldBe 36f
            candidateTextSize(17f, Int.MIN_VALUE) shouldBe 17f
        }

        "keyboard height leaves room for the editor on small windows and clamps corrupt values" {
            keyboardHeightOverride(800, 280) shouldBe 280
            keyboardHeightOverride(500, 480) shouldBe 284
            keyboardHeightOverride(320, 480) shouldBe 200
            keyboardHeightOverride(800, Int.MAX_VALUE) shouldBe 480
            keyboardHeightOverride(800, 1) shouldBe 200
        }

        "appearance overrides persist and use the existing direct-boot mirror" {
            val credential = InMemorySharedPreferences()
            val device = InMemorySharedPreferences()
            val source = AppPrefs(credential.preferences)
            source.candidates.textSize.setValue(24)
            source.keyboard.keyboardHeight.setValue(280)
            AppPrefs(credential.preferences).candidates.textSize.getValue() shouldBe 24
            val mirror = DirectBootPreferenceMirror({ device.preferences }, listOf(source.candidates, source.keyboard))
            mirror.syncAll() shouldBe true
            device.value(AppPrefs.Candidates.TEXT_SIZE) shouldBe 24
            device.value(AppPrefs.Keyboard.KEYBOARD_HEIGHT) shouldBe 280
        }

        "a large candidate font fits both the compact and expanded top-comment allocations" {
            // Font metrics in dp can exceed nominal SP under accessibility scaling/custom fonts.
            val candidate = 64f
            val annotation = 28f
            val row = candidateRowHeight(28, candidate, annotation, commentsAbove = true)
            (row * 0.6f >= candidate) shouldBe true
            (row * 0.4f >= annotation) shouldBe true
            (row >= 28) shouldBe true
        }

        "default theme fonts also grow with system font scaling while taller themes are preserved" {
            // A zero user override selects the themed SP size, not an unscaled row height.
            candidateTextSize(17f, 0) shouldBe 17f
            candidateRowHeight(28, 64f, 28f, commentsAbove = true) shouldBe 107
            candidateRowHeight(160, 64f, 28f, commentsAbove = true) shouldBe 160
            val row = candidateRowHeight(28, 64f, 28f, commentsAbove = false)
            (row >= 64) shouldBe true
        }

        "unscaled metrics that fit keep the theme height and overlay uses the taller label" {
            candidateRowHeight(48, 24f, 16f, commentsAbove = true) shouldBe 48
            candidateRowHeight(48, 24f, 56f, commentsAbove = false) shouldBe 56
        }
    })
