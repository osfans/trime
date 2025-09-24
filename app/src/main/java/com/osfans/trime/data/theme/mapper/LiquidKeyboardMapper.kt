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
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.getEnum
import com.osfans.trime.util.getString
import com.osfans.trime.util.splitWithSurrogates
import timber.log.Timber

class LiquidKeyboardMapper(
    node: YamlMap,
) : Mapper<LiquidKeyboard>(node) {
    override fun map(): LiquidKeyboard {
        val keyBarNode = node.get<YamlMap>("fixed_key_bar")
        val keyBar = keyBarNode?.let {
            val position = keyBarNode.getEnum("position", LiquidKeyboard.KeyBar.Position.BOTTOM)
            val keys = keyBarNode.get<YamlList>("keys")?.items
                ?.map { it.yamlScalar.content } ?: emptyList()
            LiquidKeyboard.KeyBar(position = position, keys = keys)
        } ?: LiquidKeyboard.KeyBar(emptyList(), LiquidKeyboard.KeyBar.Position.BOTTOM)
        val keyboards =
            getStringList("keyboards").mapNotNull decode@{ id ->
                try {
                    val keyboardNode = node.get<YamlMap>(id)
                    val type = keyboardNode?.getEnum<LiquidData.Type>("type")
                        ?: return@decode null
                    val name = keyboardNode.getString("name", id)
                    val keysNode = keyboardNode.get<YamlNode>("keys")
                    val keys = arrayListOf<LiquidKeyboard.KeyItem>()
                    if (keysNode is YamlList) {
                        keysNode.yamlList.items.forEach { item ->
                            if (item is YamlMap) {
                                val map =
                                    item.entries.entries.associate {
                                        it.key.content to it.value.yamlScalar.content
                                    }
                                if (map.containsKey("click")) {
                                    val clickText = map["click"] ?: ""
                                    val labelText = map["label"] ?: ""
                                    keys.add(LiquidKeyboard.KeyItem(clickText, labelText))
                                } else {
                                    map.forEach { keys.add(LiquidKeyboard.KeyItem(it.key, it.value)) }
                                }
                            } else if (item is YamlScalar) {
                                keys.add(LiquidKeyboard.KeyItem(item.content))
                            }
                        }
                    } else {
                        val value = keysNode?.yamlScalar?.content ?: ""
                        if (type == LiquidData.Type.SINGLE) { // single data
                            value.splitWithSurrogates().forEach {
                                keys.add(LiquidKeyboard.KeyItem(it))
                            }
                        } else { // simple keyboard data
                            value
                                .split("\n+".toRegex())
                                .filter { it.isNotEmpty() }
                                .forEach { keys.add(LiquidKeyboard.KeyItem(it)) }
                        }
                    }
                    return@decode LiquidKeyboard.Keyboard(
                        id = id,
                        type = type,
                        name = name,
                        keys = keys,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to decode LiquidKeyboard property 'keyboards'")
                    return@decode null
                }
            }
        return LiquidKeyboard(
            singleWidth = getInt("single_width"),
            keyHeight = getInt("key_height"),
            marginX = getFloat("margin_x"),
            fixedKeyBar = keyBar,
            keyboards = keyboards,
        )
    }
}
