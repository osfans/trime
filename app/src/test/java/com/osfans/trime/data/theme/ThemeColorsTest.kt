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
 * ThemeColors view semantics: typed properties mirror `ColorManager.getColor`
 * — resolvable keys return the pre-compiled value, image-valued or
 * unresolvable keys throw IllegalArgumentException.
 */
class ThemeColorsTest :
    BehaviorSpec({
        fun scheme(vararg pairs: Pair<String, String>) = pairs.toMap()

        val parseHex: (String) -> Int? = { s ->
            val digits =
                when {
                    s.startsWith("#") -> s.substring(1)
                    s.startsWith("0x", ignoreCase = true) -> s.substring(2)
                    else -> null
                }
            digits?.let { runCatching { java.lang.Long.parseLong(it, 16).toInt() }.getOrNull() }
        }

        fun colors(vararg pairs: Pair<String, String>): ThemeColors = ThemeColors(ColorTable.resolve(scheme(*pairs), emptyMap(), parseHex))

        Given("a scheme defining only chain terminals") {
            val view = colors("preedit_normal_text_color" to "#112233", "keyboard_normal_back_color" to "#445566")
            Then("every property resolves through the built-in chains") {
                view.candidateTextColor shouldBe 0x112233
                view.commentTextColor shouldBe 0x112233
                view.keyNormalTextColor shouldBe 0x112233
                view.candidateSelectedBackColor shouldBe 0x445566
                view.functionKeyOnPressedBackColor shouldBe 0x445566
                view.preeditTextColor shouldBe 0x112233
                view.backColor shouldBe 0x445566
            }
        }
        Given("a scheme with a direct entry") {
            val view = colors("candidate_normal_text_color" to "#aabbcc")
            Then("the direct value wins over the chain") {
                view.candidateTextColor shouldBe 0xaabbcc
            }
        }
        Given("an empty scheme") {
            val view = colors()
            Then("unresolvable keys throw like ColorManager.getColor") {
                shouldThrow<IllegalArgumentException> { view.candidateTextColor }
                shouldThrow<IllegalArgumentException> { view.keyNormalBackColor }
                shouldThrow<IllegalArgumentException> { view.candidateBorderColor }
            }
        }
        Given("an image-valued key") {
            val view = colors("key_normal_back_color" to "bg.png")
            Then("reading it as a color throws like ColorManager.getColor") {
                shouldThrow<IllegalArgumentException> { view.keyNormalBackColor }
            }
        }
        Given("the built-in tongwenfeng theme") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("tongwenfeng.trime.yaml")
            val colors = theme.presetColorSchemes.getValue("default")
            val view = ThemeColors(ColorTable.resolve(colors, theme.fallbackColors, parseHex))
            Then("the day scheme colors resolve to the parsed scheme values") {
                view.candidateTextColor shouldBe parseHex(colors.getValue("candidate_normal_text_color"))
                view.keyNormalBackColor shouldBe parseHex(colors.getValue("key_normal_back_color"))
                view.preeditTextColor shouldBe parseHex(colors.getValue("preedit_normal_text_color"))
            }
        }
    })
