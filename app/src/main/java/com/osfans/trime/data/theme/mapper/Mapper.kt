// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.core.RimeConfig

open class Mapper(
    val prefix: String,
    val config: RimeConfig,
) {
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
    ): Float = config.getDouble("$prefix/$key", defValue.toDouble()).toFloat()

    protected fun getBoolean(
        key: String,
        defValue: Boolean = false,
    ): Boolean = config.getBool("$prefix/$key", defValue)

    protected fun getItem(key: String): RimeConfig = config.getItem("$prefix/$key")

    protected fun getList(key: String): List<RimeConfig> = config.getList("$prefix/$key")

    protected fun getStringList(
        key: String,
        defValue: List<String> = listOf(),
    ): List<String> {
        val list = config.getStringList("$prefix/$key")
        if (list.isNotEmpty()) return list
        val string = config.getString("$prefix/$key")
        if (string.isNotEmpty()) return listOf(string)
        return defValue
    }
}
