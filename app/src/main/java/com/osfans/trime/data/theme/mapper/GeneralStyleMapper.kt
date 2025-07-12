// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.charleskorn.kaml.YamlMap
import com.osfans.trime.data.theme.ThemeFilesManager
import com.osfans.trime.data.theme.model.GeneralStyle

class GeneralStyleMapper(
    node: YamlMap,
) : Mapper<GeneralStyle>(node) {
    override fun map() =
        GeneralStyle(
            autoCaps = getBoolean("auto_caps"),
            candidateBorder = getInt("candidate_border"),
            candidateBorderRound = getFloat("candidate_border_round"),
            candidateFont = getStringList("candidate_font"),
            candidatePadding = getInt("candidate_padding"),
            candidateSpacing = getFloat("candidate_spacing"),
            candidateTextSize = getFloat("candidate_text_size"),
            candidateUseCursor = getBoolean("candidate_use_cursor"),
            candidateViewHeight = getInt("candidate_view_height"),
            commentFont = getStringList("comment_font"),
            commentHeight = getInt("comment_height"),
            commentOnTop = getBoolean("comment_on_top"),
            commentPosition = getEnum("comment_position", GeneralStyle.CommentPosition.UNKNOWN),
            commentTextSize = getFloat("comment_text_size"),
            hanbFont = getStringList("hanb_font"),
            horizontal = getBoolean("horizontal"),
            horizontalGap = getInt("horizontal_gap"),
            keyboardPadding = getInt("keyboard_padding"),
            keyboardPaddingLeft = getInt("keyboard_padding_left"),
            keyboardPaddingRight = getInt("keyboard_padding_right"),
            keyboardPaddingBottom = getInt("keyboard_padding_bottom"),
            keyboardPaddingLand = getInt("keyboard_padding_land"),
            keyboardPaddingLandBottom = getInt("keyboard_padding_land_bottom"),
            layout =
                when (val map = node.get<YamlMap>("layout")) {
                    null -> GeneralStyle.Layout()
                    else -> ThemeFilesManager.yaml.decodeFromYamlNode(map)
                },
            keyFont = getStringList("key_font"),
            keyBorder = getInt("key_border"),
            keyHeight = getInt("key_height"),
            keyLongTextSize = getFloat("key_long_text_size"),
            keyTextSize = getFloat("key_text_size"),
            keyTextOffsetX = getFloat("key_text_offset_x"),
            keyTextOffsetY = getFloat("key_text_offset_y"),
            keySymbolOffsetX = getFloat("key_symbol_offset_x"),
            keySymbolOffsetY = getFloat("key_symbol_offset_y"),
            keyHintOffsetX = getFloat("key_hint_offset_x"),
            keyHintOffsetY = getFloat("key_hint_offset_y"),
            keyPressOffsetX = getInt("key_press_offset_x"),
            keyPressOffsetY = getInt("key_press_offset_y"),
            keyWidth = getFloat("key_width"),
            labelTextSize = getFloat("label_text_size"),
            labelFont = getStringList("label_font"),
            latinFont = getStringList("latin_font"),
            keyboardHeight = getInt("keyboard_height"),
            keyboardHeightLand = getInt("keyboard_height_land"),
            previewFont = getStringList("preview_font"),
            previewHeight = getInt("preview_height"),
            previewOffset = getInt("preview_offset"),
            previewTextSize = getFloat("preview_text_size"),
            proximityCorrection = getBoolean("proximity_correction"),
            resetASCIIMode = getBoolean("reset_ascii_mode"),
            roundCorner = getFloat("round_corner"),
            shadowRadius = getFloat("shadow_radius"),
            speechOpenccConfig = getString("speech_opencc_config"),
            symbolFont = getStringList("symbol_font"),
            symbolTextSize = getFloat("symbol_text_size"),
            textFont = getStringList("text_font"),
            textSize = getFloat("text_size"),
            verticalCorrection = getInt("vertical_correction"),
            verticalGap = getInt("vertical_gap"),
            longTextFont = getStringList("long_text_font"),
            backgroundFolder = getString("background_folder"),
            keyLongTextBorder = getInt("key_long_text_border"),
            enterLabelMode = getInt("enter_label_mode"),
            enterLabel =
                when (val map = node.get<YamlMap>("enter_labels")) {
                    null -> GeneralStyle.EnterLabel()
                    else -> ThemeFilesManager.yaml.decodeFromYamlNode(map)
                },
        )
}
