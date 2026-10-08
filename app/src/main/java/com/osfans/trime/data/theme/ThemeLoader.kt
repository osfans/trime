/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.theme.model.v2.ThemeV2
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import timber.log.Timber
import java.io.File

/**
 * Loads a theme straight from its source YAML, fully decoupled from the
 * librime deployment channel.
 *
 * A theme is parsed, its `__include` / `__patch` directives are expanded by
 * [ThemeDslExpander], and the result is decoded as either the V2 format
 * (camelCase, Hamster-aligned) or the legacy format (snake_case), which is then
 * adapted to [ThemeV2]. Constructs outside the supported DSL subset, and
 * malformed files, are reported as structured [ThemeLoadError]s instead of a
 * bare log line, so callers can fall back and surface diagnostics.
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
        ) : ThemeLoadError(themeId, "Theme file not found: $path")

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

        /** The theme declares no color scheme, so there is nothing to render it with. */
        class NoColorScheme(
            themeId: String,
        ) : ThemeLoadError(themeId, "No color scheme is defined")
    }

    sealed interface ThemeLoadResult {
        data class Success(
            val themeId: String,
            val theme: ThemeV2,
            /**
             * What static checks found in the theme, or null when the checks
             * could not run (or have not been implemented for that format).
             */
            val findings: List<ThemeDiagnostics.Finding>? = null,
        ) : ThemeLoadResult

        data class Failure(
            val themeId: String,
            val error: ThemeLoadError,
        ) : ThemeLoadResult
    }

    /**
     * Loads [themeId]. Never throws: returns [ThemeLoadResult.Success] or
     * [ThemeLoadResult.Failure] with a structured [ThemeLoadError].
     */
    fun loadTheme(themeId: String): ThemeLoadResult = loadFromSource(themeId)

    /**
     * Reads [themeId] and its dependencies from source files, expands the
     * supported DSL subset and decodes the result. Returns a structured
     * failure instead of falling back to a librime deployment, which no
     * longer exists in the decoupled pipeline.
     *
     * @param file source file of [themeId] when it is already known.
     * @param sources resource lookup; the data dirs by default, a fixture loader
     *   in tests.
     */
    internal fun loadFromSource(
        themeId: String,
        file: File? = null,
        sources: SourceLoader = SourceLoader(),
    ): ThemeLoadResult {
        val node = sources.load(themeId, file)
        if (node == null) {
            val sourceFile = file ?: sources.findFile(themeId)
            return if (sourceFile == null) {
                ThemeLoadResult.Failure(themeId, ThemeLoadError.FileNotFound(themeId, "$themeId.yaml"))
            } else {
                ThemeLoadResult.Failure(
                    themeId,
                    ThemeLoadError.YamlParseError(themeId, IllegalStateException("cannot parse ${sourceFile.name}")),
                )
            }
        }
        return decodeFromSource(themeId, node, sources)
    }

    /** Expands [node] and decodes it, reporting failures structurally. */
    private fun decodeFromSource(
        themeId: String,
        node: YamlNode,
        sources: SourceLoader,
    ): ThemeLoadResult =
        try {
            val expanded = ThemeDslExpander.expand(themeId, node) { id -> sources.load(id, null) }
            val mapping = expanded.mapping
                ?: return ThemeLoadResult.Failure(themeId, ThemeLoadError.InvalidStructure(themeId, "YAML root is not a mapping"))
            decodeAndReport(themeId, mapping)
        } catch (e: ThemeDslExpander.UnsupportedDsl) {
            ThemeLoadResult.Failure(themeId, ThemeLoadError.InvalidStructure(themeId, "unsupported librime DSL: ${e.message}", e))
        } catch (e: ThemeDslExpander.UnresolvedReference) {
            ThemeLoadResult.Failure(themeId, ThemeLoadError.InvalidStructure(themeId, "unresolved reference: ${e.message}", e))
        } catch (e: ThemeLoadError) {
            ThemeLoadResult.Failure(themeId, e)
        } catch (e: Exception) {
            ThemeLoadResult.Failure(themeId, ThemeLoadError.InvalidStructure(themeId, "cannot be decoded: ${e.message}", e))
        }

    /**
     * Decodes a theme file by format. Legacy themes are decoded with the
     * snake_case parser and adapted to [ThemeV2]; V2 themes decode directly.
     * A theme that declares no color scheme is refused.
     */
    internal fun decodeAndReport(
        themeId: String,
        mapping: YamlMap,
    ): ThemeLoadResult =
        when (ThemeFormatDetector.detectFormat(mapping)) {
            ThemeFormat.V2 -> {
                val themeV2 = ThemeYamlV2.parser.decodeFromYamlNode<ThemeV2>(mapping)
                if (themeV2.colorSchemas.isEmpty()) {
                    return ThemeLoadResult.Failure(themeId, ThemeLoadError.NoColorScheme(themeId))
                }
                val findings =
                    runCatching { ThemeDiagnostics.lintV2(themeV2, mapping) }
                        .onFailure { Timber.w(it, "Theme '%s': diagnostics failed", themeId) }
                        .getOrNull()
                findings?.let { ThemeDiagnostics.log(themeId, it) }
                ThemeLoadResult.Success(themeId, themeV2, findings)
            }

            ThemeFormat.LEGACY -> {
                val legacy = ThemeYaml.parser.decodeFromYamlNode<Theme>(mapping)
                val themeV2 = LegacyThemeAdapter.toV2(legacy)
                if (themeV2.colorSchemas.isEmpty()) {
                    return ThemeLoadResult.Failure(themeId, ThemeLoadError.NoColorScheme(themeId))
                }
                val findings =
                    runCatching { ThemeDiagnostics.lint(legacy, mapping) }
                        .onFailure { Timber.w(it, "Theme '%s': diagnostics failed", themeId) }
                        .getOrNull()
                findings?.let { ThemeDiagnostics.log(themeId, it) }
                ThemeLoadResult.Success(themeId, themeV2, findings)
            }
        }

    /**
     * Expands [node] with [loadResource] and decodes the result to [ThemeV2].
     * Kept separate from the file lookup so tests can feed fixture resources.
     */
    internal fun decodeSource(
        themeId: String,
        node: YamlNode,
        loadResource: (String) -> YamlNode?,
    ): ThemeV2 {
        val expanded = ThemeDslExpander.expand(themeId, node, loadResource)
        val mapping = expanded.mapping
            ?: throw ThemeLoadError.InvalidStructure(themeId, "YAML root is not a mapping")
        return decodeToV2(mapping)
    }

    /** Decodes a raw (already expanded) mapping to [ThemeV2], honoring its format. */
    internal fun decodeToV2(mapping: YamlMap): ThemeV2 = when (ThemeFormatDetector.detectFormat(mapping)) {
        ThemeFormat.V2 -> ThemeYamlV2.parser.decodeFromYamlNode<ThemeV2>(mapping)
        ThemeFormat.LEGACY -> LegacyThemeAdapter.toV2(ThemeYaml.parser.decodeFromYamlNode<Theme>(mapping))
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
    ): YamlNode? {
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
        private val cache = HashMap<String, YamlNode?>()

        /** The source file of [resourceId], or null when it does not exist. */
        fun findFile(resourceId: String): File? = findSource(resourceId)

        /**
         * @param file source file of [resourceId] when it is already known.
         *   Included resources are always looked up by id. An explicit file
         *   wins over the id lookup cache.
         */
        fun load(resourceId: String, file: File?): YamlNode? {
            if (file != null) return readAndPatch(resourceId, file)
            if (cache.containsKey(resourceId)) return cache[resourceId]
            val result = findSource(resourceId)?.let { readAndPatch(resourceId, it) }
            cache[resourceId] = result
            return result
        }

        private fun readAndPatch(resourceId: String, file: File): YamlNode? {
            val node = runCatching { ThemeYaml.parser.parseToYamlNode(file.readText()) }.getOrNull()
            return node?.let { applyCustomPatch(resourceId, it) }
        }
    }

    /** Source file of [resourceId]: the user data dir first, then the shared one. */
    private fun findSourceFile(resourceId: String): File? = findSourceFile(
        resourceId,
        listOf(DataManager.userDataDir, DataManager.sharedDataDir),
    )

    /**
     * Source file of [resourceId] under [roots], in order. A resource id is free
     * text in an include directive, so a file that resolves outside the root it
     * was found in is refused.
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
     * that resource. An explicit root `__patch` wins; `.custom` files are never
     * patched.
     */
    internal fun applyCustomPatch(
        resourceId: String,
        node: YamlNode,
    ): YamlNode {
        if (resourceId.endsWith(".custom")) return node
        val root = node as? YamlMap ?: return node
        if (root.pairs[PATCH] != null) return node
        val patchId = resourceId.removeSuffix(".schema") + ".custom"
        return yamlMapOf(root.pairs + (PATCH to yamlScalarOf("$patchId:/patch?")))
    }
}
