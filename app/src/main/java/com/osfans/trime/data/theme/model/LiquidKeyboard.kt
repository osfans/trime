/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.osfans.trime.ime.symbol.SimpleKeyBean
import com.osfans.trime.ime.symbol.SymbolBoardType

data class LiquidKeyboard(
    val singleWidth: Int,
    val keyHeight: Int,
    val marginX: Float,
    val fixedKeyBar: KeyBar,
    val keyboards: List<Keyboard>,
) {
    data class KeyBar(
        val keys: List<String>,
        val position: Position,
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
        val type: SymbolBoardType,
        val name: String,
        val keys: List<SimpleKeyBean>,
    )
}
