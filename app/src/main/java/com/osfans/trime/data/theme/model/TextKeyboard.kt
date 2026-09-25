/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.util.boolean
import com.osfans.trime.util.enum
import com.osfans.trime.util.float
import com.osfans.trime.util.int
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.sequence
import com.osfans.trime.util.string
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
        // behaviors
        val click: KeyActionToken? = null,
        val doubleClick: KeyActionToken? = null,
        val lazyDoubleClick: KeyActionToken? = null,
        val longClick: KeyActionToken? = null,
        val swipeUp: KeyActionToken? = null,
        val swipeDown: KeyActionToken? = null,
        val swipeLeft: KeyActionToken? = null,
        val swipeRight: KeyActionToken? = null,
        val composing: KeyActionToken? = null,
        val hasMenu: KeyActionToken? = null,
        val paging: KeyActionToken? = null,
        val combo: KeyActionToken? = null,
        val ascii: KeyActionToken? = null,
    ) : Parcelable {
        companion object {
            val DEFAULTS = TextKey()

            fun decode(node: YamlMap): TextKey = TextKey(
                width = node.pairs["width"]?.float ?: DEFAULTS.width,
                height = node.pairs["height"]?.float ?: DEFAULTS.height,
                roundCorner = node.pairs["round_corner"]?.float ?: DEFAULTS.roundCorner,
                keyBorder = node.pairs["key_border"]?.int ?: DEFAULTS.keyBorder,
                label = node.pairs["label"]?.string ?: DEFAULTS.label,
                labelSymbol = node.pairs["label_symbol"]?.string ?: DEFAULTS.labelSymbol,
                hint = node.pairs["hint"]?.string ?: DEFAULTS.hint,
                sendBindings = node.pairs["send_bindings"]?.boolean ?: DEFAULTS.sendBindings,
                keyTextSize = node.pairs["key_text_size"]?.float ?: DEFAULTS.keyTextSize,
                symbolTextSize = node.pairs["symbol_text_size"]?.float ?: DEFAULTS.symbolTextSize,
                keyTextOffsetX = node.pairs["key_text_offset_x"]?.float ?: DEFAULTS.keyTextOffsetX,
                keyTextOffsetY = node.pairs["key_text_offset_y"]?.float ?: DEFAULTS.keyTextOffsetY,
                keySymbolOffsetX = node.pairs["key_symbol_offset_x"]?.float ?: DEFAULTS.keySymbolOffsetX,
                keySymbolOffsetY = node.pairs["key_symbol_offset_y"]?.float ?: DEFAULTS.keySymbolOffsetY,
                keyHintOffsetX = node.pairs["key_hint_offset_x"]?.float ?: DEFAULTS.keyHintOffsetX,
                keyHintOffsetY = node.pairs["key_hint_offset_y"]?.float ?: DEFAULTS.keyHintOffsetY,
                keyPressOffsetX = node.pairs["key_press_offset_x"]?.float ?: DEFAULTS.keyPressOffsetX,
                keyPressOffsetY = node.pairs["key_press_offset_y"]?.float ?: DEFAULTS.keyPressOffsetY,
                keyTextColor = node.pairs["key_text_color"]?.string ?: DEFAULTS.keyTextColor,
                keyBackColor = node.pairs["key_back_color"]?.string ?: DEFAULTS.keyBackColor,
                keyBorderColor = node.pairs["key_border_color"]?.string ?: DEFAULTS.keyBorderColor,
                keySymbolColor = node.pairs["key_symbol_color"]?.string ?: DEFAULTS.keySymbolColor,
                hlKeyTextColor = node.pairs["hilited_key_text_color"]?.string ?: DEFAULTS.hlKeyTextColor,
                hlKeyBackColor = node.pairs["hilited_key_back_color"]?.string ?: DEFAULTS.hlKeyBackColor,
                hlKeyBorderColor = node.pairs["hilited_key_border_color"]?.string ?: DEFAULTS.hlKeyBorderColor,
                hlKeySymbolColor = node.pairs["hilited_key_symbol_color"]?.string ?: DEFAULTS.hlKeySymbolColor,
                popup = node.pairs["popup"]?.sequence?.items?.mapNotNull(YamlNode::string) ?: DEFAULTS.popup,
                click = KeyActionToken.decode(node.pairs["click"]),
                doubleClick = KeyActionToken.decode(node.pairs["double_click"]),
                lazyDoubleClick = KeyActionToken.decode(node.pairs["lazy_double_click"]),
                longClick = KeyActionToken.decode(node.pairs["long_click"]),
                swipeUp = KeyActionToken.decode(node.pairs["swipe_up"]),
                swipeDown = KeyActionToken.decode(node.pairs["swipe_down"]),
                swipeLeft = KeyActionToken.decode(node.pairs["swipe_left"]),
                swipeRight = KeyActionToken.decode(node.pairs["swipe_right"]),
                composing = KeyActionToken.decode(node.pairs["composing"]),
                hasMenu = KeyActionToken.decode(node.pairs["has_menu"]),
                paging = KeyActionToken.decode(node.pairs["paging"]),
                combo = KeyActionToken.decode(node.pairs["combo"]),
                ascii = KeyActionToken.decode(node.pairs["ascii"]),
            )
        }
    }

    companion object {
        val DEFAULTS = TextKeyboard()

        fun decode(node: YamlMap): TextKeyboard = TextKeyboard(
            name = node.pairs["name"]?.string ?: DEFAULTS.name,
            author = node.pairs["author"]?.string ?: DEFAULTS.author,
            width = node.pairs["width"]?.float ?: DEFAULTS.width,
            height = node.pairs["height"]?.float ?: DEFAULTS.height,
            keyboardHeight = node.pairs["keyboard_height"]?.int ?: DEFAULTS.keyboardHeight,
            keyboardHeightLand = node.pairs["keyboard_height_land"]?.int ?: DEFAULTS.keyboardHeightLand,
            autoHeightIndex = node.pairs["auto_height_index"]?.int ?: DEFAULTS.autoHeightIndex,
            horizontalGap = node.pairs["horizontal_gap"]?.int ?: DEFAULTS.horizontalGap,
            verticalGap = node.pairs["vertical_gap"]?.int ?: DEFAULTS.verticalGap,
            roundCorner = node.pairs["round_corner"]?.float ?: DEFAULTS.roundCorner,
            keyBorder = node.pairs["key_border"]?.int ?: DEFAULTS.keyBorder,
            columns = node.pairs["columns"]?.int ?: DEFAULTS.columns,
            asciiMode = node.pairs["ascii_mode"]?.int?.let { it == 1 } ?: DEFAULTS.asciiMode,
            resetAsciiMode = node.pairs["reset_ascii_mode"]?.boolean ?: DEFAULTS.resetAsciiMode,
            labelTransform = node.pairs["label_transform"]?.enum<LabelTransform>() ?: DEFAULTS.labelTransform,
            lock = node.pairs["lock"]?.boolean ?: DEFAULTS.lock,
            asciiKeyboard = node.pairs["ascii_keyboard"]?.string ?: DEFAULTS.asciiKeyboard,
            landscapeKeyboard = node.pairs["landscape_keyboard"]?.string ?: DEFAULTS.landscapeKeyboard,
            landscapeSplitPercent = node.pairs["landscape_split_percent"]?.int ?: DEFAULTS.landscapeSplitPercent,
            keyTextOffsetX = node.pairs["key_text_offset_x"]?.float ?: DEFAULTS.keyTextOffsetX,
            keyTextOffsetY = node.pairs["key_text_offset_y"]?.float ?: DEFAULTS.keyTextOffsetY,
            keySymbolOffsetX = node.pairs["key_symbol_offset_x"]?.float ?: DEFAULTS.keySymbolOffsetX,
            keySymbolOffsetY = node.pairs["key_symbol_offset_y"]?.float ?: DEFAULTS.keySymbolOffsetY,
            keyHintOffsetX = node.pairs["key_hint_offset_x"]?.float ?: DEFAULTS.keyHintOffsetX,
            keyHintOffsetY = node.pairs["key_hint_offset_y"]?.float ?: DEFAULTS.keyHintOffsetY,
            keyPressOffsetX = node.pairs["key_press_offset_x"]?.float ?: DEFAULTS.keyPressOffsetX,
            keyPressOffsetY = node.pairs["key_press_offset_y"]?.float ?: DEFAULTS.keyPressOffsetY,
            importPreset = node.pairs["import_preset"]?.string ?: DEFAULTS.importPreset,
            keys = node.pairs["keys"]?.sequence?.items?.mapNotNull {
                TextKey.decode(it.mapping!!)
            } ?: DEFAULTS.keys,
        )
    }
}
