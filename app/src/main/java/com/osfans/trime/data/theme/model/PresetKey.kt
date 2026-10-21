/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable
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
    @Serializable(with = LenientStringListSerializer::class)
    val states: List<String> = emptyList(),
    val send: String = "",
    /**
     * A named behavior (doc/Theme.md §4) that fills `send`/`command`/`select`/
     * `toggle` when they are empty; explicit fields always win.
     */
    val role: String = "",
) {
    /** Fills this key's missing behavior fields from [role]. */
    fun withRoleDefaults(): PresetKey {
        val r = KeyRole.of(role) ?: return this
        return copy(
            send = send.ifEmpty { r.send },
            command = command.ifEmpty { r.command },
            option = option.ifEmpty { r.option },
            select = select.ifEmpty { r.select },
            toggle = toggle.ifEmpty { r.toggle },
        )
    }
}
