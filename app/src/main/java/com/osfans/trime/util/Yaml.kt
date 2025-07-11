/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import timber.log.Timber

fun YamlMap.getString(
    key: String,
    defaultValue: String = "",
) = getScalar(key)?.content ?: defaultValue

fun YamlMap.getInt(
    key: String,
    defaultValue: Int = 0,
) = getScalar(key)?.toInt() ?: defaultValue

fun YamlMap.getFloat(
    key: String,
    defaultValue: Float = 0f,
) = getScalar(key)?.toFloat() ?: defaultValue

fun YamlMap.getBool(
    key: String,
    defaultValue: Boolean = false,
) = getScalar(key)?.toBoolean() ?: defaultValue

inline fun <reified T : Enum<T>> YamlMap.getEnum(key: String): T? {
    val string = getScalar(key)?.content ?: return null
    return runCatching { enumValueOf<T>(string.uppercase()) }.getOrNull()
}

inline fun <reified T : Enum<T>> YamlMap.getEnum(
    key: String,
    defaultValue: T,
): T = getEnum<T>(key) ?: defaultValue

fun YamlMap.getStringList(key: String): List<String>? = get<YamlList>(key)?.items?.map { it.yamlScalar.content }

@Suppress("UNCHECKED_CAST")
fun <T : YamlNode> YamlNode.traverse(path: String): T? {
    Timber.d("traverse: $path")
    if (path.isEmpty() || path == "/") return this as T?
    val keys = path.trimEnd('/').split('/')
    var p: YamlNode? = this
    for (key in keys) {
        if (p !is YamlMap) {
            return null
        }
        p = p.yamlMap.get<YamlNode>(key)
    }
    return p as? T
}
