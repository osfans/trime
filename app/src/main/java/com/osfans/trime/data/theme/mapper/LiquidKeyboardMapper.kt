/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.symbol.SimpleKeyBean
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.util.config.Config
import timber.log.Timber

class LiquidKeyboardMapper(
    prefix: String,
    config: Config,
) : Mapper<LiquidKeyboard>(prefix, config) {
    override fun map(): LiquidKeyboard {
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
                    if (!config.isNull("$prefix/$id/type")) {
                        val type =
                            try {
                                SymbolBoardType.valueOf(getString("$id/type").uppercase())
                            } catch (_: IllegalArgumentException) {
                                return@decode null
                            }
                        val keys =
                            if (config.isList("$prefix/$id/keys")) {
                                val list = getList("$id/keys")
                                buildList {
                                    for (item in list) {
                                        if (item.isMap("")) {
                                            val map = item.getStringValueMap("")
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
                            name = getString("$id/name", id),
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
