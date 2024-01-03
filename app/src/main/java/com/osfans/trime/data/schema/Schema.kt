package com.osfans.trime.data.schema

import com.osfans.trime.util.config.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class Schema(schemaId: String = ".default") {
    private val config =
        if (schemaId == ".default") {
            Config.create("default")
        } else {
            Config.create("$schemaId.schema")
        }

    val switches get() =
        config?.getList("switches")
            ?.decode(ListSerializer(Switch.serializer()))

    val symbols get() =
        config?.getMap("punctuator/symbols")
            ?.decode(MapSerializer(String.serializer(), ListSerializer(String.serializer())))

    val alphabet get() = config?.getString("speller/alphabet")

    @Serializable
    data class Switch(
        val name: String? = null,
        val options: List<String>? = listOf(),
        val reset: Int? = null,
        val states: List<String>? = listOf(),
        @Transient var enabled: Int = 0,
    )
}
