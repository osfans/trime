// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.util.config.Config

abstract class Mapper<T>(
    val prefix: String,
    val config: Config,
) {
    abstract fun map(): T

    protected fun getString(
        key: String,
        defValue: String = "",
    ): String = config.getString("$prefix/$key", defValue)

    protected fun getInt(
        key: String,
        defValue: Int = 0,
    ): Int = config.getInt("$prefix/$key", defValue)

    protected fun getFloat(
        key: String,
        defValue: Float = 0f,
    ): Float = config.getFloat("$prefix/$key", defValue)

    protected fun getBoolean(
        key: String,
        defValue: Boolean = false,
    ): Boolean = config.getBool("$prefix/$key", defValue)

    protected fun getItem(key: String): Config = config.getItem("$prefix/$key")

    protected fun getList(key: String): List<Config> = config.getList("$prefix/$key")

    protected fun getStringList(
        key: String,
        defValue: List<String> = emptyList(),
    ): List<String> {
        if (config.isList("$prefix/$key")) {
            return config.getStringList("$prefix/$key")
        } else if (config.isValue("$prefix/$key")) {
            return listOf(config.getString("$prefix/$key"))
        }
        return defValue
    }
}
