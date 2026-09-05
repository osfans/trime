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
    val name: String = "",
    val author: String = "",
    val width: Float = 0f,
    val height: Float = 0f,
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val autoHeightIndex: Int = -1,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
    val roundCorner: Float = -1f,
    val keyBorder: Int = -1,
    val columns: Int = 30,
    val asciiMode: Boolean = true,
    val resetAsciiMode: Boolean = false,
    val labelTransform: LabelTransform = LabelTransform.NONE,
    val lock: Boolean = false,
    val asciiKeyboard: String = "",
    val landscapeKeyboard: String = "",
    val landscapeSplitPercent: Int = 0,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val importPreset: String = "",
    val keys: List<TextKey> = emptyList(),
) : Parcelable {
    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    @Parcelize
    data class TextKey(
        val width: Float = 0f,
        val height: Float = 0f,
        val roundCorner: Float = -1f,
        val keyBorder: Int = -1,
        val label: String = "",
        val labelSymbol: String = "",
        val hint: String = "",
        val sendBindings: Boolean = true,
        val keyTextSize: Float = 0f,
        val symbolTextSize: Float = 0f,
        val keyTextOffsetX: Float = 0f,
        val keyTextOffsetY: Float = 0f,
        val keySymbolOffsetX: Float = 0f,
        val keySymbolOffsetY: Float = 0f,
        val keyHintOffsetX: Float = 0f,
        val keyHintOffsetY: Float = 0f,
        val keyPressOffsetX: Float = 0f,
        val keyPressOffsetY: Float = 0f,
        val keyTextColor: String = "",
        val keyBackColor: String = "",
        val keyBorderColor: String = "",
        val keySymbolColor: String = "",
        val hlKeyTextColor: String = "",
        val hlKeyBackColor: String = "",
        val hlKeyBorderColor: String = "",
        val hlKeySymbolColor: String = "",
        val popup: List<String> = emptyList(),
        val behaviors: Map<KeyBehavior, KeyActionToken?> = emptyMap(),
        val hasClickAction: Boolean = behaviors[KeyBehavior.CLICK] != null,
    ) : Parcelable {
        companion object {
            val DEFAULTS = TextKey()

            fun decode(node: Node.Mapping): TextKey = TextKey(
                width = node["width"]?.float ?: DEFAULTS.width,
                height = node["height"]?.float ?: DEFAULTS.height,
                roundCorner = node["round_corner"]?.float ?: DEFAULTS.roundCorner,
                keyBorder = node["key_border"]?.int ?: DEFAULTS.keyBorder,
                label = node["label"]?.string ?: DEFAULTS.label,
                labelSymbol = node["label_symbol"]?.string ?: DEFAULTS.labelSymbol,
                hint = node["hint"]?.string ?: DEFAULTS.hint,
                sendBindings = node["send_bindings"]?.boolean ?: DEFAULTS.sendBindings,
                keyTextSize = node["key_text_size"]?.float ?: DEFAULTS.keyTextSize,
                symbolTextSize = node["symbol_text_size"]?.float ?: DEFAULTS.symbolTextSize,
                keyTextOffsetX = node["key_text_offset_x"]?.float ?: DEFAULTS.keyTextOffsetX,
                keyTextOffsetY = node["key_text_offset_y"]?.float ?: DEFAULTS.keyTextOffsetY,
                keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: DEFAULTS.keySymbolOffsetX,
                keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: DEFAULTS.keySymbolOffsetY,
                keyHintOffsetX = node["key_hint_offset_x"]?.float ?: DEFAULTS.keyHintOffsetX,
                keyHintOffsetY = node["key_hint_offset_y"]?.float ?: DEFAULTS.keyHintOffsetY,
                keyPressOffsetX = node["key_press_offset_x"]?.float ?: DEFAULTS.keyPressOffsetX,
                keyPressOffsetY = node["key_press_offset_y"]?.float ?: DEFAULTS.keyPressOffsetY,
                keyTextColor = node["key_text_color"]?.string ?: DEFAULTS.keyTextColor,
                keyBackColor = node["key_back_color"]?.string ?: DEFAULTS.keyBackColor,
                keyBorderColor = node["key_border_color"]?.string ?: DEFAULTS.keyBorderColor,
                keySymbolColor = node["key_symbol_color"]?.string ?: DEFAULTS.keySymbolColor,
                hlKeyTextColor = node["hilited_key_text_color"]?.string ?: DEFAULTS.hlKeyTextColor,
                hlKeyBackColor = node["hilited_key_back_color"]?.string ?: DEFAULTS.hlKeyBackColor,
                hlKeyBorderColor = node["hilited_key_border_color"]?.string ?: DEFAULTS.hlKeyBorderColor,
                hlKeySymbolColor = node["hilited_key_symbol_color"]?.string ?: DEFAULTS.hlKeySymbolColor,
                popup = node["popup"]?.sequence?.mapNotNull(Node::string) ?: DEFAULTS.popup,
                behaviors = KeyBehavior.entries
                    .associateWith { KeyActionToken.decode(node[it.name.lowercase()]) }
                    .filter { (behavior, token) ->
                        token?.let {
                            when (it) {
                                is KeyActionToken.Plain -> it.token.isNotEmpty()
                                is KeyActionToken.Inline -> listOfNotNull(it.token.commit, it.token.text, it.token.label).isNotEmpty()
                            }
                        } ?: (behavior == KeyBehavior.CLICK)
                    },
            )
        }
    }

    companion object {
        val DEFAULTS = TextKeyboard()

        fun decode(node: Node.Mapping): TextKeyboard = TextKeyboard(
            name = node["name"]?.string ?: DEFAULTS.name,
            author = node["author"]?.string ?: DEFAULTS.author,
            width = node["width"]?.float ?: DEFAULTS.width,
            height = node["height"]?.float ?: DEFAULTS.height,
            keyboardHeight = node["keyboard_height"]?.int ?: DEFAULTS.keyboardHeight,
            keyboardHeightLand = node["keyboard_height_land"]?.int ?: DEFAULTS.keyboardHeightLand,
            autoHeightIndex = node["auto_height_index"]?.int ?: DEFAULTS.autoHeightIndex,
            horizontalGap = node["horizontal_gap"]?.int ?: DEFAULTS.horizontalGap,
            verticalGap = node["vertical_gap"]?.int ?: DEFAULTS.verticalGap,
            roundCorner = node["round_corner"]?.float ?: DEFAULTS.roundCorner,
            keyBorder = node["key_border"]?.int ?: DEFAULTS.keyBorder,
            columns = node["columns"]?.int ?: DEFAULTS.columns,
            asciiMode = node["ascii_mode"]?.int?.let { it == 1 } ?: DEFAULTS.asciiMode,
            resetAsciiMode = node["reset_ascii_mode"]?.boolean ?: DEFAULTS.resetAsciiMode,
            labelTransform = node["label_transform"]?.enum<LabelTransform>() ?: DEFAULTS.labelTransform,
            lock = node["lock"]?.boolean ?: DEFAULTS.lock,
            asciiKeyboard = node["ascii_keyboard"]?.string ?: DEFAULTS.asciiKeyboard,
            landscapeKeyboard = node["landscape_keyboard"]?.string ?: DEFAULTS.landscapeKeyboard,
            landscapeSplitPercent = node["landscape_split_percent"]?.int ?: DEFAULTS.landscapeSplitPercent,
            keyTextOffsetX = node["key_text_offset_x"]?.float ?: DEFAULTS.keyTextOffsetX,
            keyTextOffsetY = node["key_text_offset_y"]?.float ?: DEFAULTS.keyTextOffsetY,
            keySymbolOffsetX = node["key_symbol_offset_x"]?.float ?: DEFAULTS.keySymbolOffsetX,
            keySymbolOffsetY = node["key_symbol_offset_y"]?.float ?: DEFAULTS.keySymbolOffsetY,
            keyHintOffsetX = node["key_hint_offset_x"]?.float ?: DEFAULTS.keyHintOffsetX,
            keyHintOffsetY = node["key_hint_offset_y"]?.float ?: DEFAULTS.keyHintOffsetY,
            keyPressOffsetX = node["key_press_offset_x"]?.float ?: DEFAULTS.keyPressOffsetX,
            keyPressOffsetY = node["key_press_offset_y"]?.float ?: DEFAULTS.keyPressOffsetY,
            importPreset = node["import_preset"]?.string ?: DEFAULTS.importPreset,
            keys = node["keys"]?.sequence?.mapNotNull {
                TextKey.decode(it.mapping!!)
            } ?: DEFAULTS.keys,
        )
    }
}
