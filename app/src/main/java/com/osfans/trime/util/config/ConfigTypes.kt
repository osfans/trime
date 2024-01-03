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
abstract class ConfigItem(val node: YamlNode) {
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
    ): Nothing {
        throw IllegalArgumentException("Expected element to be $expectedType bus is ${item::class.simpleName}")
    }
}

/** The wrapper of [YamlScalar] */
class ConfigValue(private val scalar: YamlScalar) : ConfigItem(scalar) {
    constructor(item: ConfigItem) : this(item.node.yamlScalar)

    fun getString() = scalar.content

    fun getInt() = scalar.toInt()

    fun getFloat() = scalar.toFloat()

    fun getBool() = scalar.toBoolean()

    override fun isEmpty() = scalar.content.isEmpty()

    override fun contentToString(): String = scalar.contentToString()
}

/** The wrapper of [YamlList] */
class ConfigList(private val list: YamlList) : ConfigItem(list) {
    constructor(item: ConfigItem) : this(item.node.yamlList)

    val items get() = list.items.map { convertFromYaml(it) }

    operator fun iterator() = items.iterator()

    override fun isEmpty() = list.items.isEmpty()

    override fun contentToString(): String = list.contentToString()

    operator fun get(index: Int) = items[index]

    fun getValue(index: Int) = get(index)?.configValue
}

class ConfigMap(private val map: YamlMap) : ConfigItem(map) {
    constructor(item: ConfigItem) : this(item.node.yamlMap)

    override fun isEmpty() = map.entries.isEmpty()

    override fun contentToString(): String = map.contentToString()

    fun containsKey(key: String) = map.getKey(key) != null

    val entries get() =
        map.entries.entries.associate { (s, n) ->
            s.content to convertFromYaml(n)
        }

    operator fun iterator() = entries.iterator()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : ConfigItem> get(key: String): T? = entries.entries.firstOrNull { it.key == key }?.value as T?

    fun getValue(key: String): ConfigValue? = get<ConfigValue>(key)?.configValue
}
