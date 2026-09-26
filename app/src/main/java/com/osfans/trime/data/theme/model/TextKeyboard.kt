/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.osfans.trime.util.enum
import kotlinx.serialization.Serializable

@Serializable
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
    val roundCorner: Float = -1f,
    val keyBorder: Int = -1,
    val columns: Int = 30,
    val asciiMode: Int = 1,
    val resetAsciiMode: Boolean = false,
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
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val importPreset: String = "",
    val keys: List<TextKey> = emptyList(),
) {
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    @Serializable
    data class TextKey(
        val width: Float = 0f,
        val height: Float = 0f,
        val roundCorner: Float = -1f,
        val keyBorder: Int = -1,
        val label: String = "",
        val labelSymbol: String = "",
        val hint: String = "",
        val sendBindings: Boolean = true,
        val keyTextSize: Float = 0f,
        val symbolTextSize: Float = 0f,
        val keyTextOffsetX: Float = 0f,
        val keyTextOffsetY: Float = 0f,
        val keySymbolOffsetX: Float = 0f,
        val keySymbolOffsetY: Float = 0f,
        val keyHintOffsetX: Float = 0f,
        val keyHintOffsetY: Float = 0f,
        val keyPressOffsetX: Float = 0f,
        val keyPressOffsetY: Float = 0f,
        val keyTextColor: String = "",
        val keyBackColor: String = "",
        val keyBorderColor: String = "",
        val keySymbolColor: String = "",
        val hilitedKeyTextColor: String = "",
        val hilitedKeyBackColor: String = "",
        val hilitedKeyBorderColor: String = "",
        val hilitedKeySymbolColor: String = "",
        @Serializable(with = LenientStringListSerializer::class)
        val popup: List<String> = emptyList(),
        // behaviors
        val click: KeyActionToken? = null,
        val doubleClick: KeyActionToken? = null,
        val lazyDoubleClick: KeyActionToken? = null,
        val longClick: KeyActionToken? = null,
        val swipeUp: KeyActionToken? = null,
        val swipeDown: KeyActionToken? = null,
        val swipeLeft: KeyActionToken? = null,
        val swipeRight: KeyActionToken? = null,
        val composing: KeyActionToken? = null,
        val hasMenu: KeyActionToken? = null,
        val paging: KeyActionToken? = null,
        val combo: KeyActionToken? = null,
        val ascii: KeyActionToken? = null,
    ) {
        companion object {
            val DEFAULTS = TextKey()
        }
    }

    companion object {
        val DEFAULTS = TextKeyboard()
    }
}
