/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The scheme-selection matrix, extracted from ColorManager in R2: selected
 * scheme id x follow-system-day-night x night state. Colors are irrelevant
 * here; only the light_scheme/dark_scheme links and the id lookup matter.
 */
class ColorSchemeResolverTest :
    BehaviorSpec({
        // The marker color keeps the fixtures distinguishable: the resolver
        // returns the colors of the scheme it picked, and these tests assert
        // which one that is.
        fun scheme(id: String, vararg links: Pair<String, String>) = id to (links.toMap() + ("name" to id))

        val fixtures =
            mapOf(
                scheme("default", "dark_scheme" to "steam"),
                scheme("steam", "light_scheme" to "default"),
                scheme("day_night", "light_scheme" to "dawn", "dark_scheme" to "dusk"),
                scheme("dawn"),
                scheme("dusk"),
                scheme("plain"),
            )

        /** The colors of the fixture scheme [id], for asserting which one was picked. */
        fun colorsOf(id: String) = fixtures.getValue(id)

        fun resolve(
            selected: String,
            follow: Boolean,
            night: Boolean,
        ) = ColorSchemeResolver.resolve(fixtures, selected, follow, night)

        Given("followSystemDayNight is off") {
            When("the selected scheme exists") {
                Then("it is used regardless of night state") {
                    resolve("plain", follow = false, night = false) shouldBe colorsOf("plain")
                    resolve("plain", follow = false, night = true) shouldBe colorsOf("plain")
                }
            }
            When("the selected scheme id is unknown") {
                Then("the default scheme is used") {
                    resolve("missing", follow = false, night = false) shouldBe colorsOf("default")
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme defines both links") {
            When("daytime") {
                Then("the light scheme is used") {
                    resolve("day_night", follow = true, night = false) shouldBe colorsOf("dawn")
                }
            }
            When("night") {
                Then("the dark scheme is used") {
                    resolve("day_night", follow = true, night = true) shouldBe colorsOf("dusk")
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme is light-only (a dark scheme)") {
            When("daytime") {
                Then("its light_scheme is used") {
                    resolve("steam", follow = true, night = false) shouldBe colorsOf("default")
                }
            }
            When("night") {
                Then("the scheme itself is used") {
                    resolve("steam", follow = true, night = true) shouldBe colorsOf("steam")
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme is dark-only (a light scheme)") {
            val lightOnly = mapOf(
                scheme("base", "dark_scheme" to "nightly"),
                scheme("nightly"),
            )
            fun resolveLightOnly(
                night: Boolean,
            ) = ColorSchemeResolver.resolve(lightOnly, "base", true, night)

            When("daytime") {
                Then("the scheme itself is used") {
                    resolveLightOnly(false) shouldBe lightOnly.getValue("base")
                }
            }
            When("night") {
                Then("its dark_scheme is used") {
                    resolveLightOnly(true) shouldBe lightOnly.getValue("nightly")
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme defines no links") {
            When("the mode changes") {
                Then("the explicit choice is kept instead of following the default scheme") {
                    resolve("plain", follow = true, night = false) shouldBe colorsOf("plain")
                    resolve("plain", follow = true, night = true) shouldBe colorsOf("plain")
                }
            }
        }
        Given("followSystemDayNight is on and the selected scheme id is unknown") {
            Then("the same default-based fallback applies") {
                resolve("missing", follow = true, night = false) shouldBe colorsOf("default")
                resolve("missing", follow = true, night = true) shouldBe colorsOf("steam")
            }
        }
        Given("followSystemDayNight is on and a link points at an unknown scheme id") {
            val brokenLink =
                mapOf(
                    scheme("default", "dark_scheme" to "steam"),
                    scheme("steam"),
                    scheme("plain", "dark_scheme" to "ghost"),
                )
            When("the selected scheme's link is missing") {
                Then("the selected scheme itself is used") {
                    ColorSchemeResolver.resolve(brokenLink, "plain", true, true) shouldBe brokenLink.getValue("plain")
                    ColorSchemeResolver.resolve(brokenLink, "plain", true, false) shouldBe brokenLink.getValue("plain")
                }
            }
        }
        Given("there is no scheme named default") {
            val noDefault = mapOf(scheme("first"), scheme("second"))
            When("the selected scheme id is unknown and follow is off") {
                Then("the first scheme is used") {
                    ColorSchemeResolver.resolve(noDefault, "missing", false, false) shouldBe noDefault.getValue("first")
                }
            }
        }
        Given("a theme that defines no color scheme at all") {
            // ThemeLoader refuses such a theme, so reaching this point is a
            // programming error: it says so instead of failing on the empty list.
            Then("resolving says why instead of failing on the empty list") {
                val e =
                    shouldThrow<IllegalArgumentException> {
                        ColorSchemeResolver.resolve(emptyMap(), "default", false, false)
                    }
                e.message shouldBe "The theme defines no color scheme"
            }
        }
    })
