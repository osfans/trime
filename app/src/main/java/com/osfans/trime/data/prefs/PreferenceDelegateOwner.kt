// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.SharedPreferences

abstract class PreferenceDelegateOwner(
    protected val sharedPreferences: SharedPreferences,
) {
    protected fun int(
        key: String,
        defaultValue: Int,
    ) = PreferenceDelegate(sharedPreferences, key, defaultValue)

    protected fun long(
        key: String,
        defaultValue: Long,
    ) = PreferenceDelegate(sharedPreferences, key, defaultValue)

    protected fun float(
        key: String,
        defaultValue: Float,
    ) = PreferenceDelegate(sharedPreferences, key, defaultValue)

    protected fun bool(
        key: String,
        defaultValue: Boolean,
    ): PreferenceDelegate<Boolean> = PreferenceDelegate(sharedPreferences, key, defaultValue)

    protected fun string(
        key: String,
        defaultValue: String,
    ): PreferenceDelegate<String> = PreferenceDelegate(sharedPreferences, key, defaultValue)

    protected fun stringSet(
        key: String,
        defaultValue: Set<String>,
    ) = PreferenceDelegate(sharedPreferences, key, defaultValue)

    protected fun <T : Any> serializable(
        key: String,
        defaultValue: T,
        serializer: PreferenceDelegate.Serializer<T>,
    ) = PreferenceDelegate.SerializableDelegate(sharedPreferences, key, defaultValue, serializer)

    protected inline fun <reified T : Enum<T>> enum(
        key: String,
        defaultValue: T,
    ) = serializable(
        key,
        defaultValue,
        object : PreferenceDelegate.Serializer<T> {
            override fun serialize(t: T) = t.name

            override fun deserialize(raw: String) = enumValueOf<T>(raw.uppercase())
        },
    )
}
