// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlScalar
import com.osfans.trime.util.getBool
import com.osfans.trime.util.getEnum
import com.osfans.trime.util.getFloat
import com.osfans.trime.util.getInt
import com.osfans.trime.util.getString

abstract class Mapper<T>(
    val node: YamlMap,
) {
    abstract fun map(): T

    protected fun getString(
        key: String,
        defValue: String = "",
    ): String = node.getString(key, defValue)

    protected fun getInt(
        key: String,
        defValue: Int = 0,
    ): Int = node.getInt(key, defValue)

    protected fun getFloat(
        key: String,
        defValue: Float = 0f,
    ): Float = node.getFloat(key, defValue)

    protected fun getBoolean(
        key: String,
        defValue: Boolean = false,
    ): Boolean = node.getBool(key, defValue)

    protected inline fun <reified T : Enum<T>> getEnum(
        key: String,
        defaultValue: T,
    ): T = node.getEnum<T>(key, defaultValue)

    protected fun getList(key: String): List<YamlNode>? = node.get<YamlList>(key)?.items

    protected fun getStringList(
        key: String,
        defValue: List<String> = emptyList(),
    ): List<String> {
        val node = node.get<YamlNode>(key)
        return when (node) {
            is YamlList -> node.items.map { it.yamlScalar.content }

            is YamlScalar -> listOf(node.content)

            else -> defValue
        }
    }
}
