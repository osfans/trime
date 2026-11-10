/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/** Pins the arithmetic that keeps theme images from being decoded larger than drawn. */
class BitmapSamplingTest :
    BehaviorSpec({
        Given("a source larger than the target view") {
            Then("it is scaled down until it just covers the view") {
                // 4000x3000 in a 1080x1200 keyboard: height binds, width becomes 1600.
                BitmapSampling.coverSize(4000, 3000, 1080, 1200) shouldBe (1600 to 1200)
            }
        }
        Given("a source that already fits") {
            Then("it is not upscaled") {
                BitmapSampling.coverSize(320, 200, 1080, 1200) shouldBe (320 to 200)
                BitmapSampling.coverSize(1080, 1200, 1080, 1200) shouldBe (1080 to 1200)
            }
        }
        Given("the power-of-two sample size") {
            Then("both dimensions still cover the target afterwards") {
                BitmapSampling.calculateSampleSize(4000, 3000, 1080, 1200) shouldBe 2
                BitmapSampling.calculateSampleSize(8000, 8000, 1080, 1200) shouldBe 4
            }
        }
    })
