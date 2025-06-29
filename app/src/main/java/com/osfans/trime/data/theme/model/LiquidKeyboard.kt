/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.symbol.SimpleKeyBean
import com.osfans.trime.ime.symbol.SymbolBoardType
import kotlinx.parcelize.Parcelize

@Parcelize
data class LiquidKeyboard(
    val singleWidth: Int,
    val keyHeight: Int,
    val marginX: Float,
    val fixedKeyBar: KeyBar,
    val keyboards: List<Keyboard>,
) : Parcelable {
    @Parcelize
    data class KeyBar(
        val keys: List<String>,
        val position: Position,
    ) : Parcelable {
        enum class Position {
            TOP,
            LEFT,
            BOTTOM,
            RIGHT,
        }
    }

    @Parcelize
    data class Keyboard(
        val id: String,
        val type: SymbolBoardType,
        val name: String,
        val keys: List<SimpleKeyBean>,
    ) : Parcelable
}
