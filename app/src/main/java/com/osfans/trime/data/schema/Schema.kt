// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize(using = Schema.Deserializer::class)
data class Schema(
    val switches: List<Switch> = emptyList(),
    val symbols: Map<String, List<String>> = emptyMap(),
    val alphabet: String = "",
) {
    data class Switch(
        val name: String = "",
        val options: List<String> = listOf(),
        val reset: Int = -1,
        val states: List<String> = listOf(),
        @field:JsonIgnore var enabled: Int = 0,
    )

    class Deserializer : JsonDeserializer<Schema>() {
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext,
        ): Schema {
            val node = p.codec.readTree<JsonNode>(p)
            val switches =
                node["switches"].map {
                    Switch(
                        name = it["name"]?.asText() ?: "",
                        options = it["options"]?.map { o -> o.asText() } ?: emptyList(),
                        reset = it["reset"]?.intValue() ?: -1,
                        states = it["states"]?.map { o -> o.asText() } ?: emptyList(),
                    )
                }
            val symbols =
                buildMap {
                    node.at("/punctuator/symbols").properties().forEach {
                        val value =
                            if (it.value.isArray) {
                                it.value?.map { v -> v.toString() } ?: emptyList()
                            } else {
                                listOf(it.value.toString())
                            }
                        put(it.key.toString(), value)
                    }
                }
            return Schema(
                switches = switches,
                symbols = symbols,
                alphabet = node.at("/speller/alphabet").toString(),
            )
        }
    }
}
