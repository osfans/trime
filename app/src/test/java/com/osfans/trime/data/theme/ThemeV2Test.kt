/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.v2.CandidateBarStyle
import com.osfans.trime.data.theme.model.v2.ThemeV2
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeV2Test {
    private fun decode(yaml: String): ThemeV2 {
        val node = ThemeYamlV2.parser.parseToYamlNode(yaml)
        val mapping = node.mapping ?: error("root is not a mapping")
        return ThemeYamlV2.parser.decodeFromYamlNode<ThemeV2>(mapping)
    }

    @Test
    fun `decode minimal v2 theme`() {
        val theme =
            decode(
                """
                version: "1.0"
                name: 测试主题
                author: 测试者
                keyboard:
                  height: 250
                  keyHeight: 44
                  textSize: 22
                candidateBar:
                  viewHeight: 28
                  commentPosition: right
                colorSchemas:
                  light:
                    backColor: 0xe4e7e9
                    textColor: 0x5a676e
                  dark:
                    backColor: 0x222222
                    textColor: 0xcccccc
                """.trimIndent(),
            )
        assertEquals("测试主题", theme.name)
        assertEquals(250, theme.keyboard.height)
        assertEquals(44, theme.keyboard.keyHeight)
        assertEquals(22f, theme.keyboard.textSize, 0f)
        assertEquals(28, theme.candidateBar.viewHeight)
        assertEquals(CandidateBarStyle.CommentPosition.RIGHT, theme.candidateBar.commentPosition)
        assertEquals("0xe4e7e9", theme.colorSchemas.light["backColor"])
        assertEquals("0x5a676e", theme.colorSchemas.light["textColor"])
        assertEquals("0x222222", theme.colorSchemas.dark["backColor"])
        assertEquals("0xcccccc", theme.colorSchemas.dark["textColor"])
    }

    @Test
    fun `decode keyboard offset fields`() {
        val theme =
            decode(
                """
                keyboard:
                  textOffsetX: 2
                  textOffsetY: 1
                  pressOffsetX: 3
                  paddingLandscape: 40
                """.trimIndent(),
            )
        assertEquals(2f, theme.keyboard.textOffsetX, 0f)
        assertEquals(1f, theme.keyboard.textOffsetY, 0f)
        assertEquals(3f, theme.keyboard.pressOffsetX, 0f)
        assertEquals(40, theme.keyboard.paddingLandscape)
    }

    @Test
    fun `decode fonts and enterKey`() {
        val theme =
            decode(
                """
                fonts:
                  key: symbol.ttf
                  candidate: [han.ttf, fallback.ttf]
                enterKey:
                  mode: 2
                  labels:
                    send: 发送
                """.trimIndent(),
            )
        assertEquals(listOf("symbol.ttf"), theme.fontFaces.key.toList())
        assertEquals(listOf("han.ttf", "fallback.ttf"), theme.fontFaces.candidate.toList())
        assertEquals(2, theme.enterKey.mode)
        assertEquals("发送", theme.enterKey.labels.send)
    }

    @Test
    fun `decode symbolKeyboard with camelCase fixed keys`() {
        val theme =
            decode(
                """
                symbolKeyboard:
                  keyHeight: 40
                  singleWidth: 60
                  fixedKeyBar:
                    position: bottom
                    keys: [space1, BackSpace]
                  keyboards: [emoji]
                  emoji:
                    type: SINGLE
                    keys: "🙂😂"
                """.trimIndent(),
            )
        val sk = theme.symbolKeyboard
        assertEquals(40, sk.keyHeight)
        assertEquals(60, sk.singleWidth)
        assertEquals(1, sk.keyboards.size)
        assertEquals("emoji", sk.keyboards[0].id)
        assertEquals(2, sk.keyboards[0].keys.size)
    }

    @Test
    fun `decode keys and keyboards reuse legacy models`() {
        val theme =
            decode(
                """
                keys:
                  BackSpace: {label: 退格, repeatable: true, send: BackSpace}
                keyboards:
                  default:
                    name: 预设
                    keys:
                      - {click: q, longClick: "!"}
                      - {click: BackSpace, width: 15}
                """.trimIndent(),
            )
        assertEquals("退格", theme.keys["BackSpace"]?.label)
        assertEquals(true, theme.keys["BackSpace"]?.repeatable)
        assertEquals("预设", theme.keyboards["default"]?.name)
        assertEquals(2, theme.keyboards["default"]?.keys?.size)
    }
}
