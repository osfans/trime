// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.data.theme.ThemeYaml
import com.osfans.trime.util.mapping
import com.osfans.trime.util.sequence
import com.osfans.trime.util.string
import com.osfans.trime.util.untagged
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Parse
import it.krzeminski.snakeyaml.engine.kmp.events.CollectionEndEvent
import it.krzeminski.snakeyaml.engine.kmp.events.CollectionStartEvent
import it.krzeminski.snakeyaml.engine.kmp.events.DocumentStartEvent
import it.krzeminski.snakeyaml.engine.kmp.events.NodeEvent
import java.io.File
import java.nio.charset.CodingErrorAction

/** Structural checks precede publication; native deployment still checks Rime semantics. */
internal object ConfigImportValidator {
    const val MAX_YAML_BYTES = 2 * 1024 * 1024
    private const val MAX_DICTIONARY_HEADER_BYTES = 2 * 1024 * 1024
    private const val MAX_NODES = 25_000
    private const val MAX_DEPTH = 64
    private val resourceId = Regex("[A-Za-z0-9_.-]+")
    private val parser = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            anchorsAndAliases = AnchorsAndAliases.Permitted(50u),
            codePointLimit = MAX_YAML_BYTES,
        ),
    )

    fun isConfiguration(path: String): Boolean = path.endsWith(".yaml", ignoreCase = true) || path.endsWith(".yml", ignoreCase = true)

    fun requiresDeployment(path: String): Boolean = isConfiguration(path) ||
        path.endsWith(".db", ignoreCase = true) || path.endsWith(".bin", ignoreCase = true) ||
        path.endsWith(".lua", ignoreCase = true) || path == "custom_phrase.txt" ||
        (path.startsWith("opencc/") && listOf(".json", ".txt", ".ocd", ".ocd2").any { path.endsWith(it, ignoreCase = true) })

    /** Returns false for non-YAML resources, which are still copied transactionally. */
    fun validate(file: File, relativePath: String): Boolean {
        require(SyncRelativePath.normalize(relativePath) == relativePath) { "Non-canonical import path" }
        if (!isConfiguration(relativePath)) return false
        val dictionary = relativePath.endsWith(".dict.yaml", ignoreCase = true)
        val text = if (dictionary) {
            readDictionaryHeader(file)
        } else {
            require(file.length() in 1..MAX_YAML_BYTES.toLong()) { "Configuration is empty or exceeds 2 MiB: $relativePath" }
            decodeUtf8(file.readBytes())
        }
        // The bundled tongwenfeng theme contains more than 100 aliases. Preserve the
        // existing theme parser's finite budget, while ordinary configs use 50 aliases.
        val theme = relativePath.endsWith(".trime.yaml") || File(relativePath).name == "trime.yaml"
        checkStreamBudget(text)
        val root = (if (theme) ThemeYaml.parser else parser).parseToYamlNode(text).mapping
            ?: error("Configuration root must be a mapping: $relativePath")
        require(root.entries.isNotEmpty()) { "Configuration is empty: $relativePath" }
        var nodes = 0
        fun inspect(node: YamlNode, depth: Int) {
            require(depth <= MAX_DEPTH && ++nodes <= MAX_NODES) { "Configuration structure exceeds the validation budget: $relativePath" }
            when (val value = node.untagged) {
                is YamlMap -> value.entries.forEach { (key, child) ->
                    if (key.content == "__include") child.string?.let(::requireSafeReference)
                    if (key.content == "__patch") {
                        child.string?.let(::requireSafeReference)
                        child.sequence?.items?.forEach { it.string?.let(::requireSafeReference) }
                    }
                    inspect(child, depth + 1)
                }

                is YamlList -> value.items.forEach { inspect(it, depth + 1) }

                else -> Unit
            }
        }
        inspect(root, 0)
        if (relativePath.endsWith(".schema.yaml")) {
            val schema = root.get<YamlNode>("schema")?.mapping
            require(schema != null || root.get<YamlNode>("__include") != null) { "Schema metadata is missing: $relativePath" }
            schema?.get<YamlNode>("schema_id")?.let { id ->
                val expected = File(relativePath).name.removeSuffix(".schema.yaml")
                require(id.string == expected && resourceId.matches(expected)) { "Schema ID does not match the filename: $relativePath" }
            }
            root.get<YamlNode>("engine")?.let { require(it.mapping != null) { "Schema engine must be a mapping: $relativePath" } }
        }
        if (relativePath.endsWith(".custom.yaml")) {
            require(root.get<YamlNode>("patch") != null || root.get<YamlNode>("__patch") != null || root.get<YamlNode>("__include") != null) {
                "Custom configuration has no patch: $relativePath"
            }
            root.get<YamlNode>("patch")?.let {
                require(it.mapping != null || it.sequence != null || it.string != null) { "Invalid custom patch: $relativePath" }
            }
        }
        if (dictionary) require(!root.get<YamlNode>("name")?.string.isNullOrBlank()) { "Dictionary name is missing: $relativePath" }
        if (theme) {
            require(root.get<YamlNode>("__include") != null || !root.get<YamlNode>("name")?.string.isNullOrBlank()) { "Theme name is missing: $relativePath" }
            root.get<YamlNode>("style")?.let { require(it.mapping != null) { "Theme style must be a mapping: $relativePath" } }
            root.get<YamlNode>("preset_color_schemes")?.let {
                require(it.mapping != null && it.mapping!!.entries.isNotEmpty()) { "Theme color schemes are invalid: $relativePath" }
            }
        }
        return true
    }

    /** Event parsing does not expand aliases or materialize the document's mapping/list nodes. */
    private fun checkStreamBudget(text: String) {
        var depth = 0
        var nodes = 0
        var documents = 0
        val events = Parse(LoadSettings(codePointLimit = MAX_YAML_BYTES)).parse(text)
        for (event in events) {
            if (event is DocumentStartEvent) require(++documents <= 1) { "Only one YAML document may be imported" }
            if (event is NodeEvent) require(++nodes <= MAX_NODES) { "Configuration exceeds the node budget" }
            when (event) {
                is CollectionStartEvent -> require(++depth <= MAX_DEPTH) { "Configuration exceeds the nesting budget" }
                is CollectionEndEvent -> depth--
                else -> Unit
            }
        }
    }

    private fun requireSafeReference(reference: String) {
        val resource = reference.substringBefore(':')
        require(resource.isNotBlank() && resourceId.matches(resource) && resource != "." && resource != "..") {
            "Configuration reference contains an unsafe resource path"
        }
        val path = reference.substringAfter(':', "").removePrefix("/")
        require(path.split('/').none { it == ".." || '\\' in it || '\u0000' in it }) { "Configuration reference escapes its resource" }
    }

    private fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")

    private fun readDictionaryHeader(file: File): String {
        val header = java.io.ByteArrayOutputStream()
        file.inputStream().buffered().use { input ->
            val line = java.io.ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                if (byte == -1) {
                    require(decodeUtf8(line.toByteArray()).trim() == "...") { "Dictionary YAML header has no terminator" }
                    return decodeUtf8(header.toByteArray())
                }
                require(header.size() + line.size() < MAX_DICTIONARY_HEADER_BYTES) { "Dictionary header exceeds 2 MiB" }
                line.write(byte)
                if (byte == '\n'.code) {
                    val bytes = line.toByteArray()
                    if (decodeUtf8(bytes).trim() == "...") return decodeUtf8(header.toByteArray())
                    header.write(bytes)
                    line.reset()
                }
            }
        }
    }
}
