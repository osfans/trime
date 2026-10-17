// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.SharedPreferences
import java.lang.reflect.Proxy

internal class InMemorySharedPreferences(
    initialValues: Map<String, Any?> = emptyMap(),
) {
    private object Removed

    private val values = initialValues.toMutableMap()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    var applyCount = 0
        private set

    var commitSucceeds = true

    val preferences: SharedPreferences =
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, arguments ->
            val args = arguments.orEmpty()
            when (method.name) {
                "contains" -> args[0] in values

                "edit" -> newEditor()

                "getAll" -> values.toMap()

                "getBoolean" -> values[args[0]] as? Boolean ?: args[1]

                "getFloat" -> values[args[0]] as? Float ?: args[1]

                "getInt" -> values[args[0]] as? Int ?: args[1]

                "getLong" -> values[args[0]] as? Long ?: args[1]

                "getString" -> values[args[0]] as? String ?: args[1]

                "getStringSet" ->
                    @Suppress("UNCHECKED_CAST")
                    ((values[args[0]] as? Set<String>) ?: (args[1] as? Set<String>))?.toMutableSet()

                "registerOnSharedPreferenceChangeListener" -> {
                    listeners += args[0] as SharedPreferences.OnSharedPreferenceChangeListener
                    Unit
                }

                "unregisterOnSharedPreferenceChangeListener" -> {
                    listeners -= args[0] as SharedPreferences.OnSharedPreferenceChangeListener
                    Unit
                }

                "toString" -> "InMemorySharedPreferences($values)"

                else -> error("Unsupported SharedPreferences method: ${method.name}")
            }
        } as SharedPreferences

    fun value(key: String): Any? = values[key]

    private fun newEditor(): SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        var clear = false
        lateinit var editor: SharedPreferences.Editor
        editor =
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { _, method, arguments ->
                val args = arguments.orEmpty()
                when (method.name) {
                    "clear" -> {
                        clear = true
                        editor
                    }

                    "putBoolean", "putFloat", "putInt", "putLong" -> {
                        pending[args[0] as String] = args[1]
                        editor
                    }

                    "putString" -> {
                        pending[args[0] as String] = args[1] ?: Removed
                        editor
                    }

                    "putStringSet" -> {
                        @Suppress("UNCHECKED_CAST")
                        val value = (args[1] as? Set<String>)?.toSet()
                        pending[args[0] as String] = value ?: Removed
                        editor
                    }

                    "remove" -> {
                        pending[args[0] as String] = Removed
                        editor
                    }

                    "apply" -> {
                        applyChanges(clear, pending)
                        Unit
                    }

                    "commit" -> {
                        // Android updates the process-local map before waiting for the disk write.
                        applyChanges(clear, pending)
                        commitSucceeds
                    }

                    "toString" -> "InMemorySharedPreferences.Editor"

                    else -> error("Unsupported SharedPreferences.Editor method: ${method.name}")
                }
            } as SharedPreferences.Editor
        return editor
    }

    private fun applyChanges(
        clear: Boolean,
        pending: Map<String, Any?>,
    ) {
        val changed = mutableSetOf<String>()
        if (clear) {
            changed += values.keys
            values.clear()
        }
        pending.forEach { (key, value) ->
            if (value === Removed) {
                if (values.remove(key) != null) changed += key
            } else if (values[key] != value || key !in values) {
                values[key] = value
                changed += key
            }
        }
        applyCount += 1
        changed.forEach { key ->
            listeners.toList().forEach { it.onSharedPreferenceChanged(preferences, key) }
        }
    }
}
