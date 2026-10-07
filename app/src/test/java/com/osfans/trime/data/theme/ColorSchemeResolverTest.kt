/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.v2.ColorSchemas
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The light/dark selection matrix: follow-system-day-night x night state x the
 * explicit mode selection. A V2 theme has exactly two palettes, so the only
 * question is which one to use.
 */
class ColorSchemeResolverTest :
    BehaviorSpec({
        val light = mapOf("backColor" to "0xffffff")
        val dark = mapOf("backColor" to "0x000000")
        val schemas = ColorSchemas(light = light, dark = dark)

        fun resolve(
            selected: String,
            follow: Boolean,
            night: Boolean,
        ) = ColorSchemeResolver.resolve(schemas, selected, follow, night)

        Given("followSystemDayNight is off") {
            When("the explicit selection is dark") {
                Then("dark is used regardless of the night state") {
                    resolve("dark", follow = false, night = false) shouldBe dark
                    resolve("dark", follow = false, night = true) shouldBe dark
                }
            }
            When("the explicit selection is light or a legacy scheme id") {
                Then("light is used") {
                    resolve("light", follow = false, night = false) shouldBe light
                    resolve("light", follow = false, night = true) shouldBe light
                    // A legacy scheme id (e.g. "default") no longer exists and falls back to light.
                    resolve("default", follow = false, night = true) shouldBe light
                }
            }
        }
        Given("followSystemDayNight is on") {
            Then("daytime uses light and night uses dark") {
                resolve("light", follow = true, night = false) shouldBe light
                resolve("dark", follow = true, night = true) shouldBe dark
                resolve("light", follow = true, night = true) shouldBe dark
                resolve("dark", follow = true, night = false) shouldBe light
            }
        }
    })
