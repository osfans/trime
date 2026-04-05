/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.yaml

sealed class Node {
    abstract val anchor: String?

    class Scalar(
        val string: String,
        override val anchor: String? = null,
    ) : Node(),
        Comparable<Scalar> {
        override fun compareTo(other: Scalar): Int = this.string.compareTo(other.string)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Scalar
            return this.string == other.string
        }

        override fun hashCode(): Int = string.hashCode()
    }

    class Mapping(
        val pairs: Map<Node, Node>,
        override val anchor: String? = null,
    ) : Node(),
        Map<Node, Node> by pairs {
        constructor(vararg elements: Pair<Node, Node>) : this(elements.associate { it })

        operator fun get(key: String): Node? = this[Scalar(key)]

        override fun equals(other: Any?): Boolean = pairs == other
        override fun hashCode(): Int = pairs.hashCode()
    }

    class Sequence(
        val nodes: List<Node>,
        override val anchor: String? = null,
    ) : Node(),
        List<Node> by nodes {
        constructor(vararg elements: Node) : this(elements.toList())

        override fun equals(other: Any?): Boolean = nodes == other
        override fun hashCode(): Int = nodes.hashCode()
    }

    class Alias(
        override val anchor: String,
    ) : Node(),
        Comparable<Alias> {
        override fun compareTo(other: Alias): Int = this.anchor.compareTo(other.anchor)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Alias
            return this.anchor == other.anchor
        }

        override fun hashCode(): Int = anchor.hashCode()
    }
}

val Node.scalar: Node.Scalar?
    get() = this as? Node.Scalar

val Node.mapping: Node.Mapping?
    get() = this as? Node.Mapping

val Node.sequence: Node.Sequence?
    get() = this as? Node.Sequence

val Node.alias: Node.Alias?
    get() = this as? Node.Alias

val Node.string: String?
    get() = scalar?.string

val Node.int: Int?
    get() = scalar?.parseToIntLikeOrNull(String::toIntOrNull)

val Node.long: Long?
    get() = scalar?.parseToIntLikeOrNull(String::toLongOrNull)

val Node.double: Double?
    get() {
        return when (val string = scalar?.string) {
            ".inf", ".Inf", ".INF" -> Double.POSITIVE_INFINITY
            "-.inf", "-.Inf", "-.INF" -> Double.NEGATIVE_INFINITY
            ".nan", ".NaN", ".NAN" -> Double.NaN
            else -> string?.toDoubleOrNull()
        }
    }

val Node.float: Float?
    get() {
        return when (val string = scalar?.string) {
            ".inf", ".Inf", ".INF" -> Float.POSITIVE_INFINITY
            "-.inf", "-.Inf", "-.INF" -> Float.NEGATIVE_INFINITY
            ".nan", ".NaN", ".NAN" -> Float.NaN
            else -> string?.toFloatOrNull()
        }
    }

val Node.boolean: Boolean?
    get() = when (scalar?.string) {
        "true", "True", "TRUE" -> true
        "false", "False", "FALSE" -> false
        else -> null
    }

private fun <T : Any> Node.Scalar.parseToIntLikeOrNull(converter: (String, Int) -> T?): T? = try {
    when {
        string.startsWith("0x") -> converter(string.substring(2), 16)
        string.startsWith("-0x") -> converter("-" + string.substring(3), 16)
        string.startsWith("0o") -> converter(string.substring(2), 8)
        string.startsWith("-0o") -> converter("-" + string.substring(3), 8)
        else -> converter(string, 10)
    }
} catch (_: NumberFormatException) {
    null
}

inline fun <reified T : Enum<T>> Node.enum(): T? {
    val string = scalar?.string ?: return null
    return enumValues<T>().firstOrNull { it.name.equals(string, ignoreCase = true) }
}

operator fun Node.get(node: Node): Node? = when (this) {
    is Node.Scalar, is Node.Alias -> null
    is Node.Mapping -> this[node]
    is Node.Sequence -> {
        val index = node.int
        if (index != null && index in this.indices) {
            this[index]
        } else {
            null
        }
    }
}

operator fun Node.get(string: String): Node? = this[Node.Scalar(string)]

fun Node(value: Boolean): Node = Node.Scalar(value.toString())

fun Node(value: Number): Node = Node.Scalar(value.toString())
