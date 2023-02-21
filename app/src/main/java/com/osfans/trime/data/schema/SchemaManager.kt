package com.osfans.trime.data.schema

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.core.CandidateListItem
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import java.io.File

object SchemaManager {
    private lateinit var currentSchema: RimeSchema
    private lateinit var visibleSwitches: List<RimeSchema.Switch>

    private val defaultSchema = RimeSchema(RimeSchema.Schema(".default"))

    private val arrow get() = AppPrefs.defaultInstance().keyboard.switchArrowEnabled

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    @JvmStatic
    fun init(schemaId: String) {
        val raw = runCatching {
            File(DataManager.buildDir, "$schemaId.schema.yaml")
                .bufferedReader().use { it.readText() }
        }.getOrDefault("")
        currentSchema = runCatching {
            yaml.decodeFromString(
                RimeSchema.serializer(),
                raw
            )
        }.getOrDefault(defaultSchema)
        visibleSwitches = currentSchema.switches
            .filter { !it.states.isNullOrEmpty() } // 剔除没有 states 条目项的值，它们不作为开关使用
        updateSwitchOptions()
    }

    @JvmStatic
    fun getActiveSchema() = currentSchema

    @JvmStatic
    fun updateSwitchOptions() {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return // 無方案
        visibleSwitches.forEach { s ->
            s.enabled = if (s.options.isNullOrEmpty()) { // 只有单 Rime 运行时选项的开关，开关名即选项名，标记其启用状态
                Rime.getRimeOption(s.name!!).compareTo(false)
            } else { // 带有一系列 Rime 运行时选项的开关，找到启用的选项并标记
                // 将启用状态标记为此选项的索引值，方便切换时直接从选项列表中获取
                s.options.indexOfFirst { Rime.getRimeOption(it) }
            }
        }
    }

    @JvmStatic
    fun toggleSwitchOption(index: Int) {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return
        val switch = visibleSwitches[index]
        val enabled = switch.enabled
        switch.enabled = if (switch.options.isNullOrEmpty()) {
            (1 - enabled).also { next -> Rime.setOption(switch.name!!, next == 1) }
        } else {
            val options = switch.options
            ((enabled + 1) % options.size).also { next ->
                Rime.setOption(options[enabled], false)
                Rime.setOption(options[next], true)
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
            val comment = if (switch.options.isNullOrEmpty()) {
                "${if (arrow) "→ " else ""}${switch.states[1 - enabled]}"
            } else ""
            CandidateListItem(comment, text)
        }
    }
}
