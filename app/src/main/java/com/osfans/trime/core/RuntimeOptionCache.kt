/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import java.util.concurrent.ConcurrentHashMap

/** Native notifications update this cache; view reads never wait for the engine. */
internal class RuntimeOptionCache {
    private val values = ConcurrentHashMap<String, Boolean>()

    operator fun get(option: String): Boolean = values[option] ?: false

    fun update(option: String, value: Boolean) {
        values[option] = value
    }

    fun clear() = values.clear()
}
