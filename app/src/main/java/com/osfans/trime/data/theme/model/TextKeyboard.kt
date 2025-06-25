/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.osfans.trime.data.theme.deserializer.TextKeyDeserializer
import com.osfans.trime.ime.keyboard.KeyBehavior

data class TextKeyboard(
    val name: String = "",
    val author: String = "",
    val width: Float = 0f,
    val height: Float = 0f,
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val autoHeightIndex: Int = -1,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
    val roundCorner: Float = 0f,
    val columns: Int = 30,
    val asciiMode: Boolean = true,
    val resetAsciiMode: Boolean = true,
    val labelTransform: LabelTransform = LabelTransform.NONE,
    val lock: Boolean = false,
    val asciiKeyboard: String = "",
    val landscapeKeyboard: String = "",
    val landscapeSplitPercent: Int = 0,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Int = 0,
    val keyPressOffsetY: Int = 0,
    val importPreset: String = "",
    val keys: List<TextKey> = emptyList(),
) {
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    @JsonDeserialize(using = TextKeyDeserializer::class)
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
    )
}
