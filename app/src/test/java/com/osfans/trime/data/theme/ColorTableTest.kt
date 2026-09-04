/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.ColorScheme
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit semantics of the pre-compiled color table: chain priority, empty-value
 * skipping, image/color domain split, cycle safety and diagnostics.
 */
class ColorTableTest :
    BehaviorSpec({
        fun scheme(vararg pairs: Pair<String, String>) = ColorScheme("test", pairs.toMap())

        /**
         * Fake ColorUtils.parseColor: supports #RRGGBB / #AARRGGBB and the
         * 0x forms; 8-digit values wrap to the signed Int representation.
         */
        val parseHex: (String) -> Int? = { s ->
            val digits =
                when {
                    s.startsWith("#") -> s.substring(1)
                    s.startsWith("0x", ignoreCase = true) -> s.substring(2)
                    else -> null
                }
            digits?.let { runCatching { java.lang.Long.parseLong(it, 16).toInt() }.getOrNull() }
        }

        Given("a scheme that defines only the chain terminal") {
            val table = ColorTable.resolve(scheme("text_color" to "#102030"), emptyMap(), parseHex)
            Then("candidate_text_color resolves through the built-in chain") {
                table[ColorKey.CANDIDATE_TEXT_COLOR].shouldBeInstanceOf<ColorTable.Value.Color>()
                    .argb shouldBe 0x102030
            }
        }
        Given("a scheme value that is empty") {
            val table =
                ColorTable.resolve(
                    scheme(
                        "candidate_text_color" to "",
                        "text_color" to "#112233",
                    ),
                    emptyMap(),
                    parseHex,
                )
            Then("the empty value is skipped like a missing entry") {
                table[ColorKey.CANDIDATE_TEXT_COLOR].shouldBeInstanceOf<ColorTable.Value.Color>()
                    .argb shouldBe 0x112233
            }
        }
        Given("a theme fallback_colors entry for a key with a built-in chain") {
            val table =
                ColorTable.resolve(
                    scheme("comment_text_color" to "#334455"),
                    mapOf("key_text_color" to "comment_text_color"),
                    parseHex,
                )
            Then("the theme fallback wins over the built-in chain") {
                table[ColorKey.KEY_TEXT_COLOR].shouldBeInstanceOf<ColorTable.Value.Color>()
                    .argb shouldBe 0x334455
            }
        }
        Given("a theme fallback pointing at a theme-defined key") {
            val table =
                ColorTable.resolve(
                    scheme("my_base" to "#556677"),
                    mapOf("key_text_color" to "my_base"),
                    parseHex,
                )
            Then("the raw target hop resolves like the legacy walk") {
                table[ColorKey.KEY_TEXT_COLOR].shouldBeInstanceOf<ColorTable.Value.Color>()
                    .argb shouldBe 0x556677
            }
        }
        Given("an image-valued color key") {
            val table = ColorTable.resolve(scheme("key_back_color" to "bg.png"), emptyMap(), parseHex)
            Then("the entry is an image, not a color") {
                table[ColorKey.KEY_BACK_COLOR].shouldBeInstanceOf<ColorTable.Value.Image>()
                    .path shouldBe "bg.png"
            }
        }
        Given("a scheme value that is neither image nor parseable color") {
            val table = ColorTable.resolve(scheme("candidate_text_color" to "not-a-color"), emptyMap(), parseHex)
            Then("the entry reports an invalid value and resolves to None") {
                table.invalidValues shouldContain ColorKey.CANDIDATE_TEXT_COLOR
                // Keys whose chain ends at the bad value inherit the failure.
                table.invalidValues shouldContain ColorKey.COMMENT_TEXT_COLOR
                table.invalidValues shouldContain ColorKey.KEY_TEXT_COLOR
                table[ColorKey.CANDIDATE_TEXT_COLOR] shouldBe ColorTable.Value.None
            }
        }
        Given("an empty scheme") {
            val table = ColorTable.resolve(scheme(), emptyMap(), parseHex)
            Then("keys with a built-in chain end up unresolved") {
                table.unresolvedKeys shouldContain ColorKey.CANDIDATE_TEXT_COLOR
                table.unresolvedKeys shouldContain ColorKey.HILITED_ON_KEY_BACK_COLOR
                table[ColorKey.CANDIDATE_TEXT_COLOR] shouldBe ColorTable.Value.None
            }
        }
        Given("a cyclic theme fallback_colors") {
            val cyclic =
                mapOf(
                    "key_text_color" to "my_base",
                    "my_base" to "key_text_color",
                )
            Then("resolveRaw terminates and reports the key unresolved") {
                ColorTable.resolveRaw("key_text_color", emptyMap(), cyclic).shouldBeNull()
                val table = ColorTable.resolve(scheme(), cyclic, parseHex)
                table.unresolvedKeys shouldContain ColorKey.KEY_TEXT_COLOR
            }
        }
        Given("the image domain check") {
            Then("supported suffixes are images, colors are not") {
                ColorTable.isImageValue("bg.png") shouldBe true
                ColorTable.isImageValue("bg.9.png") shouldBe true
                ColorTable.isImageValue("bg.PNG") shouldBe false
                ColorTable.isImageValue("#ffffff") shouldBe false
                ColorTable.isImageValue("0xffffff") shouldBe false
            }
        }
        Given("every scheme of the built-in themes") {
            val builtinFallbackStrings = ColorKey.builtinFallbackColors.mapKeys { it.key.key }.mapValues { it.value.key }

            /** The pre-R2 resolveValue walk, replayed for comparison. */
            fun legacyWalk(
                key: String,
                schemeColors: Map<String, String>,
                fallbackColors: Map<String, String>,
            ): String? {
                var current = key
                while (true) {
                    val target = schemeColors[current]
                    if (!target.isNullOrEmpty()) return target
                    val fallback = fallbackColors[current]
                    if (!fallback.isNullOrEmpty()) {
                        current = fallback
                        continue
                    }
                    val altFallback = builtinFallbackStrings[current]
                    if (!altFallback.isNullOrEmpty()) {
                        current = altFallback
                    } else {
                        return null
                    }
                }
            }

            for (themeName in listOf("trime.yaml", "tongwenfeng.trime.yaml")) {
                val theme = ThemeTestSupport.decodeBuiltinTheme(themeName)
                When("the theme $themeName is loaded") {
                    for (scheme in theme.colorSchemes) {
                        val table = ColorTable.resolve(scheme, theme.fallbackColors, parseHex)
                        Then("scheme '${scheme.id}' resolves every key like the legacy walk") {
                            for (entry in ColorKey.entries) {
                                val expected = legacyWalk(entry.key, scheme.colors, theme.fallbackColors)
                                ColorTable.resolveRaw(entry.key, scheme.colors, theme.fallbackColors) shouldBe expected
                                if (expected == null) {
                                    table.unresolvedKeys shouldContain entry
                                } else {
                                    table.unresolvedKeys shouldNotContain entry
                                }
                            }
                        }
                    }
                }
            }
        }
        Given("candidate_border_color, which UI code requests but the built-in themes never define") {
            When("a built-in theme is resolved") {
                val theme = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")
                val table = ColorTable.resolve(theme.colorSchemes.first(), theme.fallbackColors, parseHex)
                Then("the key is reported unresolved and resolves to None") {
                    table.unresolvedKeys shouldContain ColorKey.CANDIDATE_BORDER_COLOR
                    table[ColorKey.CANDIDATE_BORDER_COLOR] shouldBe ColorTable.Value.None
                }
            }
        }
    })
