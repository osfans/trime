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
 * Planned (R3) as the color access root for UI code, replacing the
 * string-keyed calls at the ~35 static call sites.
 */
internal class ThemeColors internal constructor(
    private val table: ColorTable,
) {
    val backColor: Int get() = color(ColorKey.BACK_COLOR)
    val borderColor: Int get() = color(ColorKey.BORDER_COLOR)
    val candidateBackground: Int get() = color(ColorKey.CANDIDATE_BACKGROUND)
    val candidateBorderColor: Int get() = color(ColorKey.CANDIDATE_BORDER_COLOR)
    val candidateSeparatorColor: Int get() = color(ColorKey.CANDIDATE_SEPARATOR_COLOR)
    val candidateTextColor: Int get() = color(ColorKey.CANDIDATE_TEXT_COLOR)
    val commentTextColor: Int get() = color(ColorKey.COMMENT_TEXT_COLOR)
    val hilitedBackColor: Int get() = color(ColorKey.HILITED_BACK_COLOR)
    val hilitedCandidateBackColor: Int get() = color(ColorKey.HILITED_CANDIDATE_BACK_COLOR)
    val hilitedCandidateButtonColor: Int get() = color(ColorKey.HILITED_CANDIDATE_BUTTON_COLOR)
    val hilitedCandidateTextColor: Int get() = color(ColorKey.HILITED_CANDIDATE_TEXT_COLOR)
    val hilitedCommentTextColor: Int get() = color(ColorKey.HILITED_COMMENT_TEXT_COLOR)
    val hilitedKeyBackColor: Int get() = color(ColorKey.HILITED_KEY_BACK_COLOR)
    val hilitedKeyBorderColor: Int get() = color(ColorKey.HILITED_KEY_BORDER_COLOR)
    val hilitedKeyTextColor: Int get() = color(ColorKey.HILITED_KEY_TEXT_COLOR)
    val hilitedKeySymbolColor: Int get() = color(ColorKey.HILITED_KEY_SYMBOL_COLOR)
    val hilitedLabelColor: Int get() = color(ColorKey.HILITED_LABEL_COLOR)
    val hilitedOffKeyBackColor: Int get() = color(ColorKey.HILITED_OFF_KEY_BACK_COLOR)
    val hilitedOffKeyBorderColor: Int get() = color(ColorKey.HILITED_OFF_KEY_BORDER_COLOR)
    val hilitedOffKeyTextColor: Int get() = color(ColorKey.HILITED_OFF_KEY_TEXT_COLOR)
    val hilitedOffKeySymbolColor: Int get() = color(ColorKey.HILITED_OFF_KEY_SYMBOL_COLOR)
    val hilitedOnKeyBackColor: Int get() = color(ColorKey.HILITED_ON_KEY_BACK_COLOR)
    val hilitedOnKeyBorderColor: Int get() = color(ColorKey.HILITED_ON_KEY_BORDER_COLOR)
    val hilitedOnKeyTextColor: Int get() = color(ColorKey.HILITED_ON_KEY_TEXT_COLOR)
    val hilitedOnKeySymbolColor: Int get() = color(ColorKey.HILITED_ON_KEY_SYMBOL_COLOR)
    val hilitedPopupBackColor: Int get() = color(ColorKey.HILITED_POPUP_BACK_COLOR)
    val hilitedPopupTextColor: Int get() = color(ColorKey.HILITED_POPUP_TEXT_COLOR)
    val hilitedTextColor: Int get() = color(ColorKey.HILITED_TEXT_COLOR)
    val keyBackColor: Int get() = color(ColorKey.KEY_BACK_COLOR)
    val keyBorderColor: Int get() = color(ColorKey.KEY_BORDER_COLOR)
    val keyTextColor: Int get() = color(ColorKey.KEY_TEXT_COLOR)
    val keySymbolColor: Int get() = color(ColorKey.KEY_SYMBOL_COLOR)
    val keyboardBackColor: Int get() = color(ColorKey.KEYBOARD_BACK_COLOR)
    val keyboardBackground: Int get() = color(ColorKey.KEYBOARD_BACKGROUND)
    val labelColor: Int get() = color(ColorKey.LABEL_COLOR)
    val liquidKeyboardBackground: Int get() = color(ColorKey.LIQUID_KEYBOARD_BACKGROUND)
    val longTextBackColor: Int get() = color(ColorKey.LONG_TEXT_BACK_COLOR)
    val longTextColor: Int get() = color(ColorKey.LONG_TEXT_COLOR)
    val offKeyBackColor: Int get() = color(ColorKey.OFF_KEY_BACK_COLOR)
    val offKeyBorderColor: Int get() = color(ColorKey.OFF_KEY_BORDER_COLOR)
    val offKeyTextColor: Int get() = color(ColorKey.OFF_KEY_TEXT_COLOR)
    val offKeySymbolColor: Int get() = color(ColorKey.OFF_KEY_SYMBOL_COLOR)
    val onKeyBackColor: Int get() = color(ColorKey.ON_KEY_BACK_COLOR)
    val onKeyBorderColor: Int get() = color(ColorKey.ON_KEY_BORDER_COLOR)
    val onKeyTextColor: Int get() = color(ColorKey.ON_KEY_TEXT_COLOR)
    val onKeySymbolColor: Int get() = color(ColorKey.ON_KEY_SYMBOL_COLOR)
    val popupBackColor: Int get() = color(ColorKey.POPUP_BACK_COLOR)
    val popupTextColor: Int get() = color(ColorKey.POPUP_TEXT_COLOR)
    val rootBackground: Int get() = color(ColorKey.ROOT_BACKGROUND)
    val shadowColor: Int get() = color(ColorKey.SHADOW_COLOR)
    val textBackColor: Int get() = color(ColorKey.TEXT_BACK_COLOR)
    val textColor: Int get() = color(ColorKey.TEXT_COLOR)

    @ColorInt
    private fun color(key: ColorKey): Int = when (val value = table[key]) {
        is ColorTable.Value.Color -> value.argb
        else -> throw IllegalArgumentException("'${key.key}' is not a color")
    }
}
