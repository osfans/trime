// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data

import com.osfans.trime.TrimeApplication
import com.osfans.trime.ime.core.PrivacyModeState

class SymbolHistory(
    val capacity: Int,
) : LinkedHashMap<String, String>(0, .75f, true) {
    companion object {
        const val FILE_NAME = "symbol_history"
    }

    private val file =
        TrimeApplication.getInstance().directBootAwareContext.filesDir.resolve(FILE_NAME)
            .apply { createNewFile() }
    private var loaded = false

    fun load() {
        if (PrivacyModeState.active) return
        loadFromDisk()
    }

    private fun loadFromDisk() {
        clear()
        val all = file.readLines()
        all.forEach {
            if (it.isNotBlank()) {
                put(it, it)
            }
        }
        loaded = true
    }

    private fun ensureLoaded() {
        if (!loaded) loadFromDisk()
    }

    fun save() {
        if (PrivacyModeState.active) return
        ensureLoaded()
        file.writeText(values.joinToString("\n"))
    }

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > capacity

    fun insert(s: String): String? {
        if (PrivacyModeState.active) return null
        ensureLoaded()
        return put(s, s)
    }

    fun toOrderedList(): List<String> {
        if (PrivacyModeState.active) return emptyList()
        ensureLoaded()
        return values.toList().reversed()
    }
}
