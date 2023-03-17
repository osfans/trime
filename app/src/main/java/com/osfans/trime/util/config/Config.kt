package com.osfans.trime.util.config

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import timber.log.Timber

/**
 * New YAML config parser intended to replace the old one.
 */
class Config(private val data: ConfigData = ConfigData()) {

    fun loadFromFile(fileName: String) = data.loadFromFile(fileName)

    fun isNull(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p is YamlNull
    }

    fun isValue(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p is YamlScalar
    }

    fun isList(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p is YamlList
    }

    fun isMap(path: String): Boolean {
        val p = data.traverse(path)
        return p == null || p is YamlMap
    }

    fun getBool(path: String, defValue: Boolean = false): Boolean {
        Timber.d("read: $path")
        val p = data.traverse(path)?.yamlScalar
        return p?.toBoolean() ?: defValue
    }

    fun getInt(path: String, defValue: Int = 0): Int {
        Timber.d("read: $path")
        val p = data.traverse(path)?.yamlScalar
        return p?.toInt() ?: defValue
    }

    fun getFloat(path: String, defValue: Float = 0f): Float {
        Timber.d("read: $path")
        val p = data.traverse(path)?.yamlScalar
        return p?.toFloat() ?: defValue
    }

    fun getString(path: String, defValue: String = ""): String {
        Timber.d("read: $path")
        val p = data.traverse(path)?.yamlScalar
        return p?.content ?: defValue
    }

    fun getNode(path: String): YamlNode? {
        Timber.d("read: $path")
        return data.traverse(path)
    }

    fun getValue(path: String): YamlScalar? {
        Timber.d("read: $path")
        return data.traverse(path)?.yamlScalar
    }

    fun getList(path: String): YamlList? {
        Timber.d("read: $path")
        return data.traverse(path)?.yamlList
    }

    fun getMap(path: String): YamlMap? {
        Timber.d("read: $path")
        return data.traverse(path)?.yamlMap
    }
}
