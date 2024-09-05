// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar
import timber.log.Timber
import java.io.File

fun convertFromYaml(node: YamlNode): ConfigItem? =
    when (node) {
        is YamlNull -> null
        is YamlScalar -> ConfigValue(node.yamlScalar)
        is YamlList -> ConfigList(node.yamlList)
        is YamlMap -> ConfigMap(node.yamlMap)
        else -> null
    }

/**
 * The wrapper of parsed YAML node.
 */
class ConfigData {
    var root: ConfigItem? = null

    private val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                ),
        )

    fun loadFromFile(fileName: String): Boolean {
        val configFile = File(fileName)
        if (!configFile.exists()) {
            Timber.w("Nonexistent config file $fileName")
            return false
        }
        Timber.i("Loading config file $fileName")
        try {
            val doc =
                configFile
                    .inputStream()
                    .bufferedReader()
                    .use { it.readText() }
            val node = yaml.parseToYamlNode(doc)
            root = convertFromYaml(node)
        } catch (e: YamlException) {
            Timber.e(e, "Error parsing YAML")
            return false
        }
        return true
    }

    fun traverse(path: String): ConfigItem? {
        Timber.d("traverse: $path")
        if (path.isEmpty() || path == "/") return root
        val keys = path.trimEnd('/').split('/')
        var p = root
        for (key in keys) {
            if (p == null || p !is ConfigMap) {
                return null
            }
            p = p.configMap[key]
        }
        return p
    }
}
