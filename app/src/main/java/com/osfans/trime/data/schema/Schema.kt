// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import com.osfans.trime.util.config.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

class Schema(
    schemaId: String = ".default",
) {
    private val config =
        if (schemaId == ".default") {
            Config.create("default")
        } else {
            Config.create("$schemaId.schema")
        }

    val switches get() =
        config?.getList("switches")

    val symbols get() =
        config?.getMap("punctuator/symbols")

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
