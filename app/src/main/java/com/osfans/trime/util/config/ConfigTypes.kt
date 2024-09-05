// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import kotlinx.serialization.DeserializationStrategy

/** Config item base class */
abstract class ConfigItem(
    val node: YamlNode,
) {
    enum class ValueType {
        Null,
        Scalar,
        List,
        Map,
        Tagged,
    }

    open fun isEmpty() = node is YamlNull

    val type get() =
        when (node) {
            is YamlNull -> ValueType.Null
            is YamlScalar -> ValueType.Scalar
            is YamlList -> ValueType.List
            is YamlMap -> ValueType.Map
            else -> ValueType.Null
        }

    abstract fun contentToString(): String

    fun <T> decode(deserializer: DeserializationStrategy<T>) =
        Yaml(configuration = YamlConfiguration(strictMode = false))
            .decodeFromYamlNode(deserializer, node)

    val configValue: ConfigValue
        get() = this as? ConfigValue ?: error(this, "ConfigValue")

    val configList: ConfigList
        get() = this as? ConfigList ?: error(this, "ConfigList")

    val configMap: ConfigMap
        get() = this as? ConfigMap ?: error(this, "ConfigMap")

    private fun error(
        item: ConfigItem,
        expectedType: String,
    ): Nothing = throw IllegalArgumentException("Expected element to be $expectedType but is ${item::class.simpleName}")
}

/** The wrapper of [YamlScalar] */
class ConfigValue(
    private val scalar: YamlScalar,
) : ConfigItem(scalar) {
    constructor(item: ConfigItem) : this(item.node.yamlScalar)

    fun getString() = scalar.content

    fun getInt() = scalar.toInt()

    fun getFloat() = scalar.toFloat()

    fun getBool() = scalar.toBoolean()

    override fun isEmpty() = scalar.content.isEmpty()

    override fun contentToString(): String = scalar.contentToString()

    override fun toString(): String = scalar.content
}

/** The wrapper of [YamlList] */
class ConfigList(
    private val list: YamlList,
) : ConfigItem(list),
    List<ConfigItem?> {
    constructor(item: ConfigItem) : this(item.node.yamlList)

    private val items = list.items.map { convertFromYaml(it) }

    override val size = list.items.size

    override fun containsAll(elements: Collection<ConfigItem?>): Boolean = items.containsAll(elements)

    override fun contains(element: ConfigItem?): Boolean = items.contains(element)

    override operator fun iterator() = items.iterator()

    override fun listIterator(): ListIterator<ConfigItem?> = items.listIterator()

    override fun listIterator(index: Int): ListIterator<ConfigItem?> = items.listIterator(index)

    override fun subList(
        fromIndex: Int,
        toIndex: Int,
    ): List<ConfigItem?> = items.subList(fromIndex, toIndex)

    override fun lastIndexOf(element: ConfigItem?): Int = items.lastIndexOf(element)

    override fun isEmpty() = list.items.isEmpty()

    override fun contentToString(): String = list.contentToString()

    override operator fun get(index: Int): ConfigItem? = items[index]

    override fun indexOf(element: ConfigItem?): Int = items.indexOf(element)

    fun getValue(index: Int) = get(index)?.configValue

    // just like other Java / Kotlin collection type
    override fun toString(): String = items.joinToString(prefix = "[", postfix = "]")
}

class ConfigMap(
    private val map: YamlMap,
) : ConfigItem(map),
    Map<String, ConfigItem?> {
    constructor(item: ConfigItem) : this(item.node.yamlMap)

    override fun isEmpty() = map.entries.isEmpty()

    override val size: Int = map.entries.size

    override fun contentToString(): String = map.contentToString()

    override fun containsKey(key: String) = map.getKey(key) != null

    override fun containsValue(value: ConfigItem?): Boolean = entries.any { it.value == value }

    override val entries: Set<Map.Entry<String, ConfigItem?>> =
        map.entries.entries
            .associate { (s, n) ->
                s.content to convertFromYaml(n)
            }.entries

    override val keys: Set<String> = entries.map { it.key }.toSet()

    override val values: Collection<ConfigItem?> = entries.map { it.value }

    operator fun iterator() = entries.iterator()

    override operator fun get(key: String): ConfigItem? = entries.firstOrNull { it.key == key }?.value

    fun getValue(key: String): ConfigValue? = get(key)?.configValue

    // just like other Java / Kotlin map type
    override fun toString(): String = entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key=$value" }
}
