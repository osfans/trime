/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.osfans.trime.data.theme.deserializer.LiquidKeyboardDeserializer
import com.osfans.trime.ime.symbol.SimpleKeyBean
import com.osfans.trime.ime.symbol.SymbolBoardType

@JsonDeserialize(using = LiquidKeyboardDeserializer::class)
data class LiquidKeyboard(
    val singleWidth: Int = 0,
    val keyHeight: Int = 0,
    val marginX: Float = 0f,
    val fixedKeyBar: KeyBar = KeyBar(),
    val keyboards: List<Keyboard> = emptyList(),
) {
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
        val id: String = "",
        val type: SymbolBoardType = SymbolBoardType.SINGLE,
        val name: String = "",
        val keys: List<SimpleKeyBean> = emptyList(),
    )
}
