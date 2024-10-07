// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.config.ConfigItem
import com.osfans.trime.util.config.ConfigList
import com.osfans.trime.util.config.ConfigMap
import com.osfans.trime.util.config.ConfigValue
import timber.log.Timber

object TabManager {
    var currentTabIndex = -1
        private set

    val tabTags = arrayListOf<TabTag>()
    private val keyboards = mutableListOf<List<SimpleKeyBean>>()

    fun resetCache(theme: Theme) {
        tabTags.clear()
        keyboards.clear()

        val available = theme.liquid.getList("keyboards") ?: return
        for (item in available) {
            val id = item?.configValue?.getString() ?: ""
            Timber.d("preparing data for tab #$id")
            val keyboard = theme.liquid.getMap(id) ?: continue
            if (!keyboard.containsKey("type")) continue
            val name = keyboard.getValue("name")?.getString() ?: id
            val type = SymbolBoardType.fromString(keyboard.getValue("type")?.getString())
            val keys = keyboard["keys"]
            addTabHasKeys(name, type, keys)
        }
    }

    private fun addListTab(
        name: String,
        type: SymbolBoardType,
        keyBeans: List<SimpleKeyBean>,
    ) {
        if (name.isBlank()) return
        if (SymbolBoardType.hasKeys(type)) {
            val index = tabTags.indexOfFirst { it.text == name }
            if (index >= 0) {
                keyboards[index] = keyBeans
                return
            }
        }
        tabTags.add(TabTag(name, type))
        keyboards.add(keyBeans)
    }

    private fun addTabHasKeys(
        name: String,
        type: SymbolBoardType,
        keys: ConfigItem?,
    ) {
        if (keys is ConfigValue?) {
            // 对于没有按键的类型，也要返回一个空的 key 值，否则无法显示在标签栏内
            val key = keys?.configValue?.getString() ?: ""
            when (type) {
                // 处理 SINGLE 类型：把字符串切分为多个按键
                SymbolBoardType.SINGLE -> addListTab(name, type, SimpleKeyDao.singleData(key))
                else -> addListTab(name, type, SimpleKeyDao.simpleKeyboardData(key))
            }
        }

        if (keys !is ConfigList) return
        val keysList = mutableListOf<SimpleKeyBean>()
        for (k in keys) {
            if (k is ConfigValue) {
                keysList.add(SimpleKeyBean(k.getString()))
            }

            if (k !is ConfigMap) continue
            val p = k.entries.associate { (s, n) -> s to n!!.configValue.getString() }
            if (k.containsKey("click")) {
                if (p.containsKey("label")) {
                    keysList.add(SimpleKeyBean(p["click"]!!, p["label"]!!))
                } else {
                    keysList.add(SimpleKeyBean(p["click"]!!))
                }
                continue
            }

            val symbolMaps = SchemaManager.activeSchema.symbols
            for ((key, value) in p) {
                if (symbolMaps?.containsKey(value) == true) {
                    keysList.add(SimpleKeyBean(value, key))
                }
            }
        }
        addListTab(name, type, keysList)
    }

    fun selectTabByIndex(index: Int): List<SimpleKeyBean> {
        if (index !in tabTags.indices) return listOf()
        currentTabIndex = index
        val tag = tabTags[index]
        if (tag.type == SymbolBoardType.TABS) {
            return tabTags
                .filter { SymbolBoardType.hasKey(it.type) }
                .map { SimpleKeyBean(it.text) }
        }
        return keyboards[index]
    }

    fun setTabExited() {
        currentTabIndex = -1
    }
}
