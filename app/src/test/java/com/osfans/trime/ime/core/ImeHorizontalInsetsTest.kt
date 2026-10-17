/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ImeHorizontalInsetsTest :
    StringSpec({
        "landscape edge-to-edge reserves asymmetric cutout and navigation regions" {
            ImeHorizontalInsets.remaining(1280, 0, 1280, 64, 96) shouldBe ImeHorizontalInsets(64, 96)
        }
        "system-fitted window does not apply the same safe area twice" {
            ImeHorizontalInsets.remaining(1280, 64, 1120, 64, 96) shouldBe ImeHorizontalInsets()
        }
        "partly fitted and split windows reserve only their own overlap" {
            ImeHorizontalInsets.remaining(1280, 24, 1200, 64, 96) shouldBe ImeHorizontalInsets(40, 40)
            ImeHorizontalInsets.remaining(1280, 640, 640, 64, 96) shouldBe ImeHorizontalInsets(0, 96)
            ImeHorizontalInsets.remaining(1280, 0, 640, 64, 96) shouldBe ImeHorizontalInsets(64, 0)
        }
        "zero-size layout and excessive insets never create negative content width" {
            ImeHorizontalInsets.remaining(0, 0, 0, 64, 96) shouldBe ImeHorizontalInsets()
            ImeHorizontalInsets.remaining(100, 0, 100, 80, 80) shouldBe ImeHorizontalInsets(80, 20)
        }
    })
