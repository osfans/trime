/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.composition

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PreeditCursorTest :
    StringSpec({
        val text = "a汉😀b"

        "Android offsets map to complete UTF8 characters without splitting a surrogate pair" {
            (0..text.length).map { preeditByteOffset(text, it) } shouldBe listOf(0, 1, 4, 4, 8, 9)
            preeditByteOffset(text, -100) shouldBe 0
            preeditByteOffset(text, 100) shouldBe 9
        }

        "character navigation crosses a non-BMP character in one action and clamps at either end" {
            adjacentPreeditCharacter(text, 2, true) shouldBe 4
            adjacentPreeditCharacter(text, 4, false) shouldBe 2
            adjacentPreeditCharacter(text, 0, false) shouldBe 0
            adjacentPreeditCharacter(text, text.length, true) shouldBe text.length
            adjacentPreeditCharacter("", 0, true) shouldBe 0
            adjacentPreeditCharacter("", 0, false) shouldBe 0
        }

        "a malformed service selection is detectable before dispatch to native" {
            preeditCharacterBoundary(text, 3) shouldBe 2
            preeditCharacterBoundary(text, 4) shouldBe 4
            preeditCharacterBoundary(text, Int.MIN_VALUE) shouldBe 0
            preeditCharacterBoundary(text, Int.MAX_VALUE) shouldBe text.length
        }
    })
