// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data

import com.osfans.trime.util.appContext

class SymbolHistory(
    val capacity: Int,
) : LinkedHashMap<String, String>(0, .75f, true) {
    companion object {
        const val FILE_NAME = "symbol_history"
    }

    private val file = appContext.filesDir.resolve(FILE_NAME).apply { createNewFile() }

    fun load() {
        val all = file.readLines()
        all.forEach {
            if (it.isNotBlank()) {
                put(it, it)
            }
        }
    }

    fun save() {
        file.writeText(values.joinToString("\n"))
    }

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > capacity

    fun insert(s: String) = put(s, s)

    fun toOrderedList() = values.toList().reversed()
}
