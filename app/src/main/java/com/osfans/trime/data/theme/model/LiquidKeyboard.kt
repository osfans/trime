/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable(with = LiquidKeyboardSerializer::class)
data class LiquidKeyboard(
    val singleWidth: Int = 0,
    val keyHeight: Int = 0,
    val marginX: Float = 0f,
    val fixedKeyBar: KeyBar = KeyBar(),
    val keyboards: List<Keyboard> = emptyList(),
) {

    @Serializable
    data class KeyBar(
        val keys: List<String> = emptyList(),
        val position: Position = Position.BOTTOM,
    ) {
        enum class Position {
            TOP,
            LEFT,
            BOTTOM,
            RIGHT,
        }
    }

    data class Keyboard(
        val id: String,
        val type: DataType,
        val name: String,
        val keys: List<KeyItem>,
    )

    data class KeyItem(
        val text: String,
        val altText: String,
    ) {
        constructor(text: String) : this(text, text)
    }

    enum class DataType {
        SINGLE,
        SYMBOL,
        TABS,
        HISTORY,
    }

    data class Tag(val label: String = "", val type: DataType)

    @Transient
    private val data = lazy {
        keyboards.map {
            Tag(it.name, it.type) to it.keys.toTypedArray()
        }
    }

    fun getTagList() = data.value.map { it.first }

    fun getDataByIndex(index: Int): List<KeyItem> {
        val item = data.value[index]
        val tag = item.first
        return if (tag.type == DataType.TABS) {
            data.value.mapNotNull {
                if (it.first.type != DataType.TABS) KeyItem(it.first.label) else null
            }
        } else {
            item.second.toList()
        }
    }
}
