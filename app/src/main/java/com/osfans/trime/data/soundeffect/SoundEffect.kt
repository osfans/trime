/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.soundeffect

import android.view.KeyEvent
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.boolean
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.int
import com.osfans.trime.util.yaml.sequence
import com.osfans.trime.util.yaml.string

data class SoundEffect(
    val name: String = "",
    val sound: List<String>,
    val folder: String,
    val melody: List<String> = listOf(),
    val keyset: List<Key>,
) {
    data class Key(
        val min: String = "UNKNOWN",
        val max: String = "UNKNOWN",
        val keys: List<String> = listOf(),
        val inOrder: Boolean,
        val sounds: List<Int>,
    ) {
        private val sysKeyCodes = keys.map { KeyEvent.keyCodeFromString(it.uppercase()) }

        private val minKeyCode = KeyEvent.keyCodeFromString(min.uppercase())

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

        companion object {
            fun decode(node: Node): Key = Key(
                min = node["min"]?.string ?: "UNKNOWN",
                max = node["max"]?.string ?: "UNKNOWN",
                keys = node["keys"]?.sequence?.mapNotNull {
                    it.string
                } ?: emptyList(),
                inOrder = node["inOrder"]!!.boolean!!,
                sounds = node["sounds"]!!.sequence!!.mapNotNull {
                    it.int
                },
            )
        }
    }

    companion object {
        fun decode(node: Node): SoundEffect = SoundEffect(
            name = node["name"]?.string ?: "",
            sound = node["sound"]?.sequence?.mapNotNull {
                it.string
            } ?: emptyList(),
            folder = node["folder"]?.string!!,
            melody = node["melody"]?.sequence?.mapNotNull {
                it.string
            } ?: emptyList(),
            keyset = node["keyset"]!!.sequence!!.mapNotNull {
                Key.decode(it)
            },
        )
    }
}
