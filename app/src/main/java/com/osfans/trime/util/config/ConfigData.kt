package com.osfans.trime.util.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlMap
import timber.log.Timber
import java.io.File

/**
 * The wrapper of parsed YAML node.
 */
class ConfigData {
    var root: YamlNode? = null

    private val yaml = Yaml(
        configuration = YamlConfiguration(
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
            val doc = configFile
                .inputStream()
                .bufferedReader()
                .use { it.readText() }
            root = yaml.parseToYamlNode(doc)
        } catch (e: YamlException) {
            Timber.e("Error parsing YAML: ${e.message}")
            return false
        }
        return true
    }

    fun traverse(path: String): YamlNode? {
        Timber.d("traverse: $path")
        if (path.isEmpty() || path == "/") return root
        val keys = path.trimEnd('/').split('/')
        var p = root
        for (key in keys) {
            if (p == null || p !is YamlMap) {
                return null
            }
            p = p.yamlMap[key]
        }
        return p
    }
}
