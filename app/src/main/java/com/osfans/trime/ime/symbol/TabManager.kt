// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import timber.log.Timber

object TabManager {
    var currentTabIndex = -1
        private set

    val tabTags = arrayListOf<TabTag>()
    private val keyboards = mutableListOf<List<LiquidKeyboard.KeyItem>>()

    fun resetCache(theme: Theme) {
        tabTags.clear()
        keyboards.clear()

        theme.liquidKeyboard.keyboards.forEach {
            addListTab(it.name, it.type, it.keys)
        }
        Timber.d("tabTags: ${tabTags.joinToString()}")
    }

    private fun addListTab(
        name: String,
        type: SymbolBoardType,
        keys: List<LiquidKeyboard.KeyItem>,
    ) {
        if (name.isBlank()) return
        if (SymbolBoardType.hasKeys(type)) {
            val index = tabTags.indexOfFirst { it.text == name }
            if (index >= 0) {
                keyboards[index] = keys
                return
            }
        }
        tabTags.add(TabTag(name, type))
        keyboards.add(keys)
    }

    fun selectTabByIndex(index: Int): List<LiquidKeyboard.KeyItem> {
        if (index !in tabTags.indices) return listOf()
        currentTabIndex = index
        val tag = tabTags[index]
        if (tag.type == SymbolBoardType.TABS) {
            return tabTags
                .filter { SymbolBoardType.hasKey(it.type) }
                .map { LiquidKeyboard.KeyItem(it.text) }
        }
        return keyboards[index]
    }

    fun setTabExited() {
        currentTabIndex = -1
    }
}
