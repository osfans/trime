/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
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

    /** Decodes a built-in theme source file (app/src/main/assets/shared/). */
    fun decodeBuiltinTheme(fileName: String): Theme = decodeThemeFile("src/main/assets/shared/$fileName")
}
