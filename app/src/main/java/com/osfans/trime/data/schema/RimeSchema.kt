package com.osfans.trime.data.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RimeSchema(
    @SerialName("__build_info")
    val buildInfo: BuildInfo, // 构建信息
    val schema: Schema, // 方案信息
    val switches: List<Switch>?, // 选项开关
    val punctuator: Punctuator, // 标点
    val speller: Speller // 拼写器
) {
    @Serializable
    data class BuildInfo(
        @SerialName("rime_version")
        val rimeVersion: String,
        val timestamps: Map<String, Long>
    )

    @Serializable
    data class Schema(
        val author: List<String>,
        @SerialName("schema_id")
        val schemaId: String,
        val version: String,
        val dependencies: List<String>,
        val description: String
    )

    @Serializable
    data class Switch(
        val name: String? = null,
        val options: List<String>? = null,
        val reset: Int? = null,
        val states: List<String>? = null,
        @Transient var enabled: Int = 0,
    )

    @Serializable
    data class Punctuator(
        val symbols: Map<String, List<String>>?
    )

    @Serializable
    data class Speller(
        val alphabet: String?
    )
}
