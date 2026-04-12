/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.enum
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
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
    val resetAsciiMode: Boolean?,
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
            fun decode(node: Node.Mapping): TextKey = TextKey(
                width = node["width"]?.float ?: 0f,
                height = node["height"]?.float ?: 0f,
                roundCorner = node["round_corner"]?.float ?: -1f,
                keyBorder = node["key_border"]?.int ?: -1,
                label = node["label"]?.string ?: "",
                labelSymbol = node["label_symbol"]?.string ?: "",
                hint = node["hint"]?.string ?: "",
                click = node["click"]?.string ?: "",
                sendBindings = node["send_bindings"]?.boolean ?: true,
                keyTextSize = node["key_text_size"]?.float ?: 0f,
                symbolTextSize = node["symbol_text_size"]?.float ?: 0f,
                keyTextOffsetX = node["key_text_offset_x"]?.float ?: 0f,
                keyTextOffsetY = node["key_text_offset_y"]?.float ?: 0f,
                keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: 0f,
                keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: 0f,
                keyHintOffsetX = node["key_hint_offset_x"]?.float ?: 0f,
                keyHintOffsetY = node["key_hint_offset_y"]?.float ?: 0f,
                keyPressOffsetX = node["key_press_offset_x"]?.float ?: 0f,
                keyPressOffsetY = node["key_press_offset_y"]?.float ?: 0f,
                keyTextColor = node["key_text_color"]?.string ?: "",
                keyBackColor = node["key_back_color"]?.string ?: "",
                keySymbolColor = node["key_symbol_color"]?.string ?: "",
                hlKeyTextColor = node["hilited_key_text_color"]?.string ?: "",
                hlKeyBackColor = node["hilited_key_back_color"]?.string ?: "",
                hlKeySymbolColor = node["hilited_key_symbol_color"]?.string ?: "",
                popup = node["popup"]?.sequence?.mapNotNull(Node::string) ?: emptyList(),
                behaviors =
                buildMap {
                    KeyBehavior.entries.forEach { entry ->
                        val action = node[entry.name.lowercase()]?.string ?: ""
                        if (action.isNotEmpty() || entry == KeyBehavior.CLICK) {
                            put(entry, action)
                        }
                    }
                },
            )
        }
    }

    companion object {
        fun decode(node: Node.Mapping): TextKeyboard = TextKeyboard(
            name = node["name"]?.string ?: "",
            author = node["author"]?.string ?: "",
            width = node["width"]?.float ?: 0f,
            height = node["height"]?.float ?: 0f,
            keyboardHeight = node["keyboard_height"]?.int ?: 0,
            keyboardHeightLand = node["keyboard_height_land"]?.int ?: 0,
            autoHeightIndex = node["auto_height_index"]?.int ?: -1,
            horizontalGap = node["horizontal_gap"]?.int ?: 0,
            verticalGap = node["vertical_gap"]?.int ?: 0,
            roundCorner = node["round_corner"]?.float ?: -1f,
            keyBorder = node["key_border"]?.int ?: -1,
            columns = node["columns"]?.int ?: 30,
            asciiMode = (node["ascii_mode"]?.int ?: 1) == 1,
            resetAsciiMode = node["reset_ascii_mode"]?.boolean,
            labelTransform = node["label_transform"]?.enum<LabelTransform>() ?: LabelTransform.NONE,
            lock = node["lock"]?.boolean ?: false,
            asciiKeyboard = node["ascii_keyboard"]?.string ?: "",
            landscapeKeyboard = node["landscape_keyboard"]?.string ?: "",
            landscapeSplitPercent = node["landscape_split_percent"]?.int ?: 0,
            keyTextOffsetX = node["key_text_offset_x"]?.float ?: 0f,
            keyTextOffsetY = node["key_text_offset_y"]?.float ?: 0f,
            keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: 0f,
            keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: 0f,
            keyHintOffsetX = node["key_hint_offset_x"]?.float ?: 0f,
            keyHintOffsetY = node["key_hint_offset_y"]?.float ?: 0f,
            keyPressOffsetX = node["key_press_offset_x"]?.float ?: 0f,
            keyPressOffsetY = node["key_press_offset_y"]?.float ?: 0f,
            importPreset = node["import_preset"]?.string ?: "",
            keys = node["keys"]?.sequence?.mapNotNull {
                TextKey.decode(it.mapping!!)
            } ?: emptyList(),
        )
    }
}
