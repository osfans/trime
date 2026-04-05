/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.enum
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
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
    val candidateTextVerticalBias: Float,
    val candidateViewHeight: Int,
    val candidateCornerRadius: Float,
    val commentFont: List<String>,
    val commentHeight: Int,
    val commentPosition: CommentPosition,
    val commentTextSize: Float,
    val commentVerticalBias: Float,
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
        RIGHT,
        TOP,
        OVERLAY,
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
            fun decode(node: Node?): EnterLabel = EnterLabel(
                go = node?.get("go")?.string ?: "go",
                done = node?.get("done")?.string ?: "done",
                next = node?.get("next")?.string ?: "next",
                pre = node?.get("pre")?.string ?: "pre",
                search = node?.get("search")?.string ?: "search",
                send = node?.get("send")?.string ?: "send",
                default = node?.get("default")?.string ?: "default",
            )
        }
    }

    companion object {
        fun decode(node: Node): GeneralStyle = GeneralStyle(
            autoCaps = node["auto_caps"]?.boolean ?: false,
            candidateBorder = node["candidate_border"]?.int ?: 0,
            candidateBorderRound = node["candidate_border_round"]?.float ?: 0f,
            candidateFont = node["candidate_font"]?.sequence?.mapNotNull { it.string } ?: emptyList(),
            candidatePadding = node["candidate_padding"]?.int ?: 0,
            candidateSpacing = node["candidate_spacing"]?.float ?: 0f,
            candidateTextSize = node["candidate_text_size"]?.float ?: 15f,
            candidateTextVerticalBias = node["candidate_text_vertical_bias"]?.float ?: 1f,
            candidateViewHeight = node["candidate_view_height"]?.int ?: 28,
            candidateCornerRadius = node["candidate_corner_radius"]?.float ?: 0f,
            commentFont = node["comment_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            commentHeight = node["comment_height"]?.int ?: 12,
            commentPosition = node["comment_position"]?.enum<CommentPosition>() ?: CommentPosition.RIGHT,
            commentTextSize = node["comment_text_size"]?.float ?: 10f,
            commentVerticalBias = node["comment_vertical_bias"]?.float ?: 0f,
            hanbFont = node["hanb_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            horizontalGap = node["horizontal_gap"]?.int ?: 0,
            keyboardPadding = node["keyboard_padding"]?.int ?: 0,
            keyboardPaddingLeft = node["keyboard_padding_left"]?.int ?: 0,
            keyboardPaddingRight = node["keyboard_padding_right"]?.int ?: 0,
            keyboardPaddingBottom = node["keyboard_padding_bottom"]?.int ?: 0,
            keyboardPaddingLand = node["keyboard_padding_land"]?.int ?: 0,
            keyboardPaddingLandBottom = node["keyboard_padding_land_bottom"]?.int ?: 0,
            keyFont = node["key_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            keyBorder = node["key_border"]?.int ?: 0,
            keyHeight = node["key_height"]?.int ?: 0,
            keyLongTextSize = node["key_long_text_size"]?.float ?: 15f,
            keyTextSize = node["key_text_size"]?.float ?: 15f,
            keyTextOffsetX = node["key_text_offset_x"]?.float ?: 0f,
            keyTextOffsetY = node["key_text_offset_y"]?.float ?: 0f,
            keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: 0f,
            keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: 0f,
            keyHintOffsetX = node["key_hint_offset_x"]?.float ?: 0f,
            keyHintOffsetY = node["key_hint_offset_y"]?.float ?: 0f,
            keyPressOffsetX = node["key_press_offset_x"]?.float ?: 0f,
            keyPressOffsetY = node["key_press_offset_y"]?.float ?: 0f,
            keyWidth = node["key_width"]?.float ?: 0f,
            labelTextSize = node["label_text_size"]?.float ?: 0f,
            labelFont = node["label_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            latinFont = node["latin_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            keyboardHeight = node["keyboard_height"]?.int ?: 0,
            keyboardHeightLand = node["keyboard_height_land"]?.int ?: 0,
            popupBottomMargin = node["popup_bottom_margin"]?.int ?: 0,
            popupWidth = node["popup_width"]?.int ?: 0,
            popupHeight = node["popup_height"]?.int ?: 0,
            popupKeyHeight = node["popup_key_height"]?.int ?: 0,
            popupFont = node["popup_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            popupTextSize = node["popup_text_size"]?.float ?: 0f,
            resetASCIIMode = node["reset_ascii_mode"]?.boolean ?: false,
            roundCorner = node["round_corner"]?.float ?: 0f,
            shadowRadius = node["shadow_radius"]?.float ?: 0f,
            symbolFont = node["symbol_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            symbolTextSize = node["symbol_text_size"]?.float ?: 0f,
            textFont = node["text_font"]?.sequence
                ?.mapNotNull(Node::string) ?: emptyList(),
            verticalGap = node["vertical_gap"]?.int ?: 0,
            backgroundFolder = node["background_folder"]?.string ?: "backgrounds",
            enterLabelMode = node["enter_label_mode"]?.int ?: 0,
            enterLabel = EnterLabel.decode(node["enter_labels"]),
        )
    }
}
