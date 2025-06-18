/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

class RimeConfig private constructor(
    private val ptr: Long,
) : AutoCloseable {
    fun getString(
        key: String,
        defaultValue: String = "",
    ): String {
        check(ptr != 0L)
        return getRimeConfigString(ptr, key) ?: defaultValue
    }

    fun getBool(
        key: String,
        defaultValue: Boolean = false,
    ): Boolean {
        check(ptr != 0L)
        return getRimeConfigBool(ptr, key) ?: defaultValue
    }

    fun getDouble(
        key: String,
        defaultValue: Double = 0.0,
    ): Double {
        check(ptr != 0L)
        return getRimeConfigDouble(ptr, key) ?: defaultValue
    }

    fun getInt(
        key: String,
        defaultValue: Int = 0,
    ): Int {
        check(ptr != 0L)
        return getRimeConfigInt(ptr, key) ?: defaultValue
    }

    fun getList(key: String): List<RimeConfig> {
        check(ptr != 0L)
        val array = getRimeConfigList(ptr, key) ?: return emptyList()
        return array.map { RimeConfig(it) }
    }

    fun getStringList(key: String): List<String> {
        check(ptr != 0L)
        return getRimeConfigList(ptr, key)?.map {
            getRimeConfigString(it, "") ?: ""
        } ?: emptyList()
    }

    fun getMap(key: String): Map<String, RimeConfig> {
        check(ptr != 0L)
        val map = getRimeConfigMap(ptr, key) ?: return emptyMap()
        return map.mapValues { RimeConfig(it.value) }
    }

    fun getStringValueMap(key: String): Map<String, String> {
        check(ptr != 0L)
        val map = getRimeConfigMap(ptr, key) ?: return emptyMap()
        return map.mapValues { getRimeConfigString(it.value, "") ?: "" }
    }

    fun getItem(key: String): RimeConfig {
        check(ptr != 0L)
        return RimeConfig(getRimeConfigItem(ptr, key))
    }

    override fun close() {
        if (ptr != 0L) {
            closeRimeConfig(ptr)
        }
    }

    companion object {
        @JvmStatic
        private external fun openRimeSchema(schemaId: String): Long

        @JvmStatic
        private external fun openRimeConfig(configId: String): Long

        @JvmStatic
        private external fun openRimeUserConfig(configId: String): Long

        @JvmStatic
        private external fun getRimeConfigString(
            ptr: Long,
            key: String,
        ): String?

        @JvmStatic
        private external fun getRimeConfigBool(
            ptr: Long,
            key: String,
        ): Boolean?

        @JvmStatic
        private external fun getRimeConfigDouble(
            ptr: Long,
            key: String,
        ): Double?

        @JvmStatic
        private external fun getRimeConfigInt(
            ptr: Long,
            key: String,
        ): Int?

        @JvmStatic
        private external fun getRimeConfigList(
            ptr: Long,
            key: String,
        ): LongArray?

        @JvmStatic
        private external fun getRimeConfigMap(
            ptr: Long,
            key: String,
        ): Map<String, Long>?

        @JvmStatic
        private external fun getRimeConfigItem(
            ptr: Long,
            key: String,
        ): Long

        @JvmStatic
        private external fun closeRimeConfig(ptr: Long)

        fun openSchema(schemaId: String) = RimeConfig(openRimeSchema(schemaId))

        fun openConfig(configId: String) = RimeConfig(openRimeConfig(configId))

        fun openUserConfig(configId: String) = RimeConfig(openRimeUserConfig(configId))
    }
}
