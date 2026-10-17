/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

class RimeSchema private constructor(val schemaId: String, loadConfig: Boolean) {
    constructor(schemaId: String) : this(schemaId, true)

    companion object {
        fun empty() = RimeSchema(".default", false)
    }
    data class Switch(
        val name: String = "",
        val options: List<String> = listOf(),
        val reset: Int = -1,
        val states: List<String> = listOf(),
    )

    val switches: List<Switch>
    val alphabet: String
    val keyboard: String
    val isPinyinT9: Boolean

    init {
        if (!loadConfig) {
            switches = emptyList()
            alphabet = ""
            keyboard = ""
            isPinyinT9 = false
        } else {
            val schemaConfig = when {
                schemaId.isEmpty() -> RimeConfig.openConfig("default")

                schemaId.startsWith('.') ->
                    RimeConfig.openConfig(schemaId.substring(1))

                else -> RimeConfig.openSchema(schemaId)
            }
            schemaConfig.use {
                switches = it.getList("switches") { path ->
                    Switch(
                        name = getString("$path/name") ?: "",
                        options = getList("$path/options", RimeConfig::getString),
                        reset = getInt("$path/reset") ?: -1,
                        states = getList("$path/states", RimeConfig::getString),
                    )
                }
                alphabet = it.getString("speller/alphabet") ?: ""
                keyboard = it.getString("trime/keyboard") ?: ""
                isPinyinT9 = it.getString("trime/t9") == "true"
            }
        }
    }
}
