// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.util.traverse
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import timber.log.Timber
import java.io.File

data class Schema(
    val switches: List<Switch> = emptyList(),
    val symbolKeys: List<String> = emptyList(),
    val alphabet: String = "",
) {
    @Serializable
    data class Switch(
        val name: String = "",
        val options: List<String> = listOf(),
        val reset: Int = -1,
        val states: List<String> = listOf(),
    ) {
        @Transient
        var enabledIndex: Int = 0
    }

    companion object {
        fun decodeBySchemaId(schemaId: String): Schema {
            if (schemaId == ".default") return Schema()
            val file = File(DataManager.resolveDeployedResourcePath("$schemaId.schema"))
            val root = SchemaManager.yaml.parseToYamlNode(file.readText()).yamlMap
            return Schema(
                switches =
                    root.get<YamlList>("switches")?.items?.mapNotNull decode@{
                        try {
                            SchemaManager.yaml.decodeFromYamlNode<Switch>(it.yamlMap)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to decode switches of schema '$schemaId'")
                            null
                        }
                    } ?: emptyList(),
                symbolKeys =
                    root
                        .traverse<YamlMap>("punctuator/symbols")
                        ?.entries
                        ?.keys
                        ?.map { it.content } ?: emptyList(),
                alphabet = root.traverse<YamlScalar>("speller/alphabet")?.content ?: "",
            )
        }
    }
}
