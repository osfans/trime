package com.osfans.trime.util.config

import com.osfans.trime.data.DataManager
import timber.log.Timber

/**
 * New YAML config parser intended to replace the old one.
 */
class Config(private val data: ConfigData = ConfigData()) {

    companion object {
        fun create(fileName: String): Config? {
            val data = ConfigData()
            return if (data.loadFromFile(DataManager.resolveDeployedResourcePath(fileName))) {
                Config(data)
            } else {
                null
            }
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

    fun getBool(path: String): Boolean? {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getBool()
    }

    fun getInt(path: String): Int? {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getInt()
    }

    fun getFloat(path: String): Float? {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getFloat()
    }

    fun getString(path: String): String? {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getString()
    }

    fun getItem(path: String): ConfigItem? {
        Timber.d("read: $path")
        return data.traverse(path)
    }

    fun getValue(path: String): ConfigValue? {
        Timber.d("read: $path")
        return data.traverse(path)?.configValue
    }

    fun getList(path: String): ConfigList? {
        Timber.d("read: $path")
        return data.traverse(path)?.configList
    }

    fun getMap(path: String): ConfigMap? {
        Timber.d("read: $path")
        return data.traverse(path)?.configMap
    }

    fun getItem() = data.root
}
