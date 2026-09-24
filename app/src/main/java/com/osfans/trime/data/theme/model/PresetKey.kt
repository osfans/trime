/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.util.boolean
import com.osfans.trime.util.get
import com.osfans.trime.util.pairs
import com.osfans.trime.util.sequence
import com.osfans.trime.util.string
import kotlinx.parcelize.Parcelize

@Parcelize
data class PresetKey(
    val command: String = "",
    val option: String = "",
    val select: String = "",
    val toggle: String = "",
    val label: String = "",
    val preview: String? = null,
    val shiftLock: String = "",
    val commit: String = "",
    val text: String = "",
    val sticky: Boolean = false,
    val repeatable: Boolean = false,
    val slideCursor: Boolean = false,
    val slideDelete: Boolean = false,
    val functional: Boolean = false,
    val states: List<String> = emptyList(),
    val send: String = "",
) : Parcelable {
    companion object {
        fun decode(node: YamlMap): PresetKey = PresetKey(
            command = node.pairs["command"]?.string ?: "",
            option = node.pairs["option"]?.string ?: "",
            select = node.pairs["select"]?.string ?: "",
            toggle = node.pairs["toggle"]?.string ?: "",
            label = node.pairs["label"]?.string ?: "",
            preview = node.pairs["preview"]?.string,
            shiftLock = node.pairs["shift_lock"]?.string ?: "",
            commit = node.pairs["commit"]?.string ?: "",
            text = node.pairs["text"]?.string ?: "",
            sticky = node.pairs["sticky"]?.boolean ?: false,
            repeatable = node.pairs["repeatable"]?.boolean ?: false,
            slideCursor = node.pairs["slide_cursor"]?.boolean ?: false,
            slideDelete = node.pairs["slide_delete"]?.boolean ?: false,
            functional = node.pairs["functional"]?.boolean ?: false,
            states = node.pairs["states"]?.sequence?.items?.mapNotNull(YamlNode::string) ?: emptyList(),
            send = node.pairs["send"]?.string ?: "",
        )
    }
}
