package com.osfans.trime.data.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RimeSchema(
    @SerialName("__build_info")
    val buildInfo: BuildInfo? = null, // 构建信息
    val schema: Schema, // 方案信息
    val switches: List<Switch> = listOf(), // 选项开关
    val punctuator: Punctuator? = null, // 标点
    val speller: Speller? = null, // 拼写器
) {
    @Serializable
    data class BuildInfo(
        @SerialName("rime_version")
        val rimeVersion: String,
        val timestamps: Map<String, Long> = mapOf(),
    )

    @Serializable
    data class Schema(
        val author: List<String> = listOf(),
        @SerialName("schema_id")
        val schemaId: String,
        val version: String? = null,
        val dependencies: List<String> = listOf(),
        val description: String? = null,
    )

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
