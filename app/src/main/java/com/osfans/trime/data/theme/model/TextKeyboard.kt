/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.keyboard.KeyBehavior
import kotlinx.parcelize.Parcelize

@Parcelize
data class TextKeyboard(
    val name: String,
    val author: String,
    val width: Float,
    val height: Float,
    val keyboardHeight: Int,
    val keyboardHeightLand: Int,
    val autoHeightIndex: Int,
    val horizontalGap: Int,
    val verticalGap: Int,
    val roundCorner: Float,
    val columns: Int,
    val asciiMode: Boolean,
    val resetAsciiMode: Boolean,
    val labelTransform: LabelTransform,
    val lock: Boolean,
    val asciiKeyboard: String,
    val landscapeKeyboard: String,
    val landscapeSplitPercent: Int,
    val keyTextOffsetX: Float,
    val keyTextOffsetY: Float,
    val keySymbolOffsetX: Float,
    val keySymbolOffsetY: Float,
    val keyHintOffsetX: Float,
    val keyHintOffsetY: Float,
    val keyPressOffsetX: Int,
    val keyPressOffsetY: Int,
    val importPreset: String,
    val keys: List<TextKey>,
) : Parcelable {
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    @Parcelize
    data class TextKey(
        val width: Float,
        val height: Float,
        val roundCorner: Float,
        val label: String,
        val labelSymbol: String,
        val hint: String,
        val click: String,
        val sendBindings: Boolean,
        val keyTextSize: Float,
        val symbolTextSize: Float,
        val keyTextOffsetX: Float,
        val keyTextOffsetY: Float,
        val keySymbolOffsetX: Float,
        val keySymbolOffsetY: Float,
        val keyHintOffsetX: Float,
        val keyHintOffsetY: Float,
        val keyPressOffsetX: Int,
        val keyPressOffsetY: Int,
        val keyTextColor: String,
        val keyBackColor: String,
        val keySymbolColor: String,
        val hlKeyTextColor: String,
        val hlKeyBackColor: String,
        val hlKeySymbolColor: String,
        val behaviors: Map<KeyBehavior, String>,
    ) : Parcelable
}
