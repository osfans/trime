/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.osfans.trime.util.enum
import kotlinx.serialization.Serializable

@Serializable
data class GeneralStyle(
    val autoCaps: Boolean? = false,
    val candidateBorder: Int = 0,
    val candidateBorderRound: Float = 0f,
    val candidateFont: MaybeStringList = MaybeStringList.Empty,
    val candidatePadding: Int = 0,
    val candidateSpacing: Float = 0f,
    val candidateTextSize: Float = 15f,
    val candidateTextVerticalBias: Float = 1f,
    val candidateViewHeight: Int = 28,
    val candidateCornerRadius: Float = 5f,
    val commentFont: MaybeStringList = MaybeStringList.Empty,
    val commentHeight: Int = 12,
    val commentPosition: CommentPosition = CommentPosition.RIGHT,
    val commentTextSize: Float = 10f,
    val commentVerticalBias: Float = 0f,
    val hanbFont: MaybeStringList = MaybeStringList.Empty,
    val horizontalGap: Int = 0,
    val keyboardPadding: Int = 0,
    val keyboardPaddingLeft: Int = 0,
    val keyboardPaddingRight: Int = 0,
    val keyboardPaddingBottom: Int = 0,
    val keyboardPaddingLand: Int = 0,
    val keyboardPaddingLandBottom: Int = 0,
    val keyFont: MaybeStringList = MaybeStringList.Empty,
    val keyBorder: Int = 0,
    val keyHeight: Int = 0,
    val keyLongTextSize: Float = 15f,
    val keyTextSize: Float = 15f,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val keyWidth: Float = 0f,
    val labelTextSize: Float = 0f,
    val labelFont: MaybeStringList = MaybeStringList.Empty,
    val latinFont: MaybeStringList = MaybeStringList.Empty,
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val popupBottomMargin: Int = 0,
    val popupWidth: Int = 0,
    val popupHeight: Int = 0,
    val popupKeyHeight: Int = 0,
    val popupFont: MaybeStringList = MaybeStringList.Empty,
    val popupTextSize: Float = 0f,
    val resetAsciiModeOnFocusChange: Boolean = false,
    val roundCorner: Float = 0f,
    val shadowRadius: Float = 0f,
    val symbolFont: MaybeStringList = MaybeStringList.Empty,
    val symbolTextSize: Float = 0f,
    val textFont: MaybeStringList = MaybeStringList.Empty,
    val verticalGap: Int = 0,
    val backgroundFolder: String = "backgrounds",
    val enterLabelMode: Int = 0,
    val enterLabels: EnterLabel = EnterLabel(),
) {
    enum class CommentPosition {
        RIGHT,
        TOP,
        OVERLAY,
    }

    @Serializable
    data class EnterLabel(
        val go: String = "go",
        val done: String = "done",
        val next: String = "next",
        val pre: String = "pre",
        val search: String = "search",
        val send: String = "send",
        val default: String = "default",
    )

    companion object {
        /**
         * Every key reads on decoding, used by the theme linter to spot typos.
         * [com.osfans.trime.data.theme.ThemeDiagnosticsTest] fails when this
         * drifts from the literals below.
         */
        internal val KNOWN_KEYS: Set<String> =
            setOf(
                "auto_caps",
                "background_folder",
                "candidate_border",
                "candidate_border_round",
                "candidate_corner_radius",
                "candidate_font",
                "candidate_padding",
                "candidate_spacing",
                "candidate_text_size",
                "candidate_text_vertical_bias",
                "candidate_view_height",
                "comment_font",
                "comment_height",
                "comment_position",
                "comment_text_size",
                "comment_vertical_bias",
                "enter_label_mode",
                "enter_labels",
                "hanb_font",
                "horizontal_gap",
                "key_border",
                "key_font",
                "key_height",
                "key_hint_offset_x",
                "key_hint_offset_y",
                "key_long_text_size",
                "key_press_offset_x",
                "key_press_offset_y",
                "key_symbol_offset_x",
                "key_symbol_offset_y",
                "key_text_offset_x",
                "key_text_offset_y",
                "key_text_size",
                "key_width",
                "keyboard_height",
                "keyboard_height_land",
                "keyboard_padding",
                "keyboard_padding_bottom",
                "keyboard_padding_land",
                "keyboard_padding_land_bottom",
                "keyboard_padding_left",
                "keyboard_padding_right",
                "label_font",
                "label_text_size",
                "latin_font",
                "popup_bottom_margin",
                "popup_font",
                "popup_height",
                "popup_key_height",
                "popup_text_size",
                "popup_width",
                "reset_ascii_mode_on_focus_change",
                "round_corner",
                "shadow_radius",
                "symbol_font",
                "symbol_text_size",
                "text_font",
                "vertical_gap",
            )
    }
}
