/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.deserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyBehavior

class TextKeyDeserializer : JsonDeserializer<TextKeyboard.TextKey>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): TextKeyboard.TextKey {
        val node = p.codec.readTree<JsonNode>(p)
        return TextKeyboard.TextKey(
            width = node["width"]?.asDouble()?.toFloat() ?: 0f,
            height = node["height"]?.asDouble()?.toFloat() ?: 0f,
            roundCorner = node["round_corner"]?.asDouble()?.toFloat() ?: 0f,
            label = node["label"]?.asText() ?: "",
            labelSymbol = node["label_symbol"]?.asText() ?: "",
            hint = node["hint"]?.asText() ?: "",
            click = node["click"]?.asText() ?: "",
            sendBindings = node["send_bindings"]?.asBoolean() ?: true,
            keyTextSize = node["key_text_size"]?.asDouble()?.toFloat() ?: 0f,
            symbolTextSize = node["symbol_text_size"]?.asDouble()?.toFloat() ?: 0f,
            keyTextOffsetX = node["key_text_offset_x"]?.asDouble()?.toFloat() ?: 0f,
            keyTextOffsetY = node["key_text_offset_y"]?.asDouble()?.toFloat() ?: 0f,
            keySymbolOffsetX = node["key_symbol_offset_x"]?.asDouble()?.toFloat() ?: 0f,
            keySymbolOffsetY = node["key_symbol_offset_y"]?.asDouble()?.toFloat() ?: 0f,
            keyHintOffsetX = node["key_hint_offset_x"]?.asDouble()?.toFloat() ?: 0f,
            keyHintOffsetY = node["key_hint_offset_y"]?.asDouble()?.toFloat() ?: 0f,
            keyPressOffsetX = node["key_press_offset_x"]?.asInt() ?: 0,
            keyPressOffsetY = node["key_press_offset_y"]?.asInt() ?: 0,
            keyTextColor = node["key_text_color"]?.asText() ?: "key_text_color",
            keyBackColor = node["key_back_color"]?.asText() ?: "key_back_color",
            keySymbolColor = node["key_symbol_color"]?.asText() ?: "key_symbol_color",
            hlKeyTextColor = node["hilited_key_text_color"]?.asText() ?: "hilited_key_text_color",
            hlKeyBackColor = node["hilited_key_back_color"]?.asText() ?: "hilited_key_back_color",
            hlKeySymbolColor = node["hilited_key_symbol_color"]?.asText() ?: "hilited_key_symbol_color",
            behaviors =
                buildMap {
                    KeyBehavior.entries.forEach { entry ->
                        val action = node[entry.name.lowercase()]?.asText() ?: ""
                        if (action.isNotEmpty() || entry == KeyBehavior.CLICK) {
                            put(entry, action)
                        }
                    }
                },
        )
    }
}
