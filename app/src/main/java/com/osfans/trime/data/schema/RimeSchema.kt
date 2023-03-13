package com.osfans.trime.data.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RimeSchema(
    val switches: List<Switch> = listOf(), // 选项开关
    val punctuator: Punctuator? = null, // 标点
    val speller: Speller? = null, // 拼写器
) {
    @Serializable
    data class Switch(
        val name: String? = null,
        val options: List<String> = listOf(),
        val reset: Int? = null,
        val states: List<String> = listOf(),
        @Transient var enabled: Int = 0,
    )

    @Serializable
    data class Punctuator(
        val symbols: Map<String, List<String>> = mapOf(),
    )

    @Serializable
    data class Speller(
        val alphabet: String? = null,
    )
}
