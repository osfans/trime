/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import com.charleskorn.kaml.Yaml as KamlYaml

/**
 * The parser every theme file goes through: source themes, the artifacts
 * librime deploys from them, and the theme names listed in the picker.
 *
 * Some settings deviate from kaml's defaults:
 * - a theme bean is decoded from `snake_case` keys, and an unknown key is a
 *   diagnostic instead of an error, so `strictMode` is off and a naming
 *   strategy converts property names;
 * - theme sources are hand-written and derive metrics and colors from anchors
 *   and aliases (`tongwenfeng.trime.yaml`), so those are permitted.
 *
 * Documents stay bounded: [CODE_POINT_LIMIT] matches what the theme reader used
 * to configure, and [MAX_ALIAS_COUNT] is a finite alias budget, so a hostile
 * file cannot make parsing exhaust memory.
 *
 * YAML outside themes has its own scope: sound effect descriptors use their own
 * key spelling (see `SoundEffectManager`), and `installation.yaml` is written by
 * librime (see `SyncPathPolicy`).
 */
object ThemeYaml {
    /**
     * Alias budget: it was 200, which real themes exceeded, and kaml's own
     * default of 100 is below what `tongwenfeng.trime.yaml` resolves. An alias
     * counts together with the accumulated weight of the nodes it resolves, so
     * a document whose aliases expand into further aliases still fails early,
     * while hand-written themes stay far below it.
     */
    private const val MAX_ALIAS_COUNT = 1000u

    /** Input limit of the theme reader this replaced. */
    private const val CODE_POINT_LIMIT = 10 * 1024 * 1024

    val parser: KamlYaml = KamlYaml(
        configuration = YamlConfiguration(
            strictMode = false,
            decodeEnumCaseInsensitive = true,
            yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
            anchorsAndAliases = AnchorsAndAliases.Permitted(MAX_ALIAS_COUNT),
            codePointLimit = CODE_POINT_LIMIT,
        ),
    )
}
