/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.shouldBe

class NavigationRouteTest :
    StringSpec({
        "route paths remain stable and unique" {
            val routes =
                listOf(
                    NavigationRoute.Main,
                    NavigationRoute.QuickSettings,
                    NavigationRoute.SchemaList,
                    NavigationRoute.UserDict,
                    NavigationRoute.Profile,
                    NavigationRoute.General,
                    NavigationRoute.VirtualKeyboard,
                    NavigationRoute.CandidatesWindow,
                    NavigationRoute.Theme,
                    NavigationRoute.Clipboard,
                    NavigationRoute.Advanced,
                    NavigationRoute.Developer,
                    NavigationRoute.About,
                    NavigationRoute.License,
                )

            routes.map(NavigationRoute::path).shouldBeUnique()
            routes.map(NavigationRoute::path) shouldBe
                listOf(
                    "com.osfans.trime.ui.main.NavigationRoute.Main",
                    "com.osfans.trime.ui.main.NavigationRoute.QuickSettings",
                    "com.osfans.trime.ui.main.NavigationRoute.SchemaList",
                    "com.osfans.trime.ui.main.NavigationRoute.UserDict",
                    "com.osfans.trime.ui.main.NavigationRoute.Profile",
                    "com.osfans.trime.ui.main.NavigationRoute.General",
                    "com.osfans.trime.ui.main.NavigationRoute.VirtualKeyboard",
                    "com.osfans.trime.ui.main.NavigationRoute.CandidatesWindow",
                    "com.osfans.trime.ui.main.NavigationRoute.Theme",
                    "com.osfans.trime.ui.main.NavigationRoute.Clipboard",
                    "com.osfans.trime.ui.main.NavigationRoute.Advanced",
                    "com.osfans.trime.ui.main.NavigationRoute.Developer",
                    "com.osfans.trime.ui.main.NavigationRoute.About",
                    "com.osfans.trime.ui.main.NavigationRoute.License",
                )
        }
    })
