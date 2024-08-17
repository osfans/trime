// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import com.osfans.trime.core.Rime
import com.osfans.trime.data.prefs.AppPrefs
import kotlinx.serialization.builtins.ListSerializer

object SchemaManager {
    private lateinit var currentSchema: Schema
    lateinit var visibleSwitches: List<Schema.Switch>

    private val arrow get() = AppPrefs.defaultInstance().keyboard.switchArrowEnabled

    private val defaultSchema = Schema()

    @JvmStatic
    fun init(schemaId: String) {
        currentSchema = runCatching { Schema(schemaId) }.getOrDefault(defaultSchema)
        visibleSwitches = currentSchema.switches
            ?.decode(ListSerializer(Schema.Switch.serializer()))
            ?.filter { !it.states.isNullOrEmpty() } ?: listOf() // 剔除没有 states 条目项的值，它们不作为开关使用
        updateSwitchOptions()
    }

    val activeSchema: Schema
        get() = runCatching { currentSchema }.getOrDefault(defaultSchema)

    @JvmStatic
    fun updateSwitchOptions() {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return // 無方案
        visibleSwitches.forEach { s ->
            s.enabled =
                if (s.options.isNullOrEmpty()) { // 只有单 Rime 运行时选项的开关，开关名即选项名，标记其启用状态
                    Rime.getRimeOption(s.name!!).compareTo(false)
                } else { // 带有一系列 Rime 运行时选项的开关，找到启用的选项并标记
                    // 将启用状态标记为此选项的索引值，方便切换时直接从选项列表中获取
                    // 注意：有可能每个 option 的状态都为 false（未启用）, 因此 indexOfFirst 可能会返回 -1,
                    // 需要 coerceAtLeast 确保其至少为 0
                    s.options.indexOfFirst { Rime.getRimeOption(it) }.coerceAtLeast(0)
                }
        }
    }
}
