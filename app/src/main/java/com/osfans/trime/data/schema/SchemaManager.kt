package com.osfans.trime.data.schema

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.core.Rime
import com.osfans.trime.core.Rime.RimeCandidate
import com.osfans.trime.data.AppPrefs
import java.io.File

object SchemaManager {
    private lateinit var currentSchema: RimeSchema
    private lateinit var visibleSwitches: List<RimeSchema.Switch>

    private lateinit var symbolMap: Map<String, List<String>>

    private val arrow get() = AppPrefs.defaultInstance().keyboard.switchArrowEnabled

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    @JvmStatic
    fun init(schemaId: String) {
        currentSchema = yaml.decodeFromStream(
            RimeSchema.serializer(),
            File(Rime.getRimeUserDataDir(), "build/$schemaId.schema.yaml").inputStream()
        )
        visibleSwitches = currentSchema.switches ?: listOf<RimeSchema.Switch>()
            .filter { !it.states.isNullOrEmpty() } // 剔除没有 states 条目项的值，它们不作为开关使用
        updateSwitchOptions()
        symbolMap = currentSchema.punctuator.symbols ?: mapOf()
    }

    @JvmStatic
    fun updateSwitchOptions() {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return // 無方案
        for (s in visibleSwitches) {
            if (s.options.isNullOrEmpty()) { // 只有单 Rime 运行时选项的开关，开关名即选项名，标记其启用状态
                s.enabled = if (Rime.getRimeOption(s.name!!)) 1 else 0
            } else { // 带有一系列 Rime 运行时选项的开关，找到启用的选项并标记
                for ((j, o) in s.options.withIndex()) {
                    if (Rime.getRimeOption(o)) {
                        // 将启用状态标记为此选项的索引值，方便切换时直接从选项列表中获取
                        s.enabled = j
                        break
                    }
                }
            }
        }
    }

    @JvmStatic
    fun toggleSwitchOption(index: Int) {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return
        val switch = visibleSwitches[index]
        val enabled = switch.enabled
        val next: Int
        if (switch.options.isNullOrEmpty()) {
            next = 1 - switch.enabled
            Rime.setOption(switch.name!!, next == 1)
        } else {
            val options = switch.options
            next = (enabled + 1) % options.size
            Rime.setOption(options[enabled], false)
            Rime.setOption(options[next], true)
        }
        switch.enabled = next
    }

    @JvmStatic
    fun getStatusSwitches(): Array<RimeCandidate> {
        if (!this::visibleSwitches.isInitialized || visibleSwitches.isEmpty()) return arrayOf()
        return Array(visibleSwitches.size) {
            val switch = visibleSwitches[it]
            val enabled = switch.enabled
            val text = switch.states!![enabled]
            val comment = if (switch.options.isNullOrEmpty()) {
                "${if (arrow) "→ " else ""}${switch.states[1 - enabled]}"
            } else ""
            RimeCandidate(text, comment)
        }
    }

    @JvmStatic
    fun hasSymbols(key: String) = symbolMap.containsKey(key)
}
