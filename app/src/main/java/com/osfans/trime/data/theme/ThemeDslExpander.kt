// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import com.osfans.trime.util.get
import com.osfans.trime.util.isNull
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
import com.osfans.trime.util.yamlListOf
import com.osfans.trime.util.yamlMapOf
import java.util.IdentityHashMap

/**
 * Expands the subset of the librime config DSL that theme files use, so a
 * theme can be read straight from its source YAML instead of a deployed
 * artifact. Semantics follow librime's config compiler: `__include` provides
 * the base node, sibling keys override it (mappings merge recursively), and
 * `__patch` overwrites keys afterwards.
 *
 * Supported directives:
 * - `__include: /local/path` and `__include: file[.yaml]:/path`, where a
 *   trailing `?` marks an optional reference;
 * - sibling keys overriding the included node;
 * - `__patch: /path` and `__patch: { key: value }`.
 *
 * Anything beyond this subset (include lists, `/+` and `/=` path operators,
 * nested patch paths, list references, references through an ancestor with
 * directives, `__merge`, ...) raises [UnsupportedDsl] so callers can
 * fall back to the librime deployment path instead of guessing semantics.
 *
 * Fidelity notes, all mirroring librime's config compiler:
 * - a sibling key without a value leaves the included node untouched, while an
 *   empty string replaces it (`MergeTree` / `EditNode`);
 * - merging a mapping into a sibling key whose value is not a mapping fails in
 *   librime, so it is refused here as well;
 * - a node is expanded once per run: the parser resolves anchors and aliases
 *   into equivalent copies, and the memo keeps a shared node from being
 *   expanded twice.
 */
object ThemeDslExpander {
    /** A construct outside the supported subset; callers should fall back. */
    class UnsupportedDsl(message: String) : Exception(message)

    /** A referenced node could not be resolved or is circular. */
    class UnresolvedReference(message: String) : Exception(message)

    private const val INCLUDE = "__include"
    private const val PATCH = "__patch"

    /**
     * Returns [node] with all supported directives expanded.
     *
     * @param resourceId id of the resource [node] was parsed from; references
     *   without a file part resolve against [node] itself.
     * @param loadResource parses another resource by id (without the `.yaml`
     *   suffix), returning null when it does not exist.
     */
    fun expand(
        resourceId: String,
        node: YamlNode,
        loadResource: (String) -> YamlNode?,
    ): YamlNode = Context(loadResource).expandNode(node, resourceId, node)

    private class Context(private val loadResource: (String) -> YamlNode?) {
        private val expanding = LinkedHashSet<String>()

        /** Results by node identity, so a node reached twice is expanded once. */
        private val expanded = IdentityHashMap<YamlNode, YamlNode>()

        fun expandNode(
            node: YamlNode,
            resourceId: String,
            root: YamlNode,
        ): YamlNode = when (node) {
            // Kaml may resolve the scalar inside a tag before exposing the
            // wrapper (for example, `!!str null`). Do not erase the tag and
            // guess at librime's type semantics.
            is YamlTaggedNode -> throw UnsupportedDsl("tagged YAML nodes require librime")

            is YamlScalar -> {
                // Kaml discards quoting information. A plain `Null`/`NULL` is
                // null to yaml-cpp, while a quoted one is a string, so only the
                // native parser can decide without changing user data.
                if (node.content == "Null" || node.content == "NULL") {
                    throw UnsupportedDsl("ambiguous capitalized null scalar requires librime")
                }
                node
            }

            is YamlList ->
                expanded.getOrPut(node) {
                    yamlListOf(*node.items.map { expandNode(it, resourceId, root) }.toTypedArray())
                }

            is YamlMap -> expanded.getOrPut(node) { expandMapping(node, resourceId, root) }

            else -> node
        }

        private fun expandMapping(
            map: YamlMap,
            resourceId: String,
            root: YamlNode,
        ): YamlNode {
            val entries = map.pairs
            val include = entries[INCLUDE]
            val patch = entries[PATCH]
            val overrides = entries.filterKeys { it != INCLUDE && it != PATCH }
            overrides.keys.forEach(::checkPlainKey)

            var result: YamlNode = yamlMapOf(overrides.mapValues { expandNode(it.value, resourceId, root) })
            if (include != null) {
                val included = reference(include, resourceId, root)
                if (included != null) {
                    result = when {
                        overrides.isEmpty() -> included
                        included is YamlMap -> mergeMaps(included, result as YamlMap)
                        else -> throw UnsupportedDsl("cannot merge sibling keys into a non-mapping '$INCLUDE' target")
                    }
                }
            }
            if (patch != null) {
                result = applyPatch(result, patch, resourceId, root)
            }
            return result
        }

        /** Overwrites [base] with the literal or referenced [patch]. */
        private fun applyPatch(
            base: YamlNode,
            patch: YamlNode,
            resourceId: String,
            root: YamlNode,
        ): YamlNode {
            val patchMap = when (patch) {
                is YamlTaggedNode -> throw UnsupportedDsl("tagged '$PATCH' values require librime")

                is YamlMap -> {
                    if (patch.pairs[INCLUDE] != null || patch.pairs[PATCH] != null) {
                        throw UnsupportedDsl("directives inside a literal '$PATCH' require librime")
                    }
                    patch
                }

                is YamlScalar -> {
                    // A reference, usually `__patch: <id>.custom:/patch?`; absent means
                    // "no such patch", which librime tolerates for optional references.
                    val target = reference(patch, resourceId, root) ?: return base
                    target as? YamlMap
                        ?: throw UnsupportedDsl("'$PATCH' target is not a mapping")
                }

                else -> throw UnsupportedDsl("unsupported '$PATCH' value")
            }
            val baseMap = base as? YamlMap
                ?: throw UnsupportedDsl("cannot '$PATCH' a non-mapping node")
            val patched = LinkedHashMap(baseMap.pairs)
            patchMap.pairs.forEach { (key, value) ->
                checkPlainKey(key)
                patched[key] = expandNode(value, resourceId, root)
            }
            return yamlMapOf(patched)
        }

        /**
         * Merges already-expanded [overrides] into [base] following librime's
         * rules: mappings merge recursively, everything else is replaced.
         */
        private fun mergeMaps(base: YamlMap, overrides: YamlMap): YamlMap {
            val merged = LinkedHashMap(base.pairs)
            overrides.pairs.forEach { (key, value) ->
                // librime leaves the included node untouched for a key without a value.
                if (value.isNull) return@forEach
                val existing = merged[key]
                merged[key] = when {
                    existing is YamlMap && value is YamlMap -> mergeMaps(existing, value)

                    // librime cannot merge a tree into a node of another type and fails
                    // the whole include; refuse it so the deployed path decides.
                    value is YamlMap && existing != null && existing !is YamlMap ->
                        throw UnsupportedDsl("cannot merge a mapping into the non-mapping sibling key '$key'")

                    else -> value
                }
            }
            return yamlMapOf(merged)
        }

        /**
         * Resolves an `__include` / `__patch` path and returns the target node
         * with its own directives expanded. Null means the optional reference
         * is absent.
         */
        private fun reference(
            value: YamlNode,
            resourceId: String,
            root: YamlNode,
        ): YamlNode? {
            if (value is YamlTaggedNode) {
                throw UnsupportedDsl("tagged '$INCLUDE'/'$PATCH' references require librime")
            }
            val raw = value.string
                ?: throw UnsupportedDsl("'$INCLUDE'/'$PATCH' expects a path")
            val optional = raw.endsWith("?")
            val path = if (optional) raw.dropLast(1) else raw
            val separator = path.indexOf(':')
            val (targetResource, localPath) = when {
                separator == -1 -> resourceId to path
                separator == 0 -> resourceId to path.substring(1)
                else -> path.substring(0, separator).removeSuffix(".yaml") to path.substring(separator + 1)
            }
            val normalized = localPath.removePrefix("/")
            val key = "$targetResource:$normalized"
            if (!expanding.add(key)) {
                throw UnresolvedReference("circular reference to '$key'")
            }
            try {
                val sameResource = targetResource == resourceId
                val fileRoot = if (sameResource) root else loadResource(targetResource)
                if (fileRoot == null) {
                    if (optional) return null
                    throw UnresolvedReference("resource '$targetResource' not found")
                }
                val target = findLocal(fileRoot, normalized)
                    ?: if (optional) {
                        return null
                    } else {
                        throw UnresolvedReference("'$key' not found")
                    }
                val fileId = if (sameResource) resourceId else targetResource
                return expandNode(target, fileId, fileRoot)
            } finally {
                expanding.remove(key)
            }
        }

        private fun findLocal(root: YamlNode, path: String): YamlNode? {
            var current: YamlNode = root
            for (segment in path.split('/')) {
                if (segment.isEmpty()) continue
                // librime indexes lists with @0 / @last, whereas the generic accessor
                // accepts plain integers. Neither spelling can use that accessor
                // faithfully here, even when the reference is optional.
                if (current is YamlTaggedNode) {
                    throw UnsupportedDsl("references through tagged YAML nodes require librime: '$path'")
                }
                val self = current
                if (self is YamlList) {
                    throw UnsupportedDsl("references through sequences require librime: '$path'")
                }
                // Native GetResolvedItem resolves blocking dependencies before
                // entering a child. Looking in this unexpanded map could return
                // an old value or mistake an inherited optional child for absent.
                // An auto-injected root custom patch also requires this fallback.
                if (self is YamlMap && (self.pairs[INCLUDE] != null || self.pairs[PATCH] != null)) {
                    throw UnsupportedDsl("references through an ancestor with directives require librime: '$path'")
                }
                current = self[segment] ?: return null
            }
            return current
        }
    }

    /**
     * Paths and operators such as `a/b`, `key/+` or `key/=` carry patch
     * semantics outside the supported subset.
     */
    private fun checkPlainKey(key: String) {
        if (key.isEmpty()) throw UnsupportedDsl("empty mapping key")
        if (key.startsWith("__") && key != INCLUDE && key != PATCH) {
            throw UnsupportedDsl("unsupported directive '$key'")
        }
        if (key.contains('/') || key.endsWith("+") || key.endsWith("=")) {
            throw UnsupportedDsl("unsupported patch path '$key'")
        }
    }
}
