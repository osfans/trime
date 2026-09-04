/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import java.io.File

/**
 * Decodes a theme from its source file (YAML parse + [Theme.decode]), skipping the librime
 * deploy step, which needs rime_jni and is unavailable in JVM unit tests. trime.yaml's
 * `__include` entries are librime DSL: at source level they decode as default keyboards
 * with no keys (see [ThemeGoldenTest]). Paths are relative to the app module directory
 * (the unit-test working directory).
 */
object ThemeTestSupport {
    fun decodeThemeFile(relativePath: String): Theme {
        val file = File(relativePath)
        check(file.isFile) { "Theme fixture not found: $relativePath (cwd=${File(".").absolutePath})" }
        val mapping = Yaml.parseToYamlNode(file.readText()).mapping
            ?: error("$relativePath: YAML root is not a mapping")
        return Theme.decode(mapping)
    }

    /** Decodes a built-in theme source file (app/src/main/assets/shared/). */
    fun decodeBuiltinTheme(fileName: String): Theme = decodeThemeFile("src/main/assets/shared/$fileName")
}
