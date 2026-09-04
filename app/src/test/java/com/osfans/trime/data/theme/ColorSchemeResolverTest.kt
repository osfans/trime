/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.ColorScheme
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The scheme-selection matrix, extracted from ColorManager in R2: selected
 * scheme id x follow-system-day-night x night state. Colors are irrelevant
 * here; only the light_scheme/dark_scheme links and the id lookup matter.
 */
class ColorSchemeResolverTest :
    BehaviorSpec({
        fun scheme(id: String, vararg links: Pair<String, String>) = ColorScheme(id, links.toMap())

        val fixtures =
            listOf(
                scheme("default", "dark_scheme" to "steam"),
                scheme("steam", "light_scheme" to "default"),
                scheme("day_night", "light_scheme" to "dawn", "dark_scheme" to "dusk"),
                scheme("dawn"),
                scheme("dusk"),
                scheme("plain"),
            )
        fun resolve(
            selected: String,
            follow: Boolean,
            night: Boolean,
        ) = ColorSchemeResolver.resolve(fixtures, selected, follow, night).id

        Given("followSystemDayNight is off") {
            When("the selected scheme exists") {
                Then("it is used regardless of night state") {
                    resolve("plain", follow = false, night = false) shouldBe "plain"
                    resolve("plain", follow = false, night = true) shouldBe "plain"
                }
            }
            When("the selected scheme id is unknown") {
                Then("the default scheme is used") {
                    resolve("missing", follow = false, night = false) shouldBe "default"
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme defines both links") {
            When("daytime") {
                Then("the light scheme is used") {
                    resolve("day_night", follow = true, night = false) shouldBe "dawn"
                }
            }
            When("night") {
                Then("the dark scheme is used") {
                    resolve("day_night", follow = true, night = true) shouldBe "dusk"
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme is light-only (a dark scheme)") {
            When("daytime") {
                Then("its light_scheme is used") {
                    resolve("steam", follow = true, night = false) shouldBe "default"
                }
            }
            When("night") {
                Then("the scheme itself is used") {
                    resolve("steam", follow = true, night = true) shouldBe "steam"
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme is dark-only (a light scheme)") {
            val lightOnly = listOf(
                scheme("base", "dark_scheme" to "nightly"),
                scheme("nightly"),
            )
            fun resolveLightOnly(
                night: Boolean,
            ) = ColorSchemeResolver.resolve(lightOnly, "base", true, night).id

            When("daytime") {
                Then("the scheme itself is used") {
                    resolveLightOnly(false) shouldBe "base"
                }
            }
            When("night") {
                Then("its dark_scheme is used") {
                    resolveLightOnly(true) shouldBe "nightly"
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme defines no links") {
            When("the default scheme defines a dark_scheme") {
                Then("daytime falls back to the default scheme itself") {
                    resolve("plain", follow = true, night = false) shouldBe "default"
                }
                Then("night falls back to the default scheme's dark_scheme") {
                    resolve("plain", follow = true, night = true) shouldBe "steam"
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme id is unknown") {
            Then("the same default-based fallback applies") {
                resolve("missing", follow = true, night = false) shouldBe "default"
                resolve("missing", follow = true, night = true) shouldBe "steam"
            }
        }
        Given("followSystemDayNight is on and a link points at an unknown scheme id") {
            val brokenLink = listOf(scheme("default", "dark_scheme" to "ghost"), scheme("plain"))
            When("the linked scheme is missing") {
                Then("the resolution falls back through the default scheme") {
                    ColorSchemeResolver.resolve(brokenLink, "plain", true, true).id shouldBe "default"
                    ColorSchemeResolver.resolve(brokenLink, "plain", true, false).id shouldBe "default"
                }
            }
        }
        Given("there is no scheme named default") {
            val noDefault = listOf(scheme("first"), scheme("second"))
            When("the selected scheme id is unknown and follow is off") {
                Then("the first scheme is used") {
                    ColorSchemeResolver.resolve(noDefault, "missing", false, false).id shouldBe "first"
                }
            }
        }
    })
