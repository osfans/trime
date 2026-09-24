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
import com.osfans.trime.data.theme.LiquidData
import com.osfans.trime.util.enum
import com.osfans.trime.util.float
import com.osfans.trime.util.get
import com.osfans.trime.util.int
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.sequence
import com.osfans.trime.util.splitWithSurrogates
import com.osfans.trime.util.string
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
            val keyBarNode = node?.pairs?.get("fixed_key_bar")?.mapping
            val keyBar = keyBarNode?.let {
                val position = keyBarNode.pairs["position"]?.enum<KeyBar.Position>()
                    ?: KeyBar.Position.BOTTOM
                val keys = keyBarNode.pairs["keys"]?.sequence?.items
                    ?.mapNotNull { it.string } ?: emptyList()
                KeyBar(position = position, keys = keys)
            } ?: KeyBar(emptyList(), KeyBar.Position.BOTTOM)
            val keyboards =
                node?.pairs?.get("keyboards")?.sequence?.items?.asSequence()
                    ?.mapNotNull { it.string }
                    ?.mapNotNull decode@{ id ->
                        try {
                            val keyboardNode = node.pairs[id]?.mapping
                            val type = keyboardNode?.pairs?.get("type")?.enum<LiquidData.Type>()
                                ?: return@decode null
                            val name = keyboardNode.pairs["name"]?.string ?: id
                            val keysNode = keyboardNode.pairs["keys"]
                            val keys = arrayListOf<KeyItem>()
                            if (keysNode is YamlList) {
                                keysNode.items.forEach { item ->
                                    if (item is YamlMap) {
                                        val map = item.pairs.mapValues { it.value.string!! }
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
                                val value = keysNode?.string ?: ""
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
                    }?.toList() ?: emptyList()
            return LiquidKeyboard(
                singleWidth = node?.pairs?.get("single_width")?.int ?: 0,
                keyHeight = node?.pairs?.get("key_height")?.int ?: 0,
                marginX = node?.pairs?.get("margin_x")?.float ?: 0f,
                fixedKeyBar = keyBar,
                keyboards = keyboards,
            )
        }
    }
}
