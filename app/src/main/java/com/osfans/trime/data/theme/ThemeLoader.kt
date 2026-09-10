/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.core.Rime
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import timber.log.Timber
import java.io.File

/**
 * Loads a theme from its source YAML, expanding the supported librime DSL
 * subset directly. Themes using constructs outside that subset fall back to
 * the librime-deployed artifact. Failures are reported as [ThemeLoadError]
 * instead of a bare log line, so callers can fall back and surface
 * diagnostics (YAML syntax errors carry line/column info).
 */
object ThemeLoader {
    const val CONFIG_VERSION_KEY = "config_version"

    private const val INCLUDE = "__include"
    private const val PATCH = "__patch"

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
    fun loadTheme(themeId: String): ThemeLoadResult = loadFromSource(themeId) ?: loadDeployedTheme(themeId)

    /**
     * Reads [themeId] and its dependencies from source files, expands the
     * supported DSL subset and decodes the result. Returns null when the source
     * is missing, unreadable, or uses DSL outside the supported subset, so the
     * caller can fall back to the deployed artifact.
     */
    private fun loadFromSource(themeId: String): ThemeLoadResult? {
        val sources = SourceLoader()
        val node = sources.load(themeId, null) ?: return null
        return try {
            ThemeLoadResult.Success(themeId, decodeSource(themeId, node) { id -> sources.load(id, null) })
        } catch (e: ThemeDslExpander.UnsupportedDsl) {
            Timber.w("Theme '%s' uses unsupported DSL (%s), falling back to the deployed artifact", themeId, e.message)
            null
        } catch (e: ThemeDslExpander.UnresolvedReference) {
            Timber.w("Theme '%s' has unresolved references (%s), falling back to the deployed artifact", themeId, e.message)
            null
        }
    }

    /**
     * Expands [node] with [loadResource] and decodes the result. Kept separate
     * from the file lookup so tests can feed fixture resources.
     */
    internal fun decodeSource(
        themeId: String,
        node: Node,
        loadResource: (String) -> Node?,
    ): Theme {
        val expanded = ThemeDslExpander.expand(themeId, node, loadResource)
        val mapping = expanded.mapping
            ?: throw ThemeLoadError.InvalidStructure(themeId, "YAML root is not a mapping")
        return Theme.decode(mapping)
    }

    /**
     * Reads [themeId] from [file], or looks its source up in the user and
     * shared data dirs when [file] is null, and expands the supported DSL
     * subset. Returns null when the source is missing, unreadable, or uses DSL
     * outside the supported subset.
     */
    internal fun loadSourceNode(
        themeId: String,
        file: File? = null,
    ): Node? = SourceLoader().load(themeId, file)

    /**
     * Applies the librime auto-patch convention: unless the root already has an
     * explicit `__patch`, the `patch` node of `<id>.custom.yaml` is applied on
     * top of the resource. Reads `trime.yaml` source files (user data first,
     * then shared data) the same way librime resolves resources.
     */
    private class SourceLoader {
        private val cache = HashMap<String, Node?>()

        /**
         * @param file source file of [resourceId] when it is already known.
         *   Included resources are always looked up by id.
         */
        fun load(resourceId: String, file: File?): Node? {
            if (cache.containsKey(resourceId)) return cache[resourceId]
            val source = file ?: findSource(resourceId)
            val node = source?.let { runCatching { Yaml.parseToYamlNode(it.readText()) }.getOrNull() }
            val result = node?.let { applyCustomPatch(resourceId, it) { id -> load(id, null) } }
            cache[resourceId] = result
            return result
        }

        private fun findSource(resourceId: String): File? {
            val relative = "$resourceId.yaml"
            return listOf(DataManager.userDataDir, DataManager.sharedDataDir)
                .firstNotNullOfOrNull { it.resolve(relative).takeIf(File::isFile) }
        }
    }

    /**
     * Injects the `patch` node of `<id>.custom.yaml` as an `__patch` directive,
     * mirroring librime's auto-patch plugin. An explicit root `__patch` wins.
     */
    internal fun applyCustomPatch(
        resourceId: String,
        node: Node,
        loadResource: (String) -> Node?,
    ): Node {
        if (resourceId.endsWith(".custom")) return node
        val root = node as? Node.Mapping ?: return node
        if (root[PATCH] != null) return node
        val patchId = resourceId.removeSuffix(".schema") + ".custom"
        val patch = loadResource(patchId)?.mapping?.get("patch") as? Node.Mapping ?: return node
        return Node.Mapping(root.pairs + (Node.Scalar(PATCH) to patch), root.anchor)
    }

    /** Loads the theme from its librime-deployed artifact. */
    private fun loadDeployedTheme(themeId: String): ThemeLoadResult {
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
