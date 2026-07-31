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
    fun isStandardKey(code: Int): Boolean = code in 1 until RimeKeyMapping.SYMBOL_CODE_OFFSET

    fun nameToKeyCode(name: String): Int {
        Timber.d("nameToKeyCode: $name")
        if (name.isEmpty()) return KeyEvent.KEYCODE_UNKNOWN

        RimeKeyMapping.upperNameToCode(name)?.let { return it }
        RimeKeyMapping.symbolNameToCode(name)?.let { return it }
        RimeKeyMapping.charToCode(name)?.let { return it }

        val androidCode = KeyEvent.keyCodeFromString("KEYCODE_$name")
        if (androidCode > 0) return androidCode

        val rimeCode = RimeKeyMapping.nameToKeyCode(name)
        if (rimeCode != KeyEvent.KEYCODE_UNKNOWN) return rimeCode

        return KeyEvent.KEYCODE_UNKNOWN
    }

    fun codeToKeyName(code: Int): String? {
        RimeKeyMapping.upperCodeToName(code)?.let { return it }
        RimeKeyMapping.symbolCodeToName(code)?.let { return it }

        val rimeName = RimeKeyMapping.keyCodeToName(code)
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
    } else {
        RimeKeyMapping.symbolCodeToLabel(code) ?: codeToKeyName(code) ?: ""
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
