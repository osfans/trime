/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.mapper

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlScalar
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.symbol.SimpleKeyBean
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.util.getEnum
import com.osfans.trime.util.getString
import timber.log.Timber

class LiquidKeyboardMapper(
    node: YamlMap,
) : Mapper<LiquidKeyboard>(node) {
    override fun map(): LiquidKeyboard {
        val fixedKeyBarNode = node.get<YamlMap>("fixed_key_bar")
        val fixedKeyBar =
            if (fixedKeyBarNode != null) {
                LiquidKeyboard.KeyBar(
                    position = fixedKeyBarNode.getEnum("position", LiquidKeyboard.KeyBar.Position.BOTTOM),
                    keys =
                        fixedKeyBarNode
                            .get<YamlList>("keys")
                            ?.items
                            ?.map { it.yamlScalar.content } ?: emptyList(),
                )
            } else {
                LiquidKeyboard.KeyBar(emptyList(), LiquidKeyboard.KeyBar.Position.BOTTOM)
            }
        val keyboards =
            getStringList("keyboards").mapNotNull decode@{ id ->
                try {
                    val keyboardNode = node.get<YamlMap>(id)
                    val type = keyboardNode?.getEnum<SymbolBoardType>("type")
                    if (type != null) {
                        val keysNode = keyboardNode.get<YamlNode>("keys")
                        val keys =
                            if (keysNode is YamlList) {
                                val list = keysNode.yamlList.items
                                buildList {
                                    for (item in list) {
                                        if (item is YamlMap) {
                                            val map =
                                                item.entries.entries.associate {
                                                    it.key.content to it.value.yamlScalar.content
                                                }
                                            if (map.containsKey("click")) {
                                                add(
                                                    SimpleKeyBean(
                                                        map["click"] ?: "",
                                                        map["label"] ?: "",
                                                    ),
                                                )
                                            } else {
                                                val symbolKeys =
                                                    SchemaManager.activeSchema.symbolKeys
                                                addAll(
                                                    map
                                                        .filter { symbolKeys.contains(it.value) }
                                                        .map {
                                                            SimpleKeyBean(
                                                                it.value,
                                                                it.key,
                                                            )
                                                        },
                                                )
                                            }
                                        } else if (item is YamlScalar) {
                                            add(SimpleKeyBean(item.content))
                                        }
                                    }
                                }
                            } else {
                                val value = keysNode?.yamlScalar?.content ?: ""
                                if (type == SymbolBoardType.SINGLE) { // single data
                                    buildList {
                                        var h = Char(0)
                                        for (ch in value) {
                                            if (ch.isHighSurrogate()) {
                                                h = ch
                                            } else if (ch.isLowSurrogate()) {
                                                add(
                                                    SimpleKeyBean(
                                                        String(
                                                            charArrayOf(
                                                                h,
                                                                ch,
                                                            ),
                                                        ),
                                                    ),
                                                )
                                            } else {
                                                add(SimpleKeyBean(ch.toString()))
                                            }
                                        }
                                    }
                                } else { // simple keyboard data
                                    value
                                        .split("\n+".toRegex())
                                        .filter { it.isNotEmpty() }
                                        .map { SimpleKeyBean(it) }
                                }
                            }
                        return@decode LiquidKeyboard.Keyboard(
                            id = id,
                            type = type,
                            name = keyboardNode.getString("name", id),
                            keys = keys,
                        )
                    } else {
                        return@decode null
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to decode LiquidKeyboard property 'keyboards'")
                    return@decode null
                }
            }
        return LiquidKeyboard(
            singleWidth = getInt("single_width"),
            keyHeight = getInt("key_height"),
            marginX = getFloat("margin_x"),
            fixedKeyBar = fixedKeyBar,
            keyboards = keyboards,
        )
    }
}
