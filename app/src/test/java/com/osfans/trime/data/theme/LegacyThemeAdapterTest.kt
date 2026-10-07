/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyThemeAdapterTest {
    @Test
    fun `adapting the builtin theme preserves its structure`() {
        val legacy = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")
        val v2 = LegacyThemeAdapter.toV2(legacy)

        assertEquals(legacy.name, v2.name)
        assertEquals(legacy.presetKeyboards.size, v2.keyboards.size)
        assertEquals(legacy.presetKeys.size, v2.keys.size)
        // 旧的多 scheme 合并为严格的 light/dark 两个配色。
        assertTrue(v2.colorSchemas.light.isNotEmpty())
        assertEquals(legacy.liquidKeyboard.keyboards.size, v2.symbolKeyboard.keyboards.size)
    }

    @Test
    fun `adapting collapses schemes into light and dark palettes`() {
        val legacy = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")
        val v2 = LegacyThemeAdapter.toV2(legacy)

        // The legacy scheme declares snake_case keys; the adapter renames them.
        assertTrue(v2.colorSchemas.light.keys.any { it == "backColor" || it == "textColor" || it == "keyTextColor" })
        // A theme without day/night links reuses the base palette for both modes.
        assertTrue(v2.colorSchemas.dark.isNotEmpty())
    }

    @Test
    fun `style fields round-trip through the adapter and back`() {
        val legacy = ThemeTestSupport.decodeBuiltinTheme("trime.yaml")
        val v2 = LegacyThemeAdapter.toV2(legacy)

        val roundTripped =
            GeneralStyleAdapter.fromV2(
                v2.keyboard,
                v2.candidateBar,
                v2.popup,
                v2.fontFaces,
                v2.enterKey,
            )
        // autoCaps is nullable in the legacy model; null and false are equivalent.
        assertEquals(
            legacy.style.copy(autoCaps = legacy.style.autoCaps ?: false),
            roundTripped,
        )
    }
}
