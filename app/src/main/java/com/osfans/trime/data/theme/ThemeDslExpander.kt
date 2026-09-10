// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.string

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
 * nested patch paths, `__merge`, ...) raises [UnsupportedDsl] so callers can
 * fall back to the librime deployment path instead of guessing semantics.
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
        node: Node,
        loadResource: (String) -> Node?,
    ): Node = Context(loadResource).expandNode(node, resourceId, node)

    private class Context(private val loadResource: (String) -> Node?) {
        private val expanding = LinkedHashSet<String>()

        fun expandNode(
            node: Node,
            resourceId: String,
            root: Node,
        ): Node = when (node) {
            is Node.Scalar, is Node.Alias -> node
            is Node.Sequence -> Node.Sequence(node.nodes.map { expandNode(it, resourceId, root) }, node.anchor)
            is Node.Mapping -> expandMapping(node, resourceId, root)
        }

        private fun expandMapping(
            map: Node.Mapping,
            resourceId: String,
            root: Node,
        ): Node {
            val include = map[INCLUDE]
            val patch = map[PATCH]
            val overrides = map.pairs.filterKeys { it.string != INCLUDE && it.string != PATCH }
            overrides.keys.forEach { checkPlainKey(it.string) }

            var result: Node = Node.Mapping(overrides.mapValues { expandNode(it.value, resourceId, root) })
            if (include != null) {
                val included = reference(include, resourceId, root)
                if (included != null) {
                    result = when {
                        overrides.isEmpty() -> included
                        included is Node.Mapping -> mergeMaps(included, result as Node.Mapping)
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
            base: Node,
            patch: Node,
            resourceId: String,
            root: Node,
        ): Node {
            val patchMap = when (patch) {
                is Node.Mapping -> patch
                is Node.Scalar ->
                    reference(patch, resourceId, root) as? Node.Mapping
                        ?: throw UnsupportedDsl("'$PATCH' target is not a mapping")
                else -> throw UnsupportedDsl("unsupported '$PATCH' value")
            }
            val baseMap = base as? Node.Mapping
                ?: throw UnsupportedDsl("cannot '$PATCH' a non-mapping node")
            val patched = LinkedHashMap(baseMap.pairs)
            patchMap.pairs.forEach { (key, value) ->
                checkPlainKey(key.string)
                patched[key] = expandNode(value, resourceId, root)
            }
            return Node.Mapping(patched, baseMap.anchor)
        }

        /**
         * Merges already-expanded [overrides] into [base] following librime's
         * rules: mappings merge recursively, everything else is replaced.
         */
        private fun mergeMaps(base: Node.Mapping, overrides: Node.Mapping): Node.Mapping {
            val merged = LinkedHashMap(base.pairs)
            overrides.pairs.forEach { (key, value) ->
                val existing = merged[key]
                merged[key] = if (existing is Node.Mapping && value is Node.Mapping) {
                    mergeMaps(existing, value)
                } else {
                    value
                }
            }
            return Node.Mapping(merged)
        }

        /**
         * Resolves an `__include` / `__patch` path and returns the target node
         * with its own directives expanded. Null means the optional reference
         * is absent.
         */
        private fun reference(
            value: Node,
            resourceId: String,
            root: Node,
        ): Node? {
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

        private fun findLocal(root: Node, path: String): Node? {
            var current: Node = root
            for (segment in path.split('/')) {
                if (segment.isEmpty()) continue
                current = current[segment] ?: return null
            }
            return current
        }
    }

    /**
     * Paths and operators such as `a/b`, `key/+` or `key/=` carry patch
     * semantics outside the supported subset.
     */
    private fun checkPlainKey(key: String?) {
        if (key == null) throw UnsupportedDsl("non-scalar mapping key")
        if (key.startsWith("__") && key != INCLUDE && key != PATCH) {
            throw UnsupportedDsl("unsupported directive '$key'")
        }
        if (key.contains('/') || key.endsWith("+") || key.endsWith("=")) {
            throw UnsupportedDsl("unsupported patch path '$key'")
        }
    }
}
