/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyRole
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

/** Pins the `role:` vocabulary of doc/Theme.md §2. */
class KeyRoleTest :
    BehaviorSpec({
        Given("the role table") {
            Then("the names are unique") {
                KeyRole.entries.map { it.yamlName }.toSet().size shouldBe KeyRole.entries.size
            }
            Then("the names follow the snake_case vocabulary") {
                val snakeCase = Regex("^[a-z][a-z0-9_]*$")
                for (entry in KeyRole.entries) {
                    entry.yamlName shouldMatch snakeCase
                }
            }
            Then("the usual spellings all resolve") {
                KeyRole.of("enter") shouldBe KeyRole.ENTER
                KeyRole.of("page_up") shouldBe KeyRole.PAGE_UP
                KeyRole.of("pageUp") shouldBe KeyRole.PAGE_UP
                KeyRole.of("PAGE-UP") shouldBe KeyRole.PAGE_UP
                KeyRole.of(" cursor_left ") shouldBe KeyRole.CURSOR_LEFT
            }
            Then("an unknown role is null") {
                KeyRole.of("").shouldBeNull()
                KeyRole.of("macro").shouldBeNull()
            }
        }
        Given("the default behaviors") {
            Then("plain keys carry their keysym") {
                KeyRole.ENTER.send shouldBe "Return"
                KeyRole.SPACE.send shouldBe "space"
                KeyRole.BACKSPACE.send shouldBe "BackSpace"
                KeyRole.SHIFT.send shouldBe "Shift_L"
            }
            Then("data-driven keys carry their switch") {
                KeyRole.KEYBOARD.select shouldBe ".next"
                KeyRole.LANGUAGE.toggle shouldBe "ascii_mode"
                KeyRole.SYMBOL.command shouldBe "liquid_keyboard"
                KeyRole.CLIPBOARD.command shouldBe "clipboard_window"
            }
        }
    })
