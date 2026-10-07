// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.charleskorn.kaml.yamlMap
import com.osfans.trime.data.theme.ThemeDiagnostics.Code
import com.osfans.trime.data.theme.ThemeDiagnostics.Severity
import com.osfans.trime.data.theme.model.v2.ThemeV2
import com.osfans.trime.ime.keyboard.KeyCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ThemeDiagnosticsV2Test :
    BehaviorSpec({
        beforeTest { KeyCode.androidKeyNameToCode = { 0 } }

        fun parseHex(value: String): Int? {
            val digits = when {
                value.startsWith("#") -> value.substring(1)
                value.startsWith("0x", ignoreCase = true) -> value.substring(2)
                else -> null
            }
            return digits?.let { runCatching { java.lang.Long.parseLong(it, 16).toInt() }.getOrNull() }
        }

        fun lintV2(yaml: String): List<ThemeDiagnostics.Finding> {
            val node = ThemeYamlV2.parser.parseToYamlNode(yaml)
            val theme = ThemeYamlV2.parser.decodeFromYamlNode<ThemeV2>(node)
            return ThemeDiagnostics.lintV2(theme, node.yamlMap, ::parseHex)
        }

        Given("a minimal valid V2 theme") {
            Then("nothing is reported") {
                lintV2(
                    """
                    version: "1.0"
                    name: minimal
                    keyboard: {textSize: 20}
                    colorSchemas:
                      light: {backColor: "#000000"}
                      dark: {backColor: "#111111"}
                    keys:
                      A: {label: a, send: a}
                    keyboards:
                      letter: {name: letter, keys: [{click: a}]}
                    """.trimIndent(),
                ) shouldBe emptyList()
            }
        }

        Given("unknown top-level keys") {
            Then("they are reported once each") {
                val findings =
                    lintV2(
                        """
                        keyboard: {textSize: 20}
                        colorSchemas: {light: {backColor: "#000000"}, dark: {backColor: "#111111"}}
                        height: 5
                        """.trimIndent(),
                    )
                findings.filter { it.code == Code.UNKNOWN_TOP_LEVEL_KEY } shouldContainExactlyInAnyOrder
                    listOf(
                        ThemeDiagnostics.Finding(
                            Severity.INFO,
                            Code.UNKNOWN_TOP_LEVEL_KEY,
                            "unknown key 'height' in ''; the runtime ignores it",
                            "/height",
                        ),
                    )
            }
        }

        Given("unknown section keys") {
            Then("they are reported under their section") {
                val findings =
                    lintV2(
                        """
                        keyboard: {textSize: 20, bogusKey: 1}
                        colorSchemas: {light: {backColor: "#000000"}, dark: {backColor: "#111111"}}
                        """.trimIndent(),
                    )
                findings.filter { it.code == Code.UNKNOWN_STYLE_KEY } shouldContainExactlyInAnyOrder
                    listOf(
                        ThemeDiagnostics.Finding(
                            Severity.WARNING,
                            Code.UNKNOWN_STYLE_KEY,
                            "unknown key 'bogusKey' in 'keyboard'; the runtime ignores it",
                            "keyboard/bogusKey",
                        ),
                    )
            }
        }

        Given("an unparseable color value") {
            Then("it is reported at the palette that carries it") {
                val findings =
                    lintV2(
                        """
                        colorSchemas:
                          light: {backColor: "not-a-color"}
                          dark: {backColor: "#111111"}
                        """.trimIndent(),
                    )
                findings.filter { it.code == Code.INVALID_COLOR_VALUE } shouldBe
                    listOf(
                        ThemeDiagnostics.Finding(
                            Severity.WARNING,
                            Code.INVALID_COLOR_VALUE,
                            "scheme 'light': 'backColor' cannot be parsed as a color (value 'not-a-color')",
                            "colorSchemas/light/backColor",
                        ),
                    )
            }
        }

        Given("a V2 theme with no color schemes") {
            Then("NO_COLOR_SCHEME is reported") {
                lintV2(
                    """
                    keyboard: {textSize: 20}
                    """.trimIndent(),
                ).filter { it.code == Code.NO_COLOR_SCHEME } shouldBe
                    listOf(
                        ThemeDiagnostics.Finding(
                            Severity.WARNING,
                            Code.NO_COLOR_SCHEME,
                            "no 'colorSchemas'; the keyboard cannot resolve any color",
                        ),
                    )
            }
        }
    })
