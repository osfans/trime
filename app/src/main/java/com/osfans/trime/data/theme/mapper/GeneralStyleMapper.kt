// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.symbol.VarLengthAdapter.SecondTextPosition
import com.osfans.trime.util.config.ConfigItem

class GeneralStyleMapper(
    style: Map<String, ConfigItem?>?,
) : Mapper(style) {
    fun map(): GeneralStyle {
        val autoCaps = getString("auto_caps")

        val backgroundDimAmount = getFloat("background_dim_amount")

        val candidateBorder = getInt("candidate_border")
        val candidateBorderRound = getInt("candidate_border_round")

        val candidateFont = getStringList("candidate_font")

        val candidatePadding = getInt("candidate_padding")

        val candidateSpacing = getFloat("candidate_spacing")

        val candidateTextSize = getInt("candidate_text_size")

        val candidateUseCursor = getBoolean("candidate_use_cursor")

        val candidateViewHeight = getInt("candidate_view_height")

        val colorScheme = getString("color_scheme")

        val commentFont = getStringList("comment_font")

        val commentHeight = getInt("comment_height")

        val commentOnTop = getBoolean("comment_on_top")
        val secondTextPosition =
            runCatching {
                val s = getString("comment_position")
                SecondTextPosition.valueOf(s.uppercase())
            }.getOrElse {
                SecondTextPosition.UNKNOWN
            }

        val commentTextSize = getInt("comment_text_size")

        val hanbFont = getStringList("hanb_font")

        val horizontal = getBoolean("horizontal")

        val horizontalGap = getInt("horizontal_gap")

        val keyboardPadding = getInt("keyboard_padding")

        val keyboardPaddingLeft = getInt("keyboard_padding_left")

        val keyboardPaddingRight = getInt("keyboard_padding_right")

        val keyboardPaddingBottom = getInt("keyboard_padding_bottom")

        val keyboardPaddingLand = getInt("keyboard_padding_land")

        val keyboardPaddingLandBottom = getInt("keyboard_padding_land_bottom")

        val layout =
            getObject("layout").let {
                LayoutStyleMapper(it).map()
            }

        val window =
            (getList("window")).map {
                val compositionWindowStyleMapper = CompositionWindowStyleMapper(it.configMap)
                compositionWindowStyleMapper.map()
            }
        val liquidKeyboardWindow =
            (getList("liquid_keyboard_window")).map {
                val mapper = CompositionWindowStyleMapper(it.configMap)
                mapper.map()
            }
        val keyFont = getStringList("key_font")
        val keyBorder = getInt("key_border")

        val keyHeight = getInt("key_height")

        val keyLongTextSize = getInt("key_long_text_size")

        val keyTextSize = getInt("key_text_size")

        val keyTextOffsetX = getInt("key_text_offset_x")
        val keyTextOffsetY = getInt("key_text_offset_y")
        val keySymbolOffsetX = getInt("key_symbol_offset_x")
        val keySymbolOffsetY = getInt("key_symbol_offset_y")
        val keyHintOffsetX = getInt("key_hint_offset_x")
        val keyHintOffsetY = getInt("key_hint_offset_y")
        val keyPressOffsetX = getInt("key_press_offset_x")
        val keyPressOffsetY = getInt("key_press_offset_y")

        val keyWidth = getFloat("key_width")

        val keyboards = getStringList("keyboards")

        val labelTextSize = getInt("label_text_size")

        val labelFont = getStringList("label_font")

        val latinFont = getStringList("latin_font")

        val latinLocale = getString("latin_locale")

        val locale = getString("locale")

        val keyboardHeight = getInt("keyboard_height")

        val keyboardHeightLand = getInt("keyboard_height_land")

        val previewFont = getStringList("preview_font")

        val previewHeight = getInt("preview_height")

        val previewOffset = getInt("preview_offset")

        val previewTextSize = getInt("preview_text_size")

        val proximityCorrection = getBoolean("proximity_correction")

        val resetASCIIMode = getBoolean("reset_ascii_mode")

        val roundCorner = getInt("round_corner")

        val shadowRadius = getFloat("shadow_radius")

        val speechOpenccConfig = getString("speech_opencc_config")

        val symbolFont = getStringList("symbol_font")

        val symbolTextSize = getInt("symbol_text_size")

        val textFont = getStringList("text_font")

        val textSize = getInt("text_size")

        val verticalCorrection = getInt("vertical_correction")

        val verticalGap = getInt("vertical_gap")

        val longTextFont = getStringList("long_text_font")

        val backgroundFolder = getString("background_folder")

        val keyIntTextBorder = getInt("key_long_text_border")

        val enterLabelMode = getInt("enter_label_mode")

        val enterLabels = EnterLabelStyleMapper((getObject("enter_labels")?.configMap)).map()

        return GeneralStyle(
            autoCaps,
            backgroundDimAmount,
            candidateBorder,
            candidateBorderRound,
            candidateFont,
            candidatePadding,
            candidateSpacing,
            candidateTextSize,
            candidateUseCursor,
            candidateViewHeight,
            colorScheme,
            commentFont,
            commentHeight,
            commentOnTop,
            secondTextPosition,
            commentTextSize,
            hanbFont,
            horizontal,
            horizontalGap,
            keyboardPadding,
            keyboardPaddingLeft,
            keyboardPaddingRight,
            keyboardPaddingBottom,
            keyboardPaddingLand,
            keyboardPaddingLandBottom,
            layout,
            window,
            liquidKeyboardWindow,
            keyFont,
            keyBorder,
            keyHeight,
            keyLongTextSize,
            keyTextSize,
            keyTextOffsetX,
            keyTextOffsetY,
            keySymbolOffsetX,
            keySymbolOffsetY,
            keyHintOffsetX,
            keyHintOffsetY,
            keyPressOffsetX,
            keyPressOffsetY,
            keyWidth,
            keyboards,
            labelTextSize,
            labelFont,
            latinFont,
            latinLocale,
            locale,
            keyboardHeight,
            keyboardHeightLand,
            previewFont,
            previewHeight,
            previewOffset,
            previewTextSize,
            proximityCorrection,
            resetASCIIMode,
            roundCorner,
            shadowRadius,
            speechOpenccConfig,
            symbolFont,
            symbolTextSize,
            textFont,
            textSize,
            verticalCorrection,
            verticalGap,
            longTextFont,
            backgroundFolder,
            keyIntTextBorder,
            enterLabelMode,
            enterLabels,
        )
    }
}
