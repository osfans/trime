/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

/**
 * The behavior a key gets from `role:` (doc/Theme.md §4), so a theme writes
 * `role: enter` instead of repeating `send: Return`.
 *
 * A role only supplies defaults: any explicit `send`/`command`/`select`/
 * `toggle` in the same entry wins.
 */
enum class KeyRole(
    val yamlName: String,
    val send: String = "",
    val command: String = "",
    val option: String = "",
    val select: String = "",
    val toggle: String = "",
) {
    SPACE("space", send = "space"),
    ENTER("enter", send = "Return"),
    BACKSPACE("backspace", send = "BackSpace"),
    TAB("tab", send = "Tab"),
    ESCAPE("escape", send = "Escape"),
    DELETE("delete", send = "Delete"),
    INSERT("insert", send = "Insert"),
    HOME("home", send = "Home"),
    END("end", send = "End"),
    PAGE_UP("page_up", send = "Page_Up"),
    PAGE_DOWN("page_down", send = "Page_Down"),
    CURSOR_LEFT("cursor_left", send = "Left"),
    CURSOR_RIGHT("cursor_right", send = "Right"),
    CURSOR_UP("cursor_up", send = "Up"),
    CURSOR_DOWN("cursor_down", send = "Down"),
    SHIFT("shift", send = "Shift_L"),
    CONTROL("control", send = "Control_L"),
    ALT("alt", send = "Alt_L"),
    META("meta", send = "Meta_L"),
    LANGUAGE("language", send = "SWITCH_CHARSET", toggle = "ascii_mode"),
    KEYBOARD("keyboard", send = "Eisu_toggle", select = ".next"),
    SYMBOL("symbol", command = "liquid_keyboard"),
    CLIPBOARD("clipboard", command = "clipboard_window"),
    HIDE("hide", send = "BACK"),
    EDITOR("editor", send = "Eisu_toggle", select = "editor"),
    FULL_SHAPE("full_shape", send = "SWITCH_CHARSET", toggle = "full_shape"),
    SIMPLIFICATION("simplification", send = "SWITCH_CHARSET", toggle = "simplification"),
    EXTENDED_CHARSET("extended_charset", send = "SWITCH_CHARSET", toggle = "extended_charset"),
    ASCII_PUNCT("ascii_punct", send = "SWITCH_CHARSET", toggle = "ascii_punct"),
    ;

    /** The registered role for [raw], folding case, `-` and `_` like the sources. */
    fun matches(raw: String): Boolean {
        val norm = raw.trim().replace("[-_]".toRegex(), "").lowercase()
        return yamlName.replace("_", "").lowercase() == norm
    }

    companion object {
        /** The role [raw] names, or null when the theme spells an unknown one. */
        fun of(raw: String): KeyRole? = entries.firstOrNull { it.matches(raw) }
    }
}
