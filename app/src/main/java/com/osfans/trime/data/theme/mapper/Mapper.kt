// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.util.config.ConfigItem
import com.osfans.trime.util.config.ConfigList
import com.osfans.trime.util.config.ConfigMap
import com.osfans.trime.util.config.ConfigValue

open class Mapper(
    private val map: Map<String, ConfigItem?>?,
) {
    val errors = ArrayList<String>()

    protected fun getString(
        key: String,
        defValue: String = "",
    ): String {
        if (map.isNullOrEmpty() || key.isEmpty()) return defValue
        val v = map[key]
        return v?.configValue?.getString() ?: run {
            addError(key)
            defValue
        }
    }

    protected fun getInt(
        key: String,
        defValue: Int = 0,
    ): Int {
        if (map.isNullOrEmpty() || key.isEmpty()) return defValue
        val v = map[key]
        return runCatching {
            v!!.configValue.getInt()
        }.getOrElse {
            addError(key)
            defValue
        }
    }

    protected fun getFloat(
        key: String,
        defValue: Float = 0f,
    ): Float {
        if (map.isNullOrEmpty() || key.isEmpty()) return defValue
        val v = map[key]
        return runCatching {
            v!!.configValue.getFloat()
        }.getOrElse {
            addError(key)
            defValue
        }
    }

    protected fun getBoolean(
        key: String,
        defValue: Boolean = false,
    ): Boolean {
        if (map.isNullOrEmpty() || key.isEmpty()) return defValue
        val v = map[key]
        return runCatching {
            v!!.configValue.getBool()
        }.getOrElse {
            addError(key)
            defValue
        }
    }

    protected fun getObject(key: String): ConfigMap? =
        map?.get(key)?.configMap
            ?: run {
                addError(key)
                null
            }

    protected fun getList(key: String): List<ConfigItem> =
        runCatching {
            map?.get(key)?.configList?.mapNotNull {
                it
            } ?: run {
                addError(key)
                listOf()
            }
        }.getOrElse {
            addError(key)
            listOf()
        }

    protected fun getStringList(
        key: String,
        defValue: List<String> = listOf(),
    ): List<String> {
        val obj = map?.get(key)

        return if (obj is ConfigValue) {
            arrayListOf(obj.getString())
        } else if (obj is ConfigList) {
            obj.configList
                .mapNotNull {
                    it?.configValue?.getString()
                }.takeIf { it.isNotEmpty() } ?: defValue
        } else {
            addError(key)
            defValue
        }
    }

    private fun addError(key: String) {
        errors.add(key)
    }
}
