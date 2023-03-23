package com.osfans.trime.data.schema

import com.osfans.trime.util.config.Config
import com.osfans.trime.util.config.ConfigList

class Schema(schemaId: String = ".default") {
    private val config = if (schemaId == ".default") {
        Config.create("default")
    } else {
        Config.create("$schemaId.schema")
    }

    val switches get() = config?.getList("switches")?.items
        ?.map { item ->
            val map = item?.configMap
            Switch(
                map?.getValue("name")?.getString(),
                map?.get<ConfigList>("options")?.items?.map { it!!.configValue.getString() },
                map?.getValue("reset")?.getInt(),
                map?.get<ConfigList>("states")?.items?.map { it!!.configValue.getString() },
            )
        }

    val symbols get() = config?.getMap("punctuator/symbols")?.entries?.entries
        ?.associate { (k, v) -> k to v!!.configList.items.map { it?.configValue?.getString() } }

    val alphabet get() = config?.getString("speller/alphabet")

    data class Switch(
        val name: String? = null,
        val options: List<String>? = listOf(),
        val reset: Int? = null,
        val states: List<String>? = listOf(),
        var enabled: Int = 0,
    )
}
