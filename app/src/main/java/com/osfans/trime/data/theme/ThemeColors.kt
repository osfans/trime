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
    val backColor: Int get() = color(ColorKey.KEYBOARD_BACK_COLOR)
    val borderColor: Int get() = color(ColorKey.KEYBOARD_BORDER_COLOR)
    val candidateBackground: Int get() = color(ColorKey.CANDIDATE_BACK_COLOR)
    val candidateBorderColor: Int get() = color(ColorKey.CANDIDATE_BORDER_COLOR)
    val candidateSeparatorColor: Int get() = color(ColorKey.CANDIDATE_SEPARATOR_COLOR)
    val candidateTextColor: Int get() = color(ColorKey.CANDIDATE_TEXT_COLOR)
    val commentTextColor: Int get() = color(ColorKey.COMMENT_TEXT_COLOR)
    val candidateSelectedBackColor: Int get() = color(ColorKey.CANDIDATE_SELECTED_BACK_COLOR)
    val candidateSelectedTextColor: Int get() = color(ColorKey.CANDIDATE_SELECTED_TEXT_COLOR)
    val commentSelectedTextColor: Int get() = color(ColorKey.COMMENT_SELECTED_TEXT_COLOR)
    val preeditBackColor: Int get() = color(ColorKey.PREEDIT_BACK_COLOR)
    val preeditTextColor: Int get() = color(ColorKey.PREEDIT_TEXT_COLOR)
    val preeditSelectedBackColor: Int get() = color(ColorKey.PREEDIT_SELECTED_BACK_COLOR)
    val preeditSelectedTextColor: Int get() = color(ColorKey.PREEDIT_SELECTED_TEXT_COLOR)
    val candidateButtonPressedBackColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_PRESSED_BACK_COLOR)
    val candidateButtonPressedTextColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_PRESSED_TEXT_COLOR)
    val keyBackColor: Int get() = color(ColorKey.KEY_BACK_COLOR)
    val keyBorderColor: Int get() = color(ColorKey.KEY_BORDER_COLOR)
    val keyTextColor: Int get() = color(ColorKey.KEY_TEXT_COLOR)
    val keySymbolColor: Int get() = color(ColorKey.KEY_SYMBOL_COLOR)
    val keyPressedBackColor: Int get() = color(ColorKey.KEY_PRESSED_BACK_COLOR)
    val keyPressedBorderColor: Int get() = color(ColorKey.KEY_PRESSED_BORDER_COLOR)
    val keyPressedTextColor: Int get() = color(ColorKey.KEY_PRESSED_TEXT_COLOR)
    val keyPressedSymbolColor: Int get() = color(ColorKey.KEY_PRESSED_SYMBOL_COLOR)
    val labelColor: Int get() = color(ColorKey.LABEL_COLOR)
    val labelSelectedTextColor: Int get() = color(ColorKey.LABEL_SELECTED_TEXT_COLOR)
    val functionKeyBackColor: Int get() = color(ColorKey.FUNCTION_KEY_BACK_COLOR)
    val functionKeyBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_BORDER_COLOR)
    val functionKeyTextColor: Int get() = color(ColorKey.FUNCTION_KEY_TEXT_COLOR)
    val functionKeySymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_SYMBOL_COLOR)
    val functionKeyPressedBackColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_BACK_COLOR)
    val functionKeyPressedBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_BORDER_COLOR)
    val functionKeyPressedTextColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_TEXT_COLOR)
    val functionKeyPressedSymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_SYMBOL_COLOR)
    val functionKeyOnBackColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_BACK_COLOR)
    val functionKeyOnBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_BORDER_COLOR)
    val functionKeyOnTextColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_TEXT_COLOR)
    val functionKeyOnSymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_SYMBOL_COLOR)
    val functionKeyOnPressedBackColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_BACK_COLOR)
    val functionKeyOnPressedBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_BORDER_COLOR)
    val functionKeyOnPressedTextColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_TEXT_COLOR)
    val functionKeyOnPressedSymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_ON_PRESSED_SYMBOL_COLOR)
    val popupBackColor: Int get() = color(ColorKey.POPUP_BACK_COLOR)
    val popupTextColor: Int get() = color(ColorKey.POPUP_TEXT_COLOR)
    val popupSelectedBackColor: Int get() = color(ColorKey.POPUP_SELECTED_BACK_COLOR)
    val popupSelectedTextColor: Int get() = color(ColorKey.POPUP_SELECTED_TEXT_COLOR)
    val keyboardBackColor: Int get() = color(ColorKey.KEYBOARD_BACK_COLOR)
    val keyboardBackground: Int get() = color(ColorKey.KEYBOARD_BACK_COLOR)
    val liquidKeyboardBackground: Int get() = color(ColorKey.LIQUID_KEYBOARD_BACK_COLOR)
    val longTextBackColor: Int get() = color(ColorKey.LONG_TEXT_BACK_COLOR)
    val longTextColor: Int get() = color(ColorKey.LONG_TEXT_COLOR)
    val rootBackground: Int get() = color(ColorKey.ROOT_BACK_COLOR)
    val shadowColor: Int get() = color(ColorKey.KEY_SHADOW_COLOR)

    @ColorInt
    private fun color(key: ColorKey): Int = when (val value = table[key]) {
        is ColorTable.Value.Color -> value.argb
        else -> throw IllegalArgumentException("'${key.key}' is not a color")
    }
}
