/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string
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
        fun decode(node: Node.Mapping): PresetKey = PresetKey(
            command = node["command"]?.string ?: "",
            option = node["option"]?.string ?: "",
            select = node["select"]?.string ?: "",
            toggle = node["toggle"]?.string ?: "",
            label = node["label"]?.string ?: "",
            preview = node["preview"]?.string,
            shiftLock = node["shift_lock"]?.string ?: "",
            commit = node["commit"]?.string ?: "",
            text = node["text"]?.string ?: "",
            sticky = node["sticky"]?.boolean ?: false,
            repeatable = node["repeatable"]?.boolean ?: false,
            slideCursor = node["slide_cursor"]?.boolean ?: false,
            slideDelete = node["slide_delete"]?.boolean ?: false,
            functional = node["functional"]?.boolean ?: false,
            states = node["states"]?.sequence?.mapNotNull(Node::string) ?: emptyList(),
            send = node["send"]?.string ?: "",
        )
    }
}
