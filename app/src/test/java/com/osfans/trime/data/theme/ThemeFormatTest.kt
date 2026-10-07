/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeFormatTest {
    private fun detect(yaml: String): ThemeFormat {
        val node = ThemeYaml.parser.parseToYamlNode(yaml)
        val mapping = node.mapping ?: error("root is not a mapping")
        return ThemeFormatDetector.detectFormat(mapping)
    }

    @Test
    fun `legacy theme with style and preset_color_schemes`() {
        val format =
            detect(
                """
                config_version: "3.0"
                style:
                  key_height: 44
                preset_color_schemes:
                  default:
                    text_color: 0x000000
                """.trimIndent(),
            )
        assertEquals(ThemeFormat.LEGACY, format)
    }

    @Test
    fun `legacy theme with snake_case sections`() {
        val format =
            detect(
                """
                name: legacy
                preset_keys:
                  BackSpace: {label: 退格}
                liquid_keyboard:
                  key_height: 40
                """.trimIndent(),
            )
        assertEquals(ThemeFormat.LEGACY, format)
    }

    @Test
    fun `v2 theme with keyboard and colorSchemas`() {
        val format =
            detect(
                """
                version: "1.0"
                keyboard:
                  height: 250
                colorSchemas:
                  default:
                    light:
                      backColor: 0xffffff
                """.trimIndent(),
            )
        assertEquals(ThemeFormat.V2, format)
    }

    @Test
    fun `v2 theme detected by camelCase sections alone`() {
        val format =
            detect(
                """
                name: v2
                candidateBar:
                  viewHeight: 28
                symbolKeyboard:
                  keyHeight: 40
                """.trimIndent(),
            )
        assertEquals(ThemeFormat.V2, format)
    }

    @Test
    fun `unrecognized mapping defaults to legacy`() {
        val format =
            detect(
                """
                name: foo
                author: bar
                """.trimIndent(),
            )
        assertEquals(ThemeFormat.LEGACY, format)
    }
}
