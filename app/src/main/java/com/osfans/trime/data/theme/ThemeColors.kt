/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import androidx.annotation.ColorInt

/**
 * Typed, read-only view over the resolved colors of the active color scheme.
 *
 * Each property is one [ColorKey]; reading it is an O(1) lookup into the
 * pre-compiled [ColorTable]. Keys whose value is an image or that no chain
 * resolves throw [IllegalArgumentException], exactly like
 * `ColorManager.getColor(key)` with the same key.
 *
 * The color access root for UI code: reach it through [ThemeScope.colors] and
 * re-read values on every bind or draw — the instance is replaced whenever
 * the scheme switches.
 */
class ThemeColors internal constructor(
    private val table: ColorTable,
) {
    val backColor: Int get() = color(ColorKey.KEYBOARD_NORMAL_BACK_COLOR)
    val borderColor: Int get() = color(ColorKey.KEYBOARD_NORMAL_BORDER_COLOR)
    val candidateBackground: Int get() = color(ColorKey.CANDIDATE_NORMAL_BACK_COLOR)
    val candidateBorderColor: Int get() = color(ColorKey.CANDIDATE_NORMAL_BORDER_COLOR)
    val candidateSeparatorColor: Int get() = color(ColorKey.CANDIDATE_NORMAL_SEPARATOR_COLOR)
    val candidateTextColor: Int get() = color(ColorKey.CANDIDATE_NORMAL_TEXT_COLOR)
    val commentTextColor: Int get() = color(ColorKey.COMMENT_NORMAL_TEXT_COLOR)
    val preeditSelectedBackColor: Int get() = color(ColorKey.PREEDIT_SELECTED_BACK_COLOR)
    val candidateSelectedBackColor: Int get() = color(ColorKey.CANDIDATE_SELECTED_BACK_COLOR)
    val candidateButtonPressedBackColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_PRESSED_BACK_COLOR)
    val candidateButtonNormalBackColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_NORMAL_BACK_COLOR)
    val candidateButtonNormalTextColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_NORMAL_TEXT_COLOR)
    val candidateButtonPressedTextColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_PRESSED_TEXT_COLOR)
    val candidateSelectedTextColor: Int get() = color(ColorKey.CANDIDATE_SELECTED_TEXT_COLOR)
    val commentSelectedTextColor: Int get() = color(ColorKey.COMMENT_SELECTED_TEXT_COLOR)
    val keyPressedBackColor: Int get() = color(ColorKey.KEY_PRESSED_BACK_COLOR)
    val keyPressedBorderColor: Int get() = color(ColorKey.KEY_PRESSED_BORDER_COLOR)
    val keyPressedTextColor: Int get() = color(ColorKey.KEY_PRESSED_TEXT_COLOR)
    val keyPressedSymbolColor: Int get() = color(ColorKey.KEY_PRESSED_SYMBOL_COLOR)
    val labelSelectedTextColor: Int get() = color(ColorKey.LABEL_SELECTED_TEXT_COLOR)
    val functionKeyPressedBackColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_BACK_COLOR)
    val functionKeyPressedBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_BORDER_COLOR)
    val functionKeyPressedTextColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_TEXT_COLOR)
    val functionKeyPressedSymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_SYMBOL_COLOR)
    val functionKeyOnPressedBackColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_BACK_COLOR)
    val functionKeyOnPressedBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_BORDER_COLOR)
    val functionKeyOnPressedTextColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_TEXT_COLOR)
    val functionKeyOnPressedSymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_SYMBOL_COLOR)
    val popupSelectedBackColor: Int get() = color(ColorKey.POPUP_SELECTED_BACK_COLOR)
    val popupSelectedTextColor: Int get() = color(ColorKey.POPUP_SELECTED_TEXT_COLOR)
    val preeditSelectedTextColor: Int get() = color(ColorKey.PREEDIT_SELECTED_TEXT_COLOR)
    val keyNormalBackColor: Int get() = color(ColorKey.KEY_NORMAL_BACK_COLOR)
    val keyNormalBorderColor: Int get() = color(ColorKey.KEY_NORMAL_BORDER_COLOR)
    val keyNormalTextColor: Int get() = color(ColorKey.KEY_NORMAL_TEXT_COLOR)
    val keyNormalSymbolColor: Int get() = color(ColorKey.KEY_NORMAL_SYMBOL_COLOR)
    val keyboardBackColor: Int get() = color(ColorKey.KEYBOARD_NORMAL_BACK_COLOR)
    val keyboardBackground: Int get() = color(ColorKey.KEYBOARD_NORMAL_BACK_COLOR)
    val labelColor: Int get() = color(ColorKey.LABEL_NORMAL_TEXT_COLOR)
    val liquidKeyboardBackground: Int get() = color(ColorKey.LIQUID_KEYBOARD_NORMAL_BACK_COLOR)
    val longTextBackColor: Int get() = color(ColorKey.LONG_TEXT_NORMAL_BACK_COLOR)
    val longTextColor: Int get() = color(ColorKey.LONG_TEXT_NORMAL_TEXT_COLOR)
    val offKeyBackColor: Int get() = color(ColorKey.FUNCTION_KEY_NORMAL_BACK_COLOR)
    val offKeyBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_NORMAL_BORDER_COLOR)
    val offKeyTextColor: Int get() = color(ColorKey.FUNCTION_KEY_NORMAL_TEXT_COLOR)
    val offKeySymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_NORMAL_SYMBOL_COLOR)
    val onKeyBackColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_BACK_COLOR)
    val onKeyBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_BORDER_COLOR)
    val onKeyTextColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_TEXT_COLOR)
    val onKeySymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_SYMBOL_COLOR)
    val popupBackColor: Int get() = color(ColorKey.POPUP_NORMAL_BACK_COLOR)
    val popupTextColor: Int get() = color(ColorKey.POPUP_NORMAL_TEXT_COLOR)
    val rootBackground: Int get() = color(ColorKey.ROOT_NORMAL_BACK_COLOR)
    val shadowColor: Int get() = color(ColorKey.KEY_NORMAL_SHADOW_COLOR)
    val preeditBackColor: Int get() = color(ColorKey.PREEDIT_NORMAL_BACK_COLOR)
    val preeditTextColor: Int get() = color(ColorKey.PREEDIT_NORMAL_TEXT_COLOR)

    @ColorInt
    private fun color(key: ColorKey): Int = when (val value = table[key]) {
        is ColorTable.Value.Color -> value.argb
        else -> throw IllegalArgumentException("'${key.key}' is not a color")
    }
}
