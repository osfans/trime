// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.schema

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.launchOnReady
import timber.log.Timber
import kotlin.math.max

object SchemaManager {
    val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                ),
        )

    private lateinit var currentSchema: Schema

    val activeSchema: Schema
        get() =
            try {
                currentSchema
            } catch (e: Exception) {
                Timber.w(e, "Failed to get activeSchema")
                Schema()
            }

    fun init(schemaId: String) {
        runCatching {
            currentSchema = Schema.decodeBySchemaId(schemaId)
            updateSwitchOptions()
        }.getOrElse {
            Timber.w(it, "Failed to decode schema file of id '$schemaId'")
        }
    }

    fun updateSwitchOptions() {
        if (activeSchema.switches.isEmpty()) return // 無方案
        RimeDaemon
            .getFirstSessionOrNull()
            ?.launchOnReady { api ->
                for (s in activeSchema.switches) {
                    val labels = s.states
                    // 剔除没有 states 条目项的值，它们不作为开关使用
                    if (labels.size <= 1) continue
                    if (s.name.isNotEmpty()) {
                        if (labels.size != 2) continue
                        // 只有单 Rime 运行时选项的开关，开关名即选项名，标记其启用状态
                        s.enabledIndex = api.getRuntimeOption(s.name).compareTo(false)
                    } else {
                        // 带有一系列 Rime 运行时选项的开关，找到启用的选项并标记
                        // 将启用状态标记为此选项的索引值，方便切换时直接从选项列表中获取
                        // 注意：有可能每个 option 的状态都为 false（未启用）, 因此 indexOfFirst 可能会返回 -1,
                        // 需要确保其至少为 0
                        val options = s.options
                        if (options.size != labels.size) continue
                        s.enabledIndex = max(0, options.indexOfFirst { api.getRuntimeOption(it) })
                    }
                }
            }
    }
}
