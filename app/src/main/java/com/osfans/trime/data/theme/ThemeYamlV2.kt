/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.Yaml as KamlYaml

/**
 * The parser for V2 theme files.
 *
 * V2 keys are already camelCase, so this parser uses kaml's default naming
 * (property name as-is) instead of [ThemeYaml]'s snake_case conversion. All
 * other safety knobs mirror [ThemeYaml] so a hostile file stays bounded.
 */
object ThemeYamlV2 {
    /** Alias budget, mirroring [ThemeYaml]. */
    private const val MAX_ALIAS_COUNT = 1000u

    /** Input limit, mirroring [ThemeYaml]. */
    private const val CODE_POINT_LIMIT = 10 * 1024 * 1024

    val parser: KamlYaml =
        KamlYaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                    decodeEnumCaseInsensitive = true,
                    anchorsAndAliases = AnchorsAndAliases.Permitted(MAX_ALIAS_COUNT),
                    codePointLimit = CODE_POINT_LIMIT,
                ),
        )
}
