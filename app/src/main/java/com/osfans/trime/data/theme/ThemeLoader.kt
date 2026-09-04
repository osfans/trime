/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import timber.log.Timber
import java.io.File

/**
 * Loads a theme from its deployed artifact: librime deploy -> read -> YAML parse -> [Theme] decode.
 * Failures are reported as [ThemeLoadError] instead of a bare log line, so callers can fall back
 * and surface diagnostics (YAML syntax errors carry line/column info).
 */
object ThemeLoader {
    const val CONFIG_VERSION_KEY = "config_version"

    /** Structured failure of a single theme load. */
    sealed class ThemeLoadError(
        val themeId: String,
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause) {
        class FileNotFound(
            themeId: String,
            val path: String,
        ) : ThemeLoadError(themeId, "Deployed theme file not found: $path")

        class FileUnreadable(
            themeId: String,
            val path: String,
            cause: Throwable,
        ) : ThemeLoadError(themeId, "Cannot read theme file: $path", cause)

        /** Not valid YAML; [cause] message usually includes line/column info. */
        class YamlParseError(
            themeId: String,
            cause: Throwable,
        ) : ThemeLoadError(themeId, "Failed to parse theme YAML: ${cause.message}", cause)

        /** Root is not a mapping, or the theme structure is invalid. */
        class InvalidStructure(
            themeId: String,
            detail: String,
            cause: Throwable? = null,
        ) : ThemeLoadError(themeId, "Invalid theme structure: $detail", cause)
    }

    sealed interface ThemeLoadResult {
        data class Success(
            val themeId: String,
            val theme: Theme,
        ) : ThemeLoadResult

        data class Failure(
            val themeId: String,
            val error: ThemeLoadError,
        ) : ThemeLoadResult
    }

    /**
     * Never throws and never falls back: returns [ThemeLoadResult.Success] or
     * [ThemeLoadResult.Failure] with a structured [ThemeLoadError].
     */
    fun loadTheme(themeId: String): ThemeLoadResult {
        // Returns false when the artifact is already up to date (mtime cache), which is fine.
        if (!Rime.deployRimeConfigFile(themeId, CONFIG_VERSION_KEY)) {
            Timber.w("Failed to deploy theme config file '$themeId.yaml'")
        }

        val path = DataManager.resolveDeployedResourcePath(themeId)
        val file = File(path)
        if (!file.exists()) {
            return ThemeLoadResult.Failure(themeId, ThemeLoadError.FileNotFound(themeId, path))
        }
        val content =
            try {
                file.readText()
            } catch (e: Exception) {
                return ThemeLoadResult.Failure(
                    themeId,
                    ThemeLoadError.FileUnreadable(themeId, path, e),
                )
            }

        val node =
            try {
                Yaml.parseToYamlNode(content)
            } catch (e: Exception) {
                return ThemeLoadResult.Failure(
                    themeId,
                    ThemeLoadError.YamlParseError(themeId, e),
                )
            }
        val mapping = node.mapping ?: return ThemeLoadResult.Failure(
            themeId,
            ThemeLoadError.InvalidStructure(themeId, "YAML root is not a mapping"),
        )

        val theme =
            try {
                Theme.decode(mapping)
            } catch (e: Exception) {
                return ThemeLoadResult.Failure(
                    themeId,
                    ThemeLoadError.InvalidStructure(themeId, "Decode failed", e),
                )
            }
        return ThemeLoadResult.Success(themeId, theme)
    }
}
