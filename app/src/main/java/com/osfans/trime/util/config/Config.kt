// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.config

import com.osfans.trime.data.base.DataManager
import timber.log.Timber

/**
 * New YAML config parser intended to replace the old one.
 */
class Config(
    private val data: ConfigData = ConfigData(),
) {
    companion object {
        fun openSchema(schemaId: String): Config =
            Config().apply {
                loadFromFile(DataManager.resolveDeployedResourcePath("$schemaId.schema"))
            }

        fun openConfig(configId: String): Config =
            Config().apply {
                loadFromFile(DataManager.resolveDeployedResourcePath(configId))
            }

        fun openUserConfig(configId: String): Config =
            Config().apply {
                loadFromFile(DataManager.userDataDir.resolve("$configId.yaml").absolutePath)
            }
    }

    fun loadFromFile(fileName: String) = data.loadFromFile(fileName)

    fun isNull(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p.type == ConfigItem.ValueType.Null
    }

    fun isValue(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p.type == ConfigItem.ValueType.Scalar
    }

    fun isList(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p.type == ConfigItem.ValueType.List
    }

    fun isMap(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p.type == ConfigItem.ValueType.Map
    }

    fun getBool(
        path: String,
        defValue: Boolean = false,
    ): Boolean {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return runCatching { p?.getBool() }.getOrNull() ?: defValue
    }

    fun getInt(
        path: String,
        defValue: Int = 0,
    ): Int {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return runCatching { p?.getInt() }.getOrNull() ?: defValue
    }

    fun getFloat(
        path: String,
        defValue: Float = 0f,
    ): Float {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return runCatching { p?.getFloat() }.getOrNull() ?: defValue
    }

    fun getString(
        path: String,
        defValue: String = "",
    ): String {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return runCatching { p?.getString() }.getOrNull() ?: defValue
    }

    fun getItem(path: String): Config {
        Timber.d("read: $path")
        return Config().also {
            it.data.root = data.traverse(path)
        }
    }

    fun getValue(path: String): Config {
        Timber.d("read: $path")
        return Config().also {
            it.data.root = data.traverse(path)?.configValue
        }
    }

    fun getList(path: String): List<Config> {
        Timber.d("read: $path")
        return data.traverse(path)?.configList?.map { v ->
            Config().also { it.data.root = v }
        } ?: emptyList()
    }

    fun getStringList(path: String): List<String> {
        Timber.d("read: $path")
        return data.traverse(path)?.configList?.map { v ->
            v.configValue.getString()
        } ?: emptyList()
    }

    fun getMap(path: String): Map<String, Config> {
        Timber.d("read: $path")
        return data.traverse(path)?.configMap?.mapValues { (_, v) ->
            Config().also { it.data.root = v }
        } ?: emptyMap()
    }

    fun getStringValueMap(path: String): Map<String, String> {
        Timber.d("read: $path")
        return data.traverse(path)?.configMap?.mapValues { (_, v) ->
            v.configValue.getString()
        } ?: emptyMap()
    }

    fun getItem() = data.root
}
