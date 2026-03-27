/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.osfans.trime.util.getBool
import com.osfans.trime.util.getEnum
import com.osfans.trime.util.getFloat
import com.osfans.trime.util.getInt
import com.osfans.trime.util.getString
import com.osfans.trime.util.getStringList
import kotlinx.parcelize.Parcelize

@Parcelize
data class GeneralStyle(
    val autoCaps: Boolean,
    val candidateBorder: Int,
    val candidateBorderRound: Float,
    val candidateFont: List<String>,
    val candidatePadding: Int,
    val candidateSpacing: Float,
    val candidateTextSize: Float,
    val candidateViewHeight: Int,
    val candidateCornerRadius: Float,
    val commentFont: List<String>,
    val commentHeight: Int,
    val commentOnTop: Boolean,
    val commentPosition: CommentPosition,
    val commentTextSize: Float,
    val hanbFont: List<String>,
    val horizontalGap: Int,
    val keyboardPadding: Int,
    val keyboardPaddingLeft: Int,
    val keyboardPaddingRight: Int,
    val keyboardPaddingBottom: Int,
    val keyboardPaddingLand: Int,
    val keyboardPaddingLandBottom: Int,
    val keyFont: List<String>,
    val keyBorder: Int,
    val keyHeight: Int,
    val keyLongTextSize: Float,
    val keyTextSize: Float,
    val keyTextOffsetX: Float,
    val keyTextOffsetY: Float,
    val keySymbolOffsetX: Float,
    val keySymbolOffsetY: Float,
    val keyHintOffsetX: Float,
    val keyHintOffsetY: Float,
    val keyPressOffsetX: Float,
    val keyPressOffsetY: Float,
    val keyWidth: Float,
    val labelTextSize: Float,
    val labelFont: List<String>,
    val latinFont: List<String>,
    val keyboardHeight: Int,
    val keyboardHeightLand: Int,
    val popupBottomMargin: Int,
    val popupWidth: Int,
    val popupHeight: Int,
    val popupKeyHeight: Int,
    val popupFont: List<String>,
    val popupTextSize: Float,
    val resetASCIIMode: Boolean,
    val roundCorner: Float,
    val shadowRadius: Float,
    val symbolFont: List<String>,
    val symbolTextSize: Float,
    val textFont: List<String>,
    val verticalGap: Int,
    val backgroundFolder: String,
    val enterLabelMode: Int,
    val enterLabel: EnterLabel,
) : Parcelable {
    enum class CommentPosition {
        UNKNOWN,
        TOP,
        BOTTOM,
        RIGHT,
    }

    @Parcelize
    data class EnterLabel(
        val go: String = "go",
        val done: String = "done",
        val next: String = "next",
        val pre: String = "pre",
        val search: String = "search",
        val send: String = "send",
        val default: String = "default",
    ) : Parcelable {
        companion object {
            fun decode(node: YamlMap?): EnterLabel = EnterLabel(
                go = node.getString("go", "go"),
                done = node.getString("done", "done"),
                next = node.getString("next", "next"),
                pre = node.getString("pre", "pre"),
                search = node.getString("search", "search"),
                send = node.getString("send", "send"),
                default = node.getString("default", "default"),
            )
        }
    }

    companion object {
        fun decode(node: YamlMap): GeneralStyle = GeneralStyle(
            autoCaps = node.getBool("auto_caps"),
            candidateBorder = node.getInt("candidate_border"),
            candidateBorderRound = node.getFloat("candidate_border_round"),
            candidateFont = node.getStringList("candidate_font") ?: emptyList(),
            candidatePadding = node.getInt("candidate_padding"),
            candidateSpacing = node.getFloat("candidate_spacing"),
            candidateTextSize = node.getFloat("candidate_text_size", 15f),
            candidateViewHeight = node.getInt("candidate_view_height", 28),
            candidateCornerRadius = node.getFloat("candidate_corner_radius"),
            commentFont = node.getStringList("comment_font") ?: emptyList(),
            commentHeight = node.getInt("comment_height", 12),
            commentOnTop = node.getBool("comment_on_top"),
            commentPosition = node.getEnum<CommentPosition>("comment_position", CommentPosition.UNKNOWN),
            commentTextSize = node.getFloat("comment_text_size", 15f),
            hanbFont = node.getStringList("hanb_font") ?: emptyList(),
            horizontalGap = node.getInt("horizontal_gap"),
            keyboardPadding = node.getInt("keyboard_padding"),
            keyboardPaddingLeft = node.getInt("keyboard_padding_left"),
            keyboardPaddingRight = node.getInt("keyboard_padding_right"),
            keyboardPaddingBottom = node.getInt("keyboard_padding_bottom"),
            keyboardPaddingLand = node.getInt("keyboard_padding_land"),
            keyboardPaddingLandBottom = node.getInt("keyboard_padding_land_bottom"),
            keyFont = node.getStringList("key_font") ?: emptyList(),
            keyBorder = node.getInt("key_border"),
            keyHeight = node.getInt("key_height"),
            keyLongTextSize = node.getFloat("key_long_text_size", 15f),
            keyTextSize = node.getFloat("key_text_size", 15f),
            keyTextOffsetX = node.getFloat("key_text_offset_x"),
            keyTextOffsetY = node.getFloat("key_text_offset_y"),
            keySymbolOffsetX = node.getFloat("key_symbol_offset_x"),
            keySymbolOffsetY = node.getFloat("key_symbol_offset_y"),
            keyHintOffsetX = node.getFloat("key_hint_offset_x"),
            keyHintOffsetY = node.getFloat("key_hint_offset_y"),
            keyPressOffsetX = node.getFloat("key_press_offset_x"),
            keyPressOffsetY = node.getFloat("key_press_offset_y"),
            keyWidth = node.getFloat("key_width"),
            labelTextSize = node.getFloat("label_text_size"),
            labelFont = node.getStringList("label_font") ?: emptyList(),
            latinFont = node.getStringList("latin_font") ?: emptyList(),
            keyboardHeight = node.getInt("keyboard_height"),
            keyboardHeightLand = node.getInt("keyboard_height_land"),
            popupBottomMargin = node.getInt("popup_bottom_margin"),
            popupWidth = node.getInt("popup_width"),
            popupHeight = node.getInt("popup_height"),
            popupKeyHeight = node.getInt("popup_key_height"),
            popupFont = node.getStringList("popup_font") ?: emptyList(),
            popupTextSize = node.getFloat("popup_text_size"),
            resetASCIIMode = node.getBool("reset_ascii_mode"),
            roundCorner = node.getFloat("round_corner"),
            shadowRadius = node.getFloat("shadow_radius"),
            symbolFont = node.getStringList("symbol_font") ?: emptyList(),
            symbolTextSize = node.getFloat("symbol_text_size"),
            textFont = node.getStringList("text_font") ?: emptyList(),
            verticalGap = node.getInt("vertical_gap"),
            backgroundFolder = node.getString("background_folder", "backgrounds"),
            enterLabelMode = node.getInt("enter_label_mode"),
            enterLabel = EnterLabel.decode(node.get<YamlMap>("enter_labels")),
        )
    }
}
