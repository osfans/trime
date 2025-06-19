// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.soundeffect

import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SoundEffect(
    val name: String = "",
    val sound: List<String>,
    val folder: String,
    val melody: List<String> = listOf(),
    val keyset: List<Key>,
) {
    @Serializable
    data class Key(
        val min: String = "UNKNOWN",
        val max: String = "UNKNOWN",
        val keys: List<String> = listOf(),
        val inOrder: Boolean,
        val sounds: List<Int>,
    ) {
        @Transient
        private val sysKeyCodes = keys.map { KeyEvent.keyCodeFromString(it.uppercase()) }

        @Transient
        private val minKeyCode = KeyEvent.keyCodeFromString(min.uppercase())

        @Transient
        private val maxKeyCode = KeyEvent.keyCodeFromString(max.uppercase())

        fun querySoundIndex(keyCode: Int): Int {
            if (sounds.isEmpty()) return -1
            if (sysKeyCodes.isEmpty() && minKeyCode > maxKeyCode) return -1
            if (sysKeyCodes.isEmpty()) {
                if (keyCode !in minKeyCode..maxKeyCode) return -1
                return sounds[if (inOrder) (keyCode - minKeyCode) % sounds.size else sounds.indices.random()]
            } else {
                if (keyCode !in sysKeyCodes) return -1
                val sysKey = sysKeyCodes.indexOf(keyCode)
                return sounds[if (inOrder) sysKey % sounds.size else sounds.indices.random()]
            }
        }
    }
}
