/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.deserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.symbol.SimpleKeyBean
import com.osfans.trime.ime.symbol.SymbolBoardType

class LiquidKeyboardDeserializer : JsonDeserializer<LiquidKeyboard>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): LiquidKeyboard {
        val node = p.codec.readTree<JsonNode>(p)
        val keyBarNode = node["fixed_key_bar"]
        val keyBar =
            LiquidKeyboard.KeyBar(
                position =
                    try {
                        LiquidKeyboard.KeyBar.Position.valueOf(
                            keyBarNode["position"]?.textValue() ?: "BOTTOM",
                        )
                    } catch (_: IllegalArgumentException) {
                        LiquidKeyboard.KeyBar.Position.BOTTOM
                    },
                keys = keyBarNode["keys"].mapNotNull { it.textValue() },
            )
        return LiquidKeyboard(
            singleWidth = node["single_width"]?.intValue() ?: 0,
            keyHeight = node["key_height"]?.intValue() ?: 0,
            marginX = node["margin_x"]?.floatValue() ?: 0f,
            fixedKeyBar = keyBar,
            keyboards =
                node["keyboards"].mapNotNull {
                    deserializeKeyboard(node, it)
                },
        )
    }

    private fun deserializeKeyboard(
        parent: JsonNode?,
        node: JsonNode?,
    ): LiquidKeyboard.Keyboard? {
        if (parent == null || node == null) return null
        val id = node.textValue() ?: return null
        val detail = parent[id]
        val type =
            runCatching {
                detail["type"]?.textValue()?.let {
                    SymbolBoardType.valueOf(it.uppercase())
                }
            }.getOrNull() ?: return null
        return LiquidKeyboard.Keyboard(
            id = id,
            type = type,
            name = detail["name"]?.asText() ?: "",
            keys = deserializeKeys(detail["keys"], type),
        )
    }

    private fun deserializeKeys(
        node: JsonNode?,
        type: SymbolBoardType,
    ): List<SimpleKeyBean> {
        if (node == null) return emptyList()
        return if (node.isArray) {
            buildList(node.size()) {
                if (node.isObject) {
                    val svo =
                        node.properties().associate {
                            it.key to (it.value?.asText() ?: "")
                        }
                    val click = svo["click"]
                    if (click != null) {
                        add(SimpleKeyBean(click, svo["label"] ?: ""))
                    } else {
                        val symbols = SchemaManager.activeSchema.symbols
                        addAll(
                            svo
                                .filter { symbols.containsKey(it.value) }
                                .map { SimpleKeyBean(it.value, it.key) },
                        )
                    }
                } else if (node.isTextual) {
                    add(SimpleKeyBean(node.asText() ?: ""))
                }
            }
        } else if (node.isTextual) {
            val s = node.asText() ?: return emptyList()
            var h = Char(0)
            if (type == SymbolBoardType.SINGLE) { // single data
                buildList {
                    for (ch in s) {
                        when {
                            ch.isHighSurrogate() -> h = ch
                            ch.isLowSurrogate() -> {
                                val string = String(charArrayOf(h, ch))
                                add(SimpleKeyBean(string))
                            }
                            else -> add(SimpleKeyBean(ch.toString()))
                        }
                    }
                }
            } else { // simple keyboard data
                s.split("\n+").filter { it.isNotEmpty() }.map { SimpleKeyBean(it) }
            }
        } else {
            emptyList()
        }
    }
}
