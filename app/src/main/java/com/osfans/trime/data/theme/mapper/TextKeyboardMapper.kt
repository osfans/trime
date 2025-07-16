/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.mapper

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.getBool
import com.osfans.trime.util.getFloat
import com.osfans.trime.util.getInt
import com.osfans.trime.util.getString
import timber.log.Timber

class TextKeyboardMapper(
    node: YamlMap,
) : Mapper<TextKeyboard>(node) {
    override fun map(): TextKeyboard {
        val keys =
            try {
                getList("keys")?.map { node ->
                    val it = node.yamlMap
                    TextKeyboard.TextKey(
                        width = it.getFloat("width"),
                        height = it.getFloat("height"),
                        roundCorner = it.getFloat("round_corner"),
                        label = it.getString("label"),
                        labelSymbol = it.getString("label_symbol"),
                        hint = it.getString("hint"),
                        click = it.getString("click"),
                        sendBindings = it.getBool("send_bindings", true),
                        keyTextSize = it.getFloat("key_text_size"),
                        symbolTextSize = it.getFloat("symbol_text_size"),
                        keyTextOffsetX = it.getFloat("key_text_offset_x"),
                        keyTextOffsetY = it.getFloat("key_text_offset_y"),
                        keySymbolOffsetX = it.getFloat("key_symbol_offset_x"),
                        keySymbolOffsetY = it.getFloat("key_symbol_offset_y"),
                        keyHintOffsetX = it.getFloat("key_hint_offset_x"),
                        keyHintOffsetY = it.getFloat("key_hint_offset_y"),
                        keyPressOffsetX = it.getInt("key_press_offset_x"),
                        keyPressOffsetY = it.getInt("key_press_offset_y"),
                        keyTextColor = it.getString("key_text_color"),
                        keyBackColor = it.getString("key_back_color"),
                        keySymbolColor = it.getString("key_symbol_color"),
                        hlKeyTextColor = it.getString("hilited_key_text_color"),
                        hlKeyBackColor = it.getString("hilited_key_back_color"),
                        hlKeySymbolColor = it.getString("hilited_key_symbol_color"),
                        behaviors =
                            buildMap {
                                KeyBehavior.entries.forEach { entry ->
                                    val action = it.getString(entry.name.lowercase())
                                    if (action.isNotEmpty() || entry == KeyBehavior.CLICK) {
                                        put(entry, action)
                                    }
                                }
                            },
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode TextKeyboard property 'keys'")
                listOf()
            }
        return TextKeyboard(
            name = getString("name"),
            author = getString("author"),
            width = getFloat("width"),
            height = getFloat("height"),
            keyboardHeight = getInt("keyboard_height"),
            keyboardHeightLand = getInt("keyboard_height_land"),
            autoHeightIndex = getInt("auto_height_index", -1),
            horizontalGap = getInt("horizontal_gap"),
            verticalGap = getInt("vertical_gap"),
            roundCorner = getFloat("round_corner"),
            columns = getInt("columns", 30),
            asciiMode = getInt("ascii_mode", 1) == 1,
            resetAsciiMode = getBoolean("reset_ascii_mode", true),
            labelTransform = getEnum("label_transform", TextKeyboard.LabelTransform.NONE),
            lock = getBoolean("lock"),
            asciiKeyboard = getString("ascii_keyboard"),
            landscapeKeyboard = getString("landscape_keyboard"),
            landscapeSplitPercent = getInt("landscape_split_percent"),
            keyTextOffsetX = getFloat("key_text_offset_x"),
            keyTextOffsetY = getFloat("key_text_offset_y"),
            keySymbolOffsetX = getFloat("key_symbol_offset_x"),
            keySymbolOffsetY = getFloat("key_symbol_offset_y"),
            keyHintOffsetX = getFloat("key_hint_offset_x"),
            keyHintOffsetY = getFloat("key_hint_offset_y"),
            keyPressOffsetX = getInt("key_press_offset_x"),
            keyPressOffsetY = getInt("key_press_offset_y"),
            importPreset = getString("import_preset"),
            keys = keys,
        )
    }
}
