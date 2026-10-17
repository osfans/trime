/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.data.theme.model.KeyActionToken
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PredictionKeyBindingsTest :
    StringSpec({
        "prediction leaves digit and Enter click bindings active even with a native menu" {
            listOf("2", "Return").forEach { click ->
                val actions = mapOf(
                    KeyBehavior.CLICK to click,
                    KeyBehavior.COMPOSING to "commit_current",
                    KeyBehavior.HAS_MENU to "select_second",
                    KeyBehavior.PAGING to "select_page",
                )
                val state = resolveKeyStateBinding(actions, false, true, true, true, true)
                (state ?: actions[KeyBehavior.CLICK]) shouldBe click
            }
        }

        "ordinary compositions retain paging menu and composing precedence" {
            val actions = mapOf(
                KeyBehavior.ASCII to "ascii",
                KeyBehavior.COMPOSING to "composing",
                KeyBehavior.HAS_MENU to "menu",
                KeyBehavior.PAGING to "paging",
            )
            resolveKeyStateBinding(actions, false, true, true, true, false) shouldBe "paging"
            resolveKeyStateBinding(actions, false, true, true, false, false) shouldBe "menu"
            resolveKeyStateBinding(actions, false, true, false, false, false) shouldBe "composing"
            resolveKeyStateBinding(actions, true, true, true, true, true) shouldBe "ascii"
        }

        "bundled theme composition-only Enter overrides do not apply to next-word suggestions" {
            listOf("trime.yaml", "tongwenfeng.trime.yaml").forEach { file ->
                val theme = ThemeTestSupport.decodeBuiltinTheme(file)
                val enter = theme.presetKeyboards.values.flatMap { it.keys }
                    .first { it.click == KeyActionToken.Plain("Return") && it.composing == KeyActionToken.Plain("Return1") }
                val actions = mapOf(
                    KeyBehavior.CLICK to enter.click,
                    KeyBehavior.COMPOSING to enter.composing,
                )
                resolveKeyStateBinding(actions, false, true, true, false, false) shouldBe enter.composing
                (resolveKeyStateBinding(actions, false, true, true, false, true) ?: actions[KeyBehavior.CLICK]) shouldBe enter.click
            }
        }
    })
