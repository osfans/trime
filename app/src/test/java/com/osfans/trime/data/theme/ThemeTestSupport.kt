/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.osfans.trime.data.theme.model.v2.ThemeV2
import com.osfans.trime.util.mapping
import java.io.File

/**
 * Decodes a theme from its source file, expanding the librime DSL subset the same way
 * [ThemeLoader] does but skipping the librime deploy step, which needs rime_jni and is
 * unavailable in JVM unit tests. Cross-file references cannot be resolved here; the
 * built-in themes only use same-file ones. Paths are relative to the app module directory
 * (the unit-test working directory).
 */
object ThemeTestSupport {
    /** The parser themes are read with in production, so fixtures see the same configuration. */
    val yaml = ThemeYaml.parser

    fun decodeThemeFile(relativePath: String): Theme = themeAndNode(relativePath).first

    /** Decodes [relativePath] and also returns the expanded node it came from. */
    fun themeAndNode(relativePath: String): Pair<Theme, YamlMap> {
        val file = File(relativePath)
        check(file.isFile) { "Theme fixture not found: $relativePath (cwd=${File(".").absolutePath})" }
        val node = yaml.parseToYamlNode(file.readText())
        val expanded = ThemeDslExpander.expand(file.nameWithoutExtension, node) { null }
        val mapping = expanded.mapping
            ?: error("$relativePath: YAML root is not a mapping")
        return yaml.decodeFromYamlNode<Theme>(mapping) to mapping
    }

    /**
     * Decodes a legacy-format test fixture (app/src/test/assets/). The built-in
     * themes shipped under app/src/main/assets/shared are now V2 format, so the
     * legacy snake_case fixtures that exercise the legacy parser live here.
     */
    fun decodeBuiltinTheme(fileName: String): Theme = decodeThemeFile("src/test/assets/$fileName")

    /** Decodes a built-in V2 theme source file (app/src/main/assets/shared/). */
    fun decodeBuiltinThemeV2(fileName: String): ThemeV2 {
        val node = ThemeYamlV2.parser.parseToYamlNode(File("src/main/assets/shared/$fileName").readText())
        return ThemeYamlV2.parser.decodeFromYamlNode<ThemeV2>(node)
    }
}
