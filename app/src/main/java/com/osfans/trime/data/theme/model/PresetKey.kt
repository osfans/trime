/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.osfans.trime.util.getBool
import com.osfans.trime.util.getString
import com.osfans.trime.util.getStringList
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
            command = node.getString("command"),
            option = node.getString("option"),
            select = node.getString("select"),
            toggle = node.getString("toggle"),
            label = node.getString("label"),
            preview = node.getScalar("preview")?.content,
            shiftLock = node.getString("shift_lock"),
            commit = node.getString("commit"),
            text = node.getString("text"),
            sticky = node.getBool("sticky"),
            repeatable = node.getBool("repeatable"),
            slideCursor = node.getBool("slide_cursor"),
            slideDelete = node.getBool("slide_delete"),
            functional = node.getBool("functional"),
            states = node.getStringList("states") ?: emptyList(),
            send = node.getString("send"),
        )
    }
}
