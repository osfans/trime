/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlScalar
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.getEnum
import com.osfans.trime.util.getFloat
import com.osfans.trime.util.getInt
import com.osfans.trime.util.getString
import com.osfans.trime.util.getStringList
import com.osfans.trime.util.splitWithSurrogates
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@Parcelize
data class LiquidKeyboard(
    val singleWidth: Int,
    val keyHeight: Int,
    val marginX: Float,
    val fixedKeyBar: KeyBar,
    val keyboards: List<Keyboard>,
) : Parcelable {
    @Parcelize
    data class KeyBar(
        val keys: List<String>,
        val position: Position,
    ) : Parcelable {
        enum class Position {
            TOP,
            LEFT,
            BOTTOM,
            RIGHT,
        }
    }

    @Parcelize
    data class Keyboard(
        val id: String,
        val type: LiquidData.Type,
        val name: String,
        val keys: List<KeyItem>,
    ) : Parcelable

    @Parcelize
    data class KeyItem(
        val text: String,
        val altText: String,
    ) : Parcelable {
        constructor(text: String) : this(text, text)
    }

    companion object {
        fun decode(node: YamlMap?): LiquidKeyboard {
            val keyBarNode = node?.get<YamlMap>("fixed_key_bar")
            val keyBar = keyBarNode?.let {
                val position = keyBarNode.getEnum("position", KeyBar.Position.BOTTOM)
                val keys = keyBarNode.get<YamlList>("keys")?.items
                    ?.map { it.yamlScalar.content } ?: emptyList()
                KeyBar(position = position, keys = keys)
            } ?: KeyBar(emptyList(), KeyBar.Position.BOTTOM)
            val keyboards =
                node?.getStringList("keyboards")?.mapNotNull decode@{ id ->
                    try {
                        val keyboardNode = node.get<YamlMap>(id)
                        val type = keyboardNode?.getEnum<LiquidData.Type>("type")
                            ?: return@decode null
                        val name = keyboardNode.getString("name", id)
                        val keysNode = keyboardNode.get<YamlNode>("keys")
                        val keys = arrayListOf<KeyItem>()
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
                                        keys.add(KeyItem(clickText, labelText))
                                    } else {
                                        map.forEach { keys.add(KeyItem(it.key, it.value)) }
                                    }
                                } else if (item is YamlScalar) {
                                    keys.add(KeyItem(item.content))
                                }
                            }
                        } else {
                            val value = keysNode?.yamlScalar?.content ?: ""
                            if (type == LiquidData.Type.SINGLE) { // single data
                                value.splitWithSurrogates().forEach {
                                    keys.add(KeyItem(it))
                                }
                            } else { // simple keyboard data
                                value
                                    .split("\n+".toRegex())
                                    .filter { it.isNotEmpty() }
                                    .forEach { keys.add(KeyItem(it)) }
                            }
                        }
                        return@decode Keyboard(
                            id = id,
                            type = type,
                            name = name,
                            keys = keys,
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to decode LiquidKeyboard property 'keyboards'")
                        return@decode null
                    }
                } ?: emptyList()
            return LiquidKeyboard(
                singleWidth = node.getInt("single_width"),
                keyHeight = node.getInt("key_height"),
                marginX = node.getFloat("margin_x"),
                fixedKeyBar = keyBar,
                keyboards = keyboards,
            )
        }
    }
}
