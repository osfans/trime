/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.util.virtualKeyCharacterMap
import timber.log.Timber

object KeyCode {
    private const val SYMBOL_CODE_OFFSET = 10000
    private const val UPPER_CODE_OFFSET = 20000

    private val SYMBOL_LABELS: Map<String, String> =
        mapOf(
            "exclam" to "!",
            "quotedbl" to "\"",
            "dollar" to "$",
            "percent" to "%",
            "ampersand" to "&",
            "colon" to ":",
            "less" to "<",
            "greater" to ">",
            "question" to "?",
            "asciicircum" to "^",
            "underscore" to "_",
            "braceleft" to "{",
            "bar" to "|",
            "braceright" to "}",
            "asciitilde" to "~",
        )

    private val SYMBOL_NAME_TO_CODE: Map<String, Int> =
        SYMBOL_LABELS.keys.withIndex().associate { (i, name) ->
            name to (SYMBOL_CODE_OFFSET + i)
        }

    private val SYMBOL_CODE_TO_NAME: Map<Int, String> =
        SYMBOL_NAME_TO_CODE.entries.associate { it.value to it.key }

    private val UPPER_NAME_TO_CODE: Map<String, Int> =
        listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
            "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
            "U", "V", "W", "X", "Y", "Z",
        ).withIndex().associate { (i, name) -> name to (UPPER_CODE_OFFSET + i) }

    private val UPPER_CODE_TO_NAME: Map<Int, String> =
        UPPER_NAME_TO_CODE.entries.associate { it.value to it.key }

    private val CHAR_TO_CODE: Map<String, Int> =
        SYMBOL_LABELS.entries.associate { (name, char) ->
            char to (SYMBOL_NAME_TO_CODE[name] ?: KeyEvent.KEYCODE_UNKNOWN)
        }.plus(
            mapOf(
                "," to KeyEvent.KEYCODE_COMMA,
                "." to KeyEvent.KEYCODE_PERIOD,
                "-" to KeyEvent.KEYCODE_MINUS,
                "=" to KeyEvent.KEYCODE_EQUALS,
                "/" to KeyEvent.KEYCODE_SLASH,
                ";" to KeyEvent.KEYCODE_SEMICOLON,
                "'" to KeyEvent.KEYCODE_APOSTROPHE,
                "`" to KeyEvent.KEYCODE_GRAVE,
                "[" to KeyEvent.KEYCODE_LEFT_BRACKET,
                "]" to KeyEvent.KEYCODE_RIGHT_BRACKET,
                "\\" to KeyEvent.KEYCODE_BACKSLASH,
                "@" to KeyEvent.KEYCODE_AT,
                "#" to KeyEvent.KEYCODE_POUND,
                "*" to KeyEvent.KEYCODE_STAR,
                "+" to KeyEvent.KEYCODE_PLUS,
            ),
        )

    private fun rimeNameToCode(name: String): Int {
        val rimeVal = RimeKeyMapping.nameToKeyVal(name)
        if (rimeVal != RimeKeyMapping.RimeKey_VoidSymbol) {
            val code = RimeKeyMapping.valToKeyCode(rimeVal)
            if (code != KeyEvent.KEYCODE_UNKNOWN) return code
        }
        return KeyEvent.KEYCODE_UNKNOWN
    }

    private fun rimeCodeToName(code: Int): String? {
        val rimeVal = RimeKeyMapping.keyCodeToVal(code)
        if (rimeVal != RimeKeyMapping.RimeKey_VoidSymbol) {
            val name = RimeKeyMapping.keyValToName(rimeVal)
            if (name != "VoidSymbol") return name
        }
        return null
    }

    fun isStandardKey(code: Int): Boolean = code in 1 until SYMBOL_CODE_OFFSET

    fun hasSymbolLabel(code: Int): Boolean = SYMBOL_CODE_TO_NAME.containsKey(code)

    fun getSymbolLabel(code: Int): String {
        val name = SYMBOL_CODE_TO_NAME[code] ?: return ""
        return SYMBOL_LABELS[name] ?: ""
    }

    fun nameToKeyCode(name: String): Int {
        Timber.d("nameToKeyCode: $name")
        if (name.isEmpty()) return KeyEvent.KEYCODE_UNKNOWN

        UPPER_NAME_TO_CODE[name]?.let { return it }
        SYMBOL_NAME_TO_CODE[name]?.let { return it }
        CHAR_TO_CODE[name]?.let { return it }

        val androidCode = KeyEvent.keyCodeFromString("KEYCODE_$name")
        if (androidCode > 0) return androidCode

        val rimeCode = rimeNameToCode(name)
        if (rimeCode != KeyEvent.KEYCODE_UNKNOWN) return rimeCode

        return KeyEvent.KEYCODE_UNKNOWN
    }

    fun codeToKeyName(code: Int): String? {
        UPPER_CODE_TO_NAME[code]?.let { return it }
        SYMBOL_CODE_TO_NAME[code]?.let { return it }

        val rimeName = rimeCodeToName(code)
        if (rimeName != null) return rimeName

        val androidName = KeyEvent.keyCodeToString(code)
        if (androidName.isNotEmpty() && androidName != "KEYCODE_UNKNOWN") {
            return androidName.removePrefix("KEYCODE_")
        }

        return null
    }

    fun getDisplayLabel(
        code: Int,
        mask: Int,
    ): String = if (isStandardKey(code)) {
        if (virtualKeyCharacterMap.isPrintingKey(code)) {
            val charCode = virtualKeyCharacterMap.get(code, mask)
            Timber.d("getDisplayLabel: keyCode=$code, mask=$mask, charCode=$charCode")
            if (charCode > 0) {
                charCode.toChar().toString()
            } else {
                virtualKeyCharacterMap.getDisplayLabel(code).lowercase()
            }
        } else {
            val name = codeToKeyName(code) ?: ""
            name.removePrefix("KP_")
        }
    } else if (hasSymbolLabel(code)) {
        getSymbolLabel(code)
    } else {
        codeToKeyName(code) ?: ""
    }

    fun parse(repr: String): Pair<Int, Int> {
        if (repr.isEmpty()) return 0 to 0
        var modifiers = 0
        var start = 0
        while (true) {
            val found = repr.indexOf('+', start)
            if (found == -1) break

            val token = repr.substring(start, found)
            val modifier =
                when (token) {
                    "Shift" -> KeyEvent.META_SHIFT_ON
                    "Control" -> KeyEvent.META_CTRL_ON
                    "Alt" -> KeyEvent.META_ALT_ON
                    "Lock" -> KeyEvent.META_CAPS_LOCK_ON
                    else -> null
                }
            if (modifier != null) {
                modifiers = modifiers or modifier
            } else {
                Timber.e("Unrecognized modifier '$token'")
                return 0 to 0
            }
            start = found + 1
        }
        val token = repr.substring(start)
        val keycode = nameToKeyCode(token)
        if (keycode == 0) {
            Timber.e("Unrecognized key '$token'")
            return 0 to 0
        }
        return keycode to modifiers
    }
}
