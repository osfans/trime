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

    fun getBool(
        path: String,
        defValue: Boolean = false,
    ): Boolean {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getBool() ?: defValue
    }

    fun getInt(
        path: String,
        defValue: Int = 0,
    ): Int {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getInt() ?: defValue
    }

    fun getFloat(
        path: String,
        defValue: Float = 0f,
    ): Float {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getFloat() ?: defValue
    }

    fun getString(
        path: String,
        defValue: String = "",
    ): String {
        Timber.d("read: $path")
        val p = data.traverse(path)?.configValue
        return p?.getString() ?: defValue
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
