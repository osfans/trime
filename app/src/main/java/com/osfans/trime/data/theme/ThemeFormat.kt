/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.osfans.trime.util.pairs

/**
 * The format of a theme source file.
 *
 * [LEGACY] is the original Trime format: a single file mixing `style`,
 * `preset_color_schemes`, `fallback_colors`, `preset_keys`, `preset_keyboards`
 * and `liquid_keyboard` sections, written in snake_case and historically read
 * through the librime deployment channel.
 *
 * [V2] is the new, decoupled format aligned with the Hamster / 元书 skin
 * standard: semantic camelCase keys grouped into `keyboard`, `candidateBar`,
 * `popup`, `fonts`, `enterKey`, `toolbar`, `preedit`, `window`,
 * `colorSchemas` (with inline `light` / `dark` palettes), `fallbackColors`,
 * `keys`, `keyboards` and `symbolKeyboard` sections.
 */
enum class ThemeFormat {
    LEGACY,
    V2,
}

object ThemeFormatDetector {
    /**
     * Top-level keys that only a V2 theme declares. The legacy format spells
     * the corresponding concepts in snake_case (`tool_bar`, `fallback_colors`,
     * `preset_keys`, `preset_keyboards`, `liquid_keyboard`, `preset_color_schemes`),
     * so the presence of any camelCase V2 key is an unambiguous signal.
     */
    private val V2_ONLY_KEYS: Set<String> =
        setOf(
            "keyboard",
            "candidateBar",
            "popup",
            "fonts",
            "enterKey",
            "colorSchemas",
            "symbolKeyboard",
            "keys",
            "keyboards",
            "toolbar",
            "fallbackColors",
        )

    /**
     * Detects the format of [map], the parsed root of a theme file.
     *
     * Detection runs on the raw YAML mapping (before any naming strategy
     * applies), so key spelling is exactly what the file contains. Anything
     * without a V2-only key is treated as legacy, which keeps hand-written
     * legacy themes working.
     */
    fun detectFormat(map: YamlMap): ThemeFormat =
        if (map.pairs.keys.any { it in V2_ONLY_KEYS }) ThemeFormat.V2 else ThemeFormat.LEGACY
}
