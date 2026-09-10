/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import java.io.File

/**
 * Decodes a theme from its source file, expanding the librime DSL subset the same way
 * [ThemeLoader] does but skipping the librime deploy step, which needs rime_jni and is
 * unavailable in JVM unit tests. Cross-file references cannot be resolved here; the
 * built-in themes only use same-file ones. Paths are relative to the app module directory
 * (the unit-test working directory).
 */
object ThemeTestSupport {
    fun decodeThemeFile(relativePath: String): Theme = themeAndNode(relativePath).first

    /** Decodes [relativePath] and also returns the expanded node it came from. */
    fun themeAndNode(relativePath: String): Pair<Theme, Node.Mapping> {
        val file = File(relativePath)
        check(file.isFile) { "Theme fixture not found: $relativePath (cwd=${File(".").absolutePath})" }
        val node = Yaml.parseToYamlNode(file.readText())
        val expanded = ThemeDslExpander.expand(file.nameWithoutExtension, node) { null }
        val mapping = expanded.mapping
            ?: error("$relativePath: YAML root is not a mapping")
        return Theme.decode(mapping) to mapping
    }

    /** Decodes a built-in theme source file (app/src/main/assets/shared/). */
    fun decodeBuiltinTheme(fileName: String): Theme = decodeThemeFile("src/main/assets/shared/$fileName")
}
