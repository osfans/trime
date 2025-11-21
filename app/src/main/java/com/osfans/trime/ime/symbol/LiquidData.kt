/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard

object LiquidData {
    enum class Type {
        SINGLE,
        SYMBOL,
        TABS,
        HISTORY,
    }

    data class Tag(val label: String = "", val type: Type)

    private val data = arrayListOf<Pair<Tag, Array<LiquidKeyboard.KeyItem>>>()

    fun init(theme: Theme) {
        data.clear()

        val transformed = theme.liquidKeyboard.keyboards.map {
            Tag(it.name, it.type) to
                it.keys.toTypedArray()
        }
        data.addAll(transformed)
    }

    fun getTagList() = data.map { it.first }

    fun getDataByIndex(index: Int): List<LiquidKeyboard.KeyItem> {
        val item = data[index]
        val tag = item.first
        return if (tag.type == Type.TABS) {
            data.map { LiquidKeyboard.KeyItem(it.first.label) }
        } else {
            item.second.toList()
        }
    }
}
