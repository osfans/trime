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
     * Loads [themeId], preferring its source files and falling back to the
     * librime-deployed artifact when the source cannot be read faithfully (see
     * [loadFromSource]). Never throws: returns [ThemeLoadResult.Success] or
     * [ThemeLoadResult.Failure] with a structured [ThemeLoadError].
     */
    fun loadTheme(themeId: String): ThemeLoadResult = loadFromSource(themeId) ?: loadDeployedTheme(themeId)

    /**
     * Reads [themeId] and its dependencies from source files, expands the
     * supported DSL subset and decodes the result. Returns null whenever the
     * source cannot be read faithfully — missing, unreadable, using DSL outside
     * the subset, or failing to decode — so the caller falls back to the
     * deployed artifact and librime decides what the file means.
     *
     * @param file source file of [themeId] when it is already known.
     * @param sources resource lookup; the data dirs by default, a fixture loader
     *   in tests.
     */
    internal fun loadFromSource(
        themeId: String,
        file: File? = null,
        sources: SourceLoader = SourceLoader(),
    ): ThemeLoadResult? {
        val node = sources.load(themeId, file) ?: return null
        return try {
            ThemeLoadResult.Success(themeId, decodeSource(themeId, node) { id -> sources.load(id, null) })
        } catch (e: ThemeDslExpander.UnsupportedDsl) {
            fallBack(themeId, e, "uses DSL outside the supported subset (%s)")
        } catch (e: ThemeDslExpander.UnresolvedReference) {
            fallBack(themeId, e, "has unresolved references (%s)")
        } catch (e: Exception) {
            fallBack(themeId, e, "cannot be decoded from its source (%s)")
        }
    }

    /** Reports why the source was not used and asks for the deployed artifact. */
    private fun fallBack(
        themeId: String,
        cause: Exception,
        reason: String,
    ): ThemeLoadResult? {
        Timber.w(cause, "Theme '%s' $reason, falling back to the deployed artifact", themeId, cause.message)
        return null
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
     * subset, so a name that an `__include` provides is resolved as well.
     * Returns null when the source is missing, unreadable, or uses DSL outside
     * the supported subset.
     *
     * @param sources resource lookup; the data dirs by default, a fixture loader
     *   in tests.
     */
    internal fun loadSourceNode(
        themeId: String,
        file: File? = null,
        sources: SourceLoader = SourceLoader(),
    ): Node? {
        val node = sources.load(themeId, file) ?: return null
        return runCatching {
            ThemeDslExpander.expand(themeId, node) { id -> sources.load(id, null) }
        }.getOrNull()
    }

    /**
     * Applies the librime auto-patch convention: unless the root already has an
     * explicit `__patch`, the `patch` node of `<id>.custom.yaml` is applied on
     * top of the resource. Reads `trime.yaml` source files (user data first,
     * then shared data) the same way librime resolves resources.
     *
     * @param findSource source file of a resource id; the data dirs by default.
     */
    internal class SourceLoader(
        private val findSource: (String) -> File? = ::findSourceFile,
    ) {
        private val cache = HashMap<String, Node?>()

        /**
         * @param file source file of [resourceId] when it is already known.
         *   Included resources are always looked up by id.
         */
        fun load(resourceId: String, file: File?): Node? {
            if (cache.containsKey(resourceId)) return cache[resourceId]
            val source = file ?: findSource(resourceId)
            val node = source?.let { runCatching { Yaml.parseToYamlNode(it.readText()) }.getOrNull() }
            val result = node?.let { applyCustomPatch(resourceId, it) }
            cache[resourceId] = result
            return result
        }
    }

    /** Source file of [resourceId]: the user data dir first, then the shared one. */
    private fun findSourceFile(resourceId: String): File? = findSourceFile(resourceId, listOf(DataManager.userDataDir, DataManager.sharedDataDir))

    /**
     * Source file of [resourceId] under [roots], in order. A resource id is free
     * text in an include directive, so a file that resolves outside the root it
     * was found in is refused; librime still resolves such an id on its own, so
     * the caller falls back to the deployed artifact.
     */
    internal fun findSourceFile(
        resourceId: String,
        roots: List<File>,
    ): File? {
        val relative = "$resourceId.yaml"
        return roots.firstNotNullOfOrNull { root ->
            root.resolve(relative).takeIf { it.isFile && it.isInside(root) }
        }
    }

    /** Whether this file really lives in [root]: `..` and absolute ids are escapes. */
    private fun File.isInside(root: File): Boolean = runCatching {
        canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    }.getOrDefault(false)

    /**
     * Injects the patch of `<id>.custom.yaml` as librime's auto-patch plugin
     * does: the optional reference `__patch: <id>.custom:/patch?`, resolved in
     * that resource. Reading it as a resource keeps the patch's own directives
     * (an `__include`, for instance) relative to the file they are written in.
     * An explicit root `__patch` wins; `.custom` files are never patched.
     */
    internal fun applyCustomPatch(
        resourceId: String,
        node: Node,
    ): Node {
        if (resourceId.endsWith(".custom")) return node
        val root = node as? Node.Mapping ?: return node
        if (root[PATCH] != null) return node
        val patchId = resourceId.removeSuffix(".schema") + ".custom"
        val reference = Node.Scalar("$patchId:/patch?")
        return Node.Mapping(root.pairs + (Node.Scalar(PATCH) to reference), root.anchor)
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
