/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.util.splitWithSurrogates
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.enum
import com.osfans.trime.util.yaml.float
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
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
        fun decode(node: Node.Mapping?): LiquidKeyboard {
            val keyBarNode = node?.get("fixed_key_bar")?.mapping
            val keyBar = keyBarNode?.let {
                val position = keyBarNode["position"]?.enum<KeyBar.Position>()
                    ?: KeyBar.Position.BOTTOM
                val keys = keyBarNode["keys"]?.sequence
                    ?.mapNotNull { it.string } ?: emptyList()
                KeyBar(position = position, keys = keys)
            } ?: KeyBar(emptyList(), KeyBar.Position.BOTTOM)
            val keyboards =
                node?.get("keyboards")?.sequence?.mapNotNull decode@{ node ->
                    val id = node.string!!
                    try {
                        val keyboardNode = node[id]?.mapping
                        val type = keyboardNode?.get("type")?.enum<LiquidData.Type>()
                            ?: return@decode null
                        val name = keyboardNode["name"]?.string ?: id
                        val keysNode = keyboardNode["keys"]
                        val keys = arrayListOf<KeyItem>()
                        if (keysNode is Node.Sequence) {
                            keysNode.forEach { item ->
                                if (item is Node.Mapping) {
                                    val map =
                                        item.entries.associate {
                                            it.key.string!! to it.value.string!!
                                        }
                                    if (map.containsKey("click")) {
                                        val clickText = map["click"] ?: ""
                                        val labelText = map["label"] ?: ""
                                        keys.add(KeyItem(clickText, labelText))
                                    } else {
                                        map.forEach { keys.add(KeyItem(it.key, it.value)) }
                                    }
                                } else if (item is Node.Scalar) {
                                    keys.add(KeyItem(item.string))
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
                } ?: emptyList()
            return LiquidKeyboard(
                singleWidth = node?.get("single_width")?.int ?: 0,
                keyHeight = node?.get("key_height")?.int ?: 0,
                marginX = node?.get("margin_x")?.float ?: 0f,
                fixedKeyBar = keyBar,
                keyboards = keyboards,
            )
        }
    }
}
