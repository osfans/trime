/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.core.RimeConfig
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.symbol.SimpleKeyBean
import com.osfans.trime.ime.symbol.SymbolBoardType
import timber.log.Timber

class LiquidKeyboardMapper(
    prefix: String,
    config: RimeConfig,
) : Mapper(prefix, config) {
    fun map(): LiquidKeyboard {
        val fixedKeyBar =
            LiquidKeyboard.KeyBar(
                position =
                    try {
                        LiquidKeyboard.KeyBar.Position.valueOf(
                            getString("fixed_key_bar/position", "BOTTOM").uppercase(),
                        )
                    } catch (_: IllegalArgumentException) {
                        LiquidKeyboard.KeyBar.Position.BOTTOM
                    },
                keys = getStringList("fixed_key_bar/keys"),
            )
        val keyboards =
            getStringList("keyboards").mapNotNull decode@{ id ->
                try {
                    val type = getString("$id/type")
                    if (type.isNotEmpty()) {
                        val typeValue = SymbolBoardType.valueOf(type.uppercase())
                        return@decode LiquidKeyboard.Keyboard(
                            id = id,
                            type = typeValue,
                            name = getString("$id/name", id),
                            keys =
                                run {
                                    val list = getList("$id/keys")
                                    if (list.isNotEmpty()) {
                                        buildList {
                                            for (item in list) {
                                                val map = item.getStringValueMap("")
                                                if (map.isNotEmpty()) {
                                                    if (map.containsKey("click")) {
                                                        add(
                                                            SimpleKeyBean(
                                                                map["click"] ?: "",
                                                                map["label"] ?: "",
                                                            ),
                                                        )
                                                    } else {
                                                        val symbolMaps =
                                                            SchemaManager.activeSchema.symbols
                                                        addAll(
                                                            map
                                                                .filter { symbolMaps.containsKey(it.value) }
                                                                .map {
                                                                    SimpleKeyBean(
                                                                        it.value,
                                                                        it.key,
                                                                    )
                                                                },
                                                        )
                                                    }
                                                } else {
                                                    add(SimpleKeyBean(item.getString("")))
                                                }
                                            }
                                        }
                                    } else {
                                        val value = getString("$id/keys")
                                        if (typeValue == SymbolBoardType.SINGLE) { // single data
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
                                },
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
