// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sound

import android.view.KeyEvent
import kotlinx.serialization.Serializable

@Serializable
data class SoundEffect(
    var name: String? = null,
    val sound: List<String>,
    val folder: String,
    val melody: List<String>? = null,
    val keyset: List<Key>,
) {
    @Serializable
    data class Key(
        val min: String? = null,
        val max: String? = null,
        val keys: List<String>? = null,
        val inOrder: Boolean,
        val sounds: List<Int>,
    ) {
        private val sysKeys = keys?.map(String::uppercase)?.map(KeyEvent::keyCodeFromString)

        fun soundId(keycode: Int): Int {
            if (sounds.isEmpty()) return -1
            if (sysKeys.isNullOrEmpty()) {
                val min = KeyEvent.keyCodeFromString(min?.uppercase() ?: "UNKNOWN")
                val max = KeyEvent.keyCodeFromString(max?.uppercase() ?: "UNKNOWN")
                if (keycode in min..max) {
                    return sounds[if (inOrder) (keycode - min) % sounds.size else sounds.indices.random()]
                }
            } else {
                val sysKey = sysKeys.indexOf(keycode)
                if (sysKey > -1) {
                    return sounds[if (inOrder) sysKey % sounds.size else sounds.indices.random()]
                }
            }
            return -1
        }
    }
}
