/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.getBool
import com.osfans.trime.util.getEnum
import com.osfans.trime.util.getFloat
import com.osfans.trime.util.getInt
import com.osfans.trime.util.getList
import com.osfans.trime.util.getString
import com.osfans.trime.util.getStringList
import kotlinx.parcelize.Parcelize

@Parcelize
data class TextKeyboard(
    val name: String,
    val author: String,
    val width: Float,
    val height: Float,
    val keyboardHeight: Int,
    val keyboardHeightLand: Int,
    val autoHeightIndex: Int,
    val horizontalGap: Int,
    val verticalGap: Int,
    val roundCorner: Float,
    val keyBorder: Int,
    val columns: Int,
    val asciiMode: Boolean,
    val resetAsciiMode: Boolean,
    val labelTransform: LabelTransform,
    val lock: Boolean,
    val asciiKeyboard: String,
    val landscapeKeyboard: String,
    val landscapeSplitPercent: Int,
    val keyTextOffsetX: Float,
    val keyTextOffsetY: Float,
    val keySymbolOffsetX: Float,
    val keySymbolOffsetY: Float,
    val keyHintOffsetX: Float,
    val keyHintOffsetY: Float,
    val keyPressOffsetX: Float,
    val keyPressOffsetY: Float,
    val importPreset: String,
    val keys: List<TextKey>,
) : Parcelable {
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    @Parcelize
    data class TextKey(
        val width: Float,
        val height: Float,
        val roundCorner: Float,
        val keyBorder: Int,
        val label: String,
        val labelSymbol: String,
        val hint: String,
        val click: String,
        val sendBindings: Boolean,
        val keyTextSize: Float,
        val symbolTextSize: Float,
        val keyTextOffsetX: Float,
        val keyTextOffsetY: Float,
        val keySymbolOffsetX: Float,
        val keySymbolOffsetY: Float,
        val keyHintOffsetX: Float,
        val keyHintOffsetY: Float,
        val keyPressOffsetX: Float,
        val keyPressOffsetY: Float,
        val keyTextColor: String,
        val keyBackColor: String,
        val keySymbolColor: String,
        val hlKeyTextColor: String,
        val hlKeyBackColor: String,
        val hlKeySymbolColor: String,
        val popup: List<String> = emptyList(),
        val behaviors: Map<KeyBehavior, String>,
    ) : Parcelable {
        companion object {
            fun decode(node: YamlMap): TextKey = TextKey(
                width = node.getFloat("width"),
                height = node.getFloat("height"),
                roundCorner = node.getFloat("round_corner", -1f),
                keyBorder = node.getInt("key_border", -1),
                label = node.getString("label"),
                labelSymbol = node.getString("label_symbol"),
                hint = node.getString("hint"),
                click = node.getString("click"),
                sendBindings = node.getBool("send_bindings", true),
                keyTextSize = node.getFloat("key_text_size"),
                symbolTextSize = node.getFloat("symbol_text_size"),
                keyTextOffsetX = node.getFloat("key_text_offset_x"),
                keyTextOffsetY = node.getFloat("key_text_offset_y"),
                keySymbolOffsetX = node.getFloat("key_symbol_offset_x"),
                keySymbolOffsetY = node.getFloat("key_symbol_offset_y"),
                keyHintOffsetX = node.getFloat("key_hint_offset_x"),
                keyHintOffsetY = node.getFloat("key_hint_offset_y"),
                keyPressOffsetX = node.getFloat("key_press_offset_x"),
                keyPressOffsetY = node.getFloat("key_press_offset_y"),
                keyTextColor = node.getString("key_text_color"),
                keyBackColor = node.getString("key_back_color"),
                keySymbolColor = node.getString("key_symbol_color"),
                hlKeyTextColor = node.getString("hilited_key_text_color"),
                hlKeyBackColor = node.getString("hilited_key_back_color"),
                hlKeySymbolColor = node.getString("hilited_key_symbol_color"),
                popup = node.getStringList("popup") ?: emptyList(),
                behaviors =
                buildMap {
                    KeyBehavior.entries.forEach { entry ->
                        val action = node.getString(entry.name.lowercase())
                        if (action.isNotEmpty() || entry == KeyBehavior.CLICK) {
                            put(entry, action)
                        }
                    }
                },
            )
        }
    }

    companion object {
        fun decode(node: YamlMap): TextKeyboard = TextKeyboard(
            name = node.getString("name"),
            author = node.getString("author"),
            width = node.getFloat("width"),
            height = node.getFloat("height"),
            keyboardHeight = node.getInt("keyboard_height"),
            keyboardHeightLand = node.getInt("keyboard_height_land"),
            autoHeightIndex = node.getInt("auto_height_index", -1),
            horizontalGap = node.getInt("horizontal_gap"),
            verticalGap = node.getInt("vertical_gap"),
            roundCorner = node.getFloat("round_corner", -1f),
            keyBorder = node.getInt("key_border", -1),
            columns = node.getInt("columns", 30),
            asciiMode = node.getInt("ascii_mode", 1) == 1,
            resetAsciiMode = node.getBool("reset_ascii_mode", true),
            labelTransform = node.getEnum("label_transform", LabelTransform.NONE),
            lock = node.getBool("lock"),
            asciiKeyboard = node.getString("ascii_keyboard"),
            landscapeKeyboard = node.getString("landscape_keyboard"),
            landscapeSplitPercent = node.getInt("landscape_split_percent"),
            keyTextOffsetX = node.getFloat("key_text_offset_x"),
            keyTextOffsetY = node.getFloat("key_text_offset_y"),
            keySymbolOffsetX = node.getFloat("key_symbol_offset_x"),
            keySymbolOffsetY = node.getFloat("key_symbol_offset_y"),
            keyHintOffsetX = node.getFloat("key_hint_offset_x"),
            keyHintOffsetY = node.getFloat("key_hint_offset_y"),
            keyPressOffsetX = node.getFloat("key_press_offset_x"),
            keyPressOffsetY = node.getFloat("key_press_offset_y"),
            importPreset = node.getString("import_preset"),
            keys = node.getList("keys") {
                TextKey.decode(it.yamlMap)
            } ?: emptyList(),
        )
    }
}
