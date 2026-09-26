/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlPath
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder

/**
 * Null spellings yaml-cpp, and therefore librime, recognises but kaml does not:
 * `~`, `null` and a missing value already arrive as [YamlNull], while a plain
 * `Null` or `NULL` stays a scalar. A quoted spelling is indistinguishable here
 * and is treated as null too; theme files do not rely on that distinction.
 */
private val extraNullSpellings = setOf("Null", "NULL")

/** A node with any `!!tag` wrapper stripped; theme files never rely on tags. */
val YamlNode.untagged: YamlNode
    get() = if (this is YamlTaggedNode) innerNode.untagged else this

/** This node as a scalar, or `null` when it is not one. */
val YamlNode.scalar: YamlScalar?
    get() = untagged as? YamlScalar

/** This node as a mapping, or `null` when it is not one. */
val YamlNode.mapping: YamlMap?
    get() = untagged as? YamlMap

/** This node as a list, or `null` when it is not one. */
val YamlNode.sequence: YamlList?
    get() = untagged as? YamlList

/** The text of this node when it is a scalar, `null` otherwise. */
val YamlNode.string: String?
    get() = scalar?.content

/** True when this node is a scalar without a value (`key:`, `~`, `null`, `Null`). */
val YamlNode.isNull: Boolean
    get() = when (val node = untagged) {
        is YamlNull -> true
        is YamlScalar -> node.content in extraNullSpellings
        else -> false
    }

/** The scalar conversion kaml already implements, degraded to `null` on a non-scalar or a bad value. */
private inline fun <T : Any> YamlNode.converted(convert: YamlScalar.() -> T): T? = scalar?.let { runCatching { it.convert() }.getOrNull() }

val YamlNode.int: Int?
    get() = converted { toInt() }

val YamlNode.float: Float?
    get() = converted { toFloat() }

val YamlNode.boolean: Boolean?
    get() = converted { toBoolean() }

inline fun <reified T : Enum<T>> YamlNode.enum(): T? {
    val string = scalar?.content ?: return null
    return enumValues<T>().firstOrNull { it.name.equals(string, ignoreCase = true) }
}

/** The child of this node under [key]: a mapping entry, or a list item when [key] is an index. */
operator fun YamlNode.get(key: String): YamlNode? = when (val self = untagged) {
    is YamlMap -> self.get<YamlNode>(key)
    is YamlList -> key.toIntOrNull()?.let { self.items.getOrNull(it) }
    else -> null
}

/** Entries of a mapping keyed by their text, in document order. */
val YamlMap.pairs: Map<String, YamlNode>
    get() = entries.mapKeys { it.key.content }

/** Entries of this node when it is a mapping, `null` otherwise. */
val YamlNode.pairs: Map<String, YamlNode>?
    get() = (this as? YamlMap)?.pairs

/**
 * The YAML node a kaml decoder carries. Serializers that decide themselves what
 * an empty value means need it, because kaml hands over the node only here.
 */
internal fun Decoder.yamlNode(): YamlNode = (this as? YamlInput)?.node
    ?: throw SerializationException("Expect a YAML decoder")

fun yamlScalarOf(content: String): YamlScalar = YamlScalar(content, YamlPath.root)

fun yamlMapOf(vararg pairs: Pair<String, YamlNode>): YamlMap = yamlMapOf(pairs.asList())

fun yamlMapOf(pairs: Map<String, YamlNode>): YamlMap = yamlMapOf(pairs.toList())

private fun yamlMapOf(pairs: Collection<Pair<String, YamlNode>>): YamlMap = YamlMap(
    pairs.associate { yamlScalarOf(it.first) to it.second },
    YamlPath.root,
)

fun yamlListOf(vararg items: YamlNode): YamlList = YamlList(items.asList(), YamlPath.root)
