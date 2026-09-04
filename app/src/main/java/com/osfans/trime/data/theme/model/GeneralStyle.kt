/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
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

/**
 * Decoded `style` section of a theme. The constructor defaults are the single
 * source of truth for theme defaults; [decode] fills fields from YAML and
 * falls back to [DEFAULTS] for absent keys.
 */
@Parcelize
data class GeneralStyle(
    val autoCaps: Boolean = false,
    val candidateBorder: Int = 0,
    val candidateBorderRound: Float = 0f,
    val candidateFont: List<String> = emptyList(),
    val candidatePadding: Int = 0,
    val candidateSpacing: Float = 0f,
    val candidateTextSize: Float = 15f,
    val candidateTextVerticalBias: Float = 1f,
    val candidateViewHeight: Int = 28,
    val candidateCornerRadius: Float = 5f,
    val commentFont: List<String> = emptyList(),
    val commentHeight: Int = 12,
    val commentPosition: CommentPosition = CommentPosition.RIGHT,
    val commentTextSize: Float = 10f,
    val commentVerticalBias: Float = 0f,
    val hanbFont: List<String> = emptyList(),
    val horizontalGap: Int = 0,
    val keyboardPadding: Int = 0,
    val keyboardPaddingLeft: Int = 0,
    val keyboardPaddingRight: Int = 0,
    val keyboardPaddingBottom: Int = 0,
    val keyboardPaddingLand: Int = 0,
    val keyboardPaddingLandBottom: Int = 0,
    val keyFont: List<String> = emptyList(),
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
    val labelFont: List<String> = emptyList(),
    val latinFont: List<String> = emptyList(),
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val popupBottomMargin: Int = 0,
    val popupWidth: Int = 0,
    val popupHeight: Int = 0,
    val popupKeyHeight: Int = 0,
    val popupFont: List<String> = emptyList(),
    val popupTextSize: Float = 0f,
    val resetAsciiModeOnFocusChange: Boolean = false,
    val roundCorner: Float = 0f,
    val shadowRadius: Float = 0f,
    val symbolFont: List<String> = emptyList(),
    val symbolTextSize: Float = 0f,
    val textFont: List<String> = emptyList(),
    val verticalGap: Int = 0,
    val backgroundFolder: String = "backgrounds",
    val enterLabelMode: Int = 0,
    val enterLabel: EnterLabel = EnterLabel(),
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
        /** The default style; decode falls back to these values key by key. */
        val DEFAULTS = GeneralStyle()

        private fun Node?.stringList(): List<String> = this?.sequence?.mapNotNull(Node::string) ?: emptyList()

        fun decode(node: Node): GeneralStyle = GeneralStyle(
            autoCaps = node["auto_caps"]?.boolean ?: DEFAULTS.autoCaps,
            candidateBorder = node["candidate_border"]?.int ?: DEFAULTS.candidateBorder,
            candidateBorderRound = node["candidate_border_round"]?.float ?: DEFAULTS.candidateBorderRound,
            candidateFont = node["candidate_font"].stringList(),
            candidatePadding = node["candidate_padding"]?.int ?: DEFAULTS.candidatePadding,
            candidateSpacing = node["candidate_spacing"]?.float ?: DEFAULTS.candidateSpacing,
            candidateTextSize = node["candidate_text_size"]?.float ?: DEFAULTS.candidateTextSize,
            candidateTextVerticalBias = node["candidate_text_vertical_bias"]?.float
                ?: DEFAULTS.candidateTextVerticalBias,
            candidateViewHeight = node["candidate_view_height"]?.int ?: DEFAULTS.candidateViewHeight,
            candidateCornerRadius = node["candidate_corner_radius"]?.float ?: DEFAULTS.candidateCornerRadius,
            commentFont = node["comment_font"].stringList(),
            commentHeight = node["comment_height"]?.int ?: DEFAULTS.commentHeight,
            commentPosition = node["comment_position"]?.enum<CommentPosition>() ?: DEFAULTS.commentPosition,
            commentTextSize = node["comment_text_size"]?.float ?: DEFAULTS.commentTextSize,
            commentVerticalBias = node["comment_vertical_bias"]?.float ?: DEFAULTS.commentVerticalBias,
            hanbFont = node["hanb_font"].stringList(),
            horizontalGap = node["horizontal_gap"]?.int ?: DEFAULTS.horizontalGap,
            keyboardPadding = node["keyboard_padding"]?.int ?: DEFAULTS.keyboardPadding,
            keyboardPaddingLeft = node["keyboard_padding_left"]?.int ?: DEFAULTS.keyboardPaddingLeft,
            keyboardPaddingRight = node["keyboard_padding_right"]?.int ?: DEFAULTS.keyboardPaddingRight,
            keyboardPaddingBottom = node["keyboard_padding_bottom"]?.int ?: DEFAULTS.keyboardPaddingBottom,
            keyboardPaddingLand = node["keyboard_padding_land"]?.int ?: DEFAULTS.keyboardPaddingLand,
            keyboardPaddingLandBottom = node["keyboard_padding_land_bottom"]?.int
                ?: DEFAULTS.keyboardPaddingLandBottom,
            keyFont = node["key_font"].stringList(),
            keyBorder = node["key_border"]?.int ?: DEFAULTS.keyBorder,
            keyHeight = node["key_height"]?.int ?: DEFAULTS.keyHeight,
            keyLongTextSize = node["key_long_text_size"]?.float ?: DEFAULTS.keyLongTextSize,
            keyTextSize = node["key_text_size"]?.float ?: DEFAULTS.keyTextSize,
            keyTextOffsetX = node["key_text_offset_x"]?.float ?: DEFAULTS.keyTextOffsetX,
            keyTextOffsetY = node["key_text_offset_y"]?.float ?: DEFAULTS.keyTextOffsetY,
            keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: DEFAULTS.keySymbolOffsetX,
            keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: DEFAULTS.keySymbolOffsetY,
            keyHintOffsetX = node["key_hint_offset_x"]?.float ?: DEFAULTS.keyHintOffsetX,
            keyHintOffsetY = node["key_hint_offset_y"]?.float ?: DEFAULTS.keyHintOffsetY,
            keyPressOffsetX = node["key_press_offset_x"]?.float ?: DEFAULTS.keyPressOffsetX,
            keyPressOffsetY = node["key_press_offset_y"]?.float ?: DEFAULTS.keyPressOffsetY,
            keyWidth = node["key_width"]?.float ?: DEFAULTS.keyWidth,
            labelTextSize = node["label_text_size"]?.float ?: DEFAULTS.labelTextSize,
            labelFont = node["label_font"].stringList(),
            latinFont = node["latin_font"].stringList(),
            keyboardHeight = node["keyboard_height"]?.int ?: DEFAULTS.keyboardHeight,
            keyboardHeightLand = node["keyboard_height_land"]?.int ?: DEFAULTS.keyboardHeightLand,
            popupBottomMargin = node["popup_bottom_margin"]?.int ?: DEFAULTS.popupBottomMargin,
            popupWidth = node["popup_width"]?.int ?: DEFAULTS.popupWidth,
            popupHeight = node["popup_height"]?.int ?: DEFAULTS.popupHeight,
            popupKeyHeight = node["popup_key_height"]?.int ?: DEFAULTS.popupKeyHeight,
            popupFont = node["popup_font"].stringList(),
            popupTextSize = node["popup_text_size"]?.float ?: DEFAULTS.popupTextSize,
            resetAsciiModeOnFocusChange = node["reset_ascii_mode_on_focus_change"]?.boolean
                ?: DEFAULTS.resetAsciiModeOnFocusChange,
            roundCorner = node["round_corner"]?.float ?: DEFAULTS.roundCorner,
            shadowRadius = node["shadow_radius"]?.float ?: DEFAULTS.shadowRadius,
            symbolFont = node["symbol_font"].stringList(),
            symbolTextSize = node["symbol_text_size"]?.float ?: DEFAULTS.symbolTextSize,
            textFont = node["text_font"].stringList(),
            verticalGap = node["vertical_gap"]?.int ?: DEFAULTS.verticalGap,
            backgroundFolder = node["background_folder"]?.string ?: DEFAULTS.backgroundFolder,
            enterLabelMode = node["enter_label_mode"]?.int ?: DEFAULTS.enterLabelMode,
            enterLabel = EnterLabel.decode(node["enter_labels"]),
        )
    }
}
