// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class PreferenceDelegate<T : Any>(
    val sharedPreferences: SharedPreferences,
    val key: String,
    val defaultValue: T,
) : ReadWriteProperty<Any?, T> {
    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    open fun getValue(fallbackKey: String): T {
        val finalKey = key.ifEmpty { fallbackKey }
        return try {
            when (defaultValue) {
                is Int -> sharedPreferences.getInt(finalKey, defaultValue)
                is Long -> sharedPreferences.getLong(finalKey, defaultValue)
                is Float -> sharedPreferences.getFloat(finalKey, defaultValue)
                is Boolean -> sharedPreferences.getBoolean(finalKey, defaultValue)
                is String -> sharedPreferences.getString(finalKey, defaultValue) ?: defaultValue
                is Set<*> -> sharedPreferences.getStringSet(finalKey, defaultValue as? Set<String>)
                else -> null
            } as T
        } catch (e: Exception) {
            setValue(fallbackKey, defaultValue)
            defaultValue
        }
    }

    open fun setValue(
        fallbackKey: String,
        value: T,
    ) {
        val finalKey = key.ifEmpty { fallbackKey }
        sharedPreferences.edit {
            when (value) {
                is Int -> putInt(finalKey, value)
                is Long -> putLong(finalKey, value)
                is Float -> putFloat(finalKey, value)
                is Boolean -> putBoolean(finalKey, value)
                is String -> putString(finalKey, value)
                is Set<*> -> putStringSet(finalKey, value.map { it.toString() }.toHashSet())
            }
        }
    }

    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = getValue(property.name)

    override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) = setValue(property.name, value)

    interface Serializer<T : Any> {
        fun serialize(t: T): String

        fun deserialize(raw: String): T?
    }

    class SerializableDelegate<T : Any>(
        sharedPreferences: SharedPreferences,
        key: String,
        defaultValue: T,
        private val serializer: Serializer<T>,
    ) : PreferenceDelegate<T>(sharedPreferences, key, defaultValue) {
        override fun setValue(
            fallbackKey: String,
            value: T,
        ) {
            val finalKey = key.ifEmpty { fallbackKey }
            sharedPreferences.edit { putString(finalKey, serializer.serialize(value)) }
        }

        override fun getValue(fallbackKey: String): T {
            val finalKey = key.ifEmpty { fallbackKey }
            return try {
                sharedPreferences.getString(finalKey, null)?.let {
                    serializer.deserialize(it)
                } ?: defaultValue
            } catch (e: Exception) {
                setValue(fallbackKey, defaultValue)
                defaultValue
            }
        }
    }
}
