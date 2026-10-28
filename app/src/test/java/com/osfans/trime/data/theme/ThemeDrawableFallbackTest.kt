/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests the classification boundary used by drawable parsing, without Android
 * Drawable stubs. Actual Key drawable fallback remains a device-level check.
 */
class ThemeDrawableFallbackTest :
    BehaviorSpec({
        // Match ColorUtils' hex forms, including opaque six-digit RGB and the
        // short alpha-only form. These fixtures do not use named colors.
        fun parseHex(value: String): Int? {
            val digits = when {
                value.startsWith("#") -> value.substring(1)
                value.startsWith("0x", ignoreCase = true) -> value.substring(2)
                else -> return null
            }
            if (digits.length !in 1..8) return null
            val number = digits.toLongOrNull(16) ?: return null
            return when (digits.length) {
                1, 2 -> (number shl 24).toInt()
                in 3..6 -> (0xff000000L or number).toInt()
                else -> number.toInt()
            }
        }

        Given("the standard theme and all its shipped palettes") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("tongwenfeng.trime.yaml")
            val firstKey = theme.presetKeyboards.getValue("default").keys.first()

            Then("Google+ and tantsing leave missing aliases unresolved and expose usable generic colors") {
                firstKey.keyBackColor shouldBe "bh1"
                firstKey.keyTextColor shouldBe "th1"
                val expected = mapOf(
                    "google_plus" to (0xffffffff.toInt() to 0xffdd4b39.toInt()),
                    "tantsing" to (0xffe3e3e3.toInt() to 0xff000000.toInt()),
                )
                for ((id, pair) in expected) {
                    withClue(id) {
                        val scheme = theme.presetColorSchemes.getValue(id)
                        val table = ColorTable.resolve(scheme, theme.fallbackColors, ::parseHex)
                        for (alias in listOf(firstKey.keyBackColor, firstKey.keyTextColor)) {
                            ColorTable.resolveRaw(alias, scheme, theme.fallbackColors).shouldBeNull()
                            ColorTable.parseValue(alias, ::parseHex) shouldBe ColorTable.Value.None
                        }
                        table[ColorKey.KEY_BACK_COLOR] shouldBe ColorTable.Value.Color(pair.first)
                        table[ColorKey.KEY_TEXT_COLOR] shouldBe ColorTable.Value.Color(pair.second)
                    }
                }
            }

            Then("every palette preserves explicit key backgrounds and leaves absent aliases available for fallback") {
                val overrides = theme.presetKeyboards.values
                    .flatMap { it.keys }
                    .flatMap {
                        listOf(
                            it.keyBackColor to ColorKey.KEY_BACK_COLOR,
                            it.hilitedKeyBackColor to ColorKey.HILITED_KEY_BACK_COLOR,
                        )
                    }.filter { it.first.isNotEmpty() && ColorKey.from(it.first) == null }
                    .toSet()
                overrides.isNotEmpty() shouldBe true

                for ((id, scheme) in theme.presetColorSchemes) {
                    val table = ColorTable.resolve(scheme, theme.fallbackColors, ::parseHex)
                    for ((alias, generic) in overrides) {
                        withClue("$id / $alias -> ${generic.key}") {
                            val raw = ColorTable.resolveRaw(alias, scheme, theme.fallbackColors)
                            val value = ColorTable.parseValue(raw ?: alias, ::parseHex)
                            val explicit = scheme[alias]
                            if (explicit.isNullOrEmpty()) {
                                raw.shouldBeNull()
                                value shouldBe ColorTable.Value.None
                                // None must leave the generic background usable; it
                                // must not become a successful transparent override.
                                table[generic] shouldNotBe ColorTable.Value.None
                            } else {
                                raw shouldBe explicit
                                value shouldBe ColorTable.Value.Color(requireNotNull(parseHex(explicit)))
                            }
                        }
                    }
                }
            }
        }

        Given("literal drawable values") {
            Then("explicit zero-alpha colors remain colors rather than missing values") {
                for (literal in listOf("0x00", "0x00000000")) {
                    ColorTable.parseValue(literal, ::parseHex) shouldBe ColorTable.Value.Color(0)
                    val table = ColorTable.resolve(mapOf("key_back_color" to literal), emptyMap(), ::parseHex)
                    table[ColorKey.KEY_BACK_COLOR] shouldBe ColorTable.Value.Color(0)
                }
                ColorTable.parseValue("#ffffff", ::parseHex) shouldBe ColorTable.Value.Color(0xffffffff.toInt())
                ColorTable.parseValue("0x224466", ::parseHex) shouldBe ColorTable.Value.Color(0xff224466.toInt())
            }

            Then("invalid colors and unresolved aliases do not masquerade as transparent") {
                for (value in listOf("", "bh1", "missing_background", "not-a-color", "#zzzzzz", "0x")) {
                    ColorTable.parseValue(value, ::parseHex) shouldBe ColorTable.Value.None
                }
                ColorTable.resolveRaw("missing_background", emptyMap(), emptyMap()).shouldBeNull()
                val table = ColorTable.resolve(mapOf("key_back_color" to "not-a-color"), emptyMap(), ::parseHex)
                table[ColorKey.KEY_BACK_COLOR] shouldBe ColorTable.Value.None
            }

            Then("image paths remain images through both entry points") {
                for (path in listOf("paper.png", "paper.9.png", "night.webp", "photo.jpg", "animated.gif")) {
                    ColorTable.parseValue(path, ::parseHex) shouldBe ColorTable.Value.Image(path)
                    val table = ColorTable.resolve(mapOf("key_back_color" to path), emptyMap(), ::parseHex)
                    table[ColorKey.KEY_BACK_COLOR] shouldBe ColorTable.Value.Image(path)
                }
            }
        }

        Given("a third-party theme with custom fallback chains") {
            val scheme = mapOf("brand_back" to "#224466", "back_color" to "#ffffff")
            val fallbacks = mapOf(
                "key_back_color" to "custom_back",
                "custom_back" to "brand_back",
            )

            Then("custom chains override the built-in background fallback") {
                val raw = ColorTable.resolveRaw("custom_back", scheme, fallbacks)
                raw shouldBe "#224466"
                ColorTable.parseValue(requireNotNull(raw), ::parseHex) shouldBe ColorTable.Value.Color(0xff224466.toInt())
                ColorTable.resolve(scheme, fallbacks, ::parseHex)[ColorKey.KEY_BACK_COLOR] shouldBe
                    ColorTable.Value.Color(0xff224466.toInt())
            }

            Then("a direct custom value keeps priority over its fallback chain") {
                val overridden = scheme + ("custom_back" to "0x00")
                ColorTable.resolveRaw("custom_back", overridden, fallbacks) shouldBe "0x00"
                ColorTable.resolve(overridden, fallbacks, ::parseHex)[ColorKey.KEY_BACK_COLOR] shouldBe
                    ColorTable.Value.Color(0)
            }
        }
    })
