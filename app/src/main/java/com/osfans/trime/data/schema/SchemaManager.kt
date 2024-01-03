package com.osfans.trime.data.schema

import com.osfans.trime.core.CandidateListItem
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs

object SchemaManager {
    private lateinit var currentSchema: Schema
    private lateinit var visibleSwitches: List<Schema.Switch>

    private val arrow get() = AppPrefs.defaultInstance().keyboard.switchArrowEnabled

    private val defaultSchema = Schema()

    @JvmStatic
    fun init(schemaId: String) {
        currentSchema = runCatching { Schema(schemaId) }.getOrDefault(defaultSchema)
        visibleSwitches = currentSchema.switches
            ?.filter { !it.states.isNullOrEmpty() } ?: listOf() // 剔除没有 states 条目项的值，它们不作为开关使用
        updateSwitchOptions()
    }

    @JvmStatic
    fun getActiveSchema() = currentSchema

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

    @JvmStatic
    fun toggleSwitchOption(index: Int) {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return
        val switch = visibleSwitches[index]
        val enabled = switch.enabled
        switch.enabled =
            if (switch.options.isNullOrEmpty()) {
                (1 - enabled).also { Rime.setOption(switch.name!!, it == 1) }
            } else {
                val options = switch.options
                ((enabled + 1) % options.size).also {
                    Rime.setOption(options[enabled], false)
                    Rime.setOption(options[it], true)
                }
            }
    }

    @JvmStatic
    fun getStatusSwitches(): Array<CandidateListItem> {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return arrayOf()
        return Array(visibleSwitches.size) {
            val switch = visibleSwitches[it]
            val enabled = switch.enabled
            val text = switch.states!![enabled]
            val comment =
                if (switch.options.isNullOrEmpty()) {
                    "${if (arrow) "→ " else ""}${switch.states[1 - enabled]}"
                } else {
                    ""
                }
            CandidateListItem(comment, text)
        }
    }
}
