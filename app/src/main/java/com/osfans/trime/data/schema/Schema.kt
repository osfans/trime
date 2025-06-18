// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import com.osfans.trime.core.RimeConfig
import timber.log.Timber

class Schema(
    schemaId: String,
) {
    val switches: List<Switch>

    val symbols: Map<String, RimeConfig>

    val alphabet: String

    init {
        RimeConfig.openSchema(schemaId).use { c ->
            switches =
                c.getList("switches").mapNotNull decode@{ e ->
                    try {
                        Switch(
                            name = e.getString("name"),
                            options = e.getStringList("options"),
                            reset = e.getInt("reset", -1),
                            states = e.getStringList("states"),
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to decode switches of schema '$schemaId'")
                        null
                    }
                }
            symbols = c.getMap("punctuator/symbols")
            alphabet = c.getString("speller/alphabet")
        }
    }

    data class Switch(
        val name: String = "",
        val options: List<String> = listOf(),
        val reset: Int = -1,
        val states: List<String> = listOf(),
        var enabled: Int = 0,
    )
}
