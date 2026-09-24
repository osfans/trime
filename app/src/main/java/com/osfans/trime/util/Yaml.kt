/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import com.charleskorn.kaml.AmbiguousQuoteStyle
import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SequenceStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlPath
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import com.charleskorn.kaml.Yaml as KamlYaml

/**
 * The YAML parser every file the app reads goes through: theme sources and
 * deployed artifacts, sound effects, sync policies.
 *
 * Anchors and aliases are permitted because theme sources build their metrics
 * and colors with them (`tongwenfeng.trime.yaml`), while kaml forbids them by
 * default. kaml keeps the switch `internal`, so the whole configuration is
 * passed positionally; every value other than [AnchorsAndAliases.Permitted] is
 * kaml's own default.
 *
 * Documents stay bounded: the input limit is set to 10 MB, the value the theme
 * reader used to configure, and alias expansion has a finite budget, so a
 * hostile file cannot make parsing exhaust memory.
 */
object Yaml {
    fun parseToYamlNode(string: String): YamlNode = kaml.parseToYamlNode(string)
}

/**
 * Alias budget for theme sources: it used to be 200, which real themes exceeded,
 * and kaml's own default of 100 is below what `tongwenfeng.trime.yaml` resolves.
 * An alias is counted together with the accumulated weight of the nodes it
 * resolves, so a document whose aliases expand into more aliases still fails
 * early, while hand-written themes stay far below.
 */
private const val MAX_ALIAS_COUNT = 1000u

/** Input limit of the theme reader this replaced. */
private const val CODE_POINT_LIMIT = 10 * 1024 * 1024

private val kaml = KamlYaml(
    configuration = YamlConfiguration(
        /* encodeDefaults = */
        true,
        /* strictMode = */
        true,
        /* extensionDefinitionPrefix = */
        null,
        /* polymorphismStyle = */
        PolymorphismStyle.Tag,
        /* polymorphismPropertyName = */
        "type",
        /* encodingIndentationSize = */
        2,
        /* breakScalarsAt = */
        80,
        /* sequenceStyle = */
        SequenceStyle.Block,
        /* singleLineStringStyle = */
        SingleLineStringStyle.DoubleQuoted,
        /* multiLineStringStyle = */
        MultiLineStringStyle.DoubleQuoted,
        /* ambiguousQuoteStyle = */
        AmbiguousQuoteStyle.DoubleQuoted,
        /* sequenceBlockIndent = */
        0,
        /* anchorsAndAliases = */
        AnchorsAndAliases.Permitted(maxAliasCount = MAX_ALIAS_COUNT),
        /* yamlNamingStrategy = */
        null,
        /* codePointLimit = */
        CODE_POINT_LIMIT,
        /* decodeEnumCaseInsensitive = */
        false,
    ),
)

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

fun yamlScalarOf(content: String): YamlScalar = YamlScalar(content, YamlPath.root)

fun yamlMapOf(vararg pairs: Pair<String, YamlNode>): YamlMap = yamlMapOf(pairs.asList())

fun yamlMapOf(pairs: Map<String, YamlNode>): YamlMap = yamlMapOf(pairs.toList())

private fun yamlMapOf(pairs: Collection<Pair<String, YamlNode>>): YamlMap = YamlMap(
    pairs.associate { yamlScalarOf(it.first) to it.second },
    YamlPath.root,
)

fun yamlListOf(vararg items: YamlNode): YamlList = YamlList(items.asList(), YamlPath.root)
