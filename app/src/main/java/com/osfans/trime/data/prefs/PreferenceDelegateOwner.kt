/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.preference.PreferenceScreen

abstract class PreferenceDelegateOwner(
    protected val sharedPreferences: SharedPreferences,
    @StringRes val title: Int = 0,
) : PreferenceDelegateProvider() {
    protected fun int(
        key: String,
        defaultValue: Int,
    ) = PreferenceDelegate(sharedPreferences, key, defaultValue).apply { register() }

    protected fun long(
        key: String,
        defaultValue: Long,
    ) = PreferenceDelegate(sharedPreferences, key, defaultValue).apply { register() }

    protected fun float(
        key: String,
        defaultValue: Float,
    ) = PreferenceDelegate(sharedPreferences, key, defaultValue)

    protected fun bool(
        key: String,
        defaultValue: Boolean,
    ): PreferenceDelegate<Boolean> = PreferenceDelegate(sharedPreferences, key, defaultValue).apply { register() }

    protected fun string(
        key: String,
        defaultValue: String,
    ): PreferenceDelegate<String> = PreferenceDelegate(sharedPreferences, key, defaultValue).apply { register() }

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

    protected fun string(
        @StringRes
        title: Int,
        key: String,
        defaultValue: String,
        @StringRes
        summary: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ): PreferenceDelegate<String> {
        val pref = PreferenceDelegate(sharedPreferences, key, defaultValue)
        val ui = PreferenceDelegateUi.StringLike(title, key, defaultValue, summary, enableUiOn)
        pref.register()
        ui.registerUi()
        return pref
    }

    protected fun switch(
        @StringRes
        title: Int,
        key: String,
        defaultValue: Boolean,
        @StringRes
        summary: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ): PreferenceDelegate<Boolean> {
        val pref = PreferenceDelegate(sharedPreferences, key, defaultValue)
        val ui = PreferenceDelegateUi.Switch(title, key, defaultValue, summary, enableUiOn)
        pref.register()
        ui.registerUi()
        return pref
    }

    protected fun <T : Any> list(
        @StringRes
        title: Int,
        key: String,
        defaultValue: T,
        serializer: PreferenceDelegate.Serializer<T>,
        entryValues: List<T>,
        @StringRes
        entryLabels: List<Int>,
        enableUiOn: (() -> Boolean)? = null,
    ): PreferenceDelegate.SerializableDelegate<T> {
        val pref = PreferenceDelegate.SerializableDelegate(sharedPreferences, key, defaultValue, serializer)
        val ui = PreferenceDelegateUi.StringList(title, key, defaultValue, serializer, entryValues, entryLabels, enableUiOn)
        pref.register()
        ui.registerUi()
        return pref
    }

    protected fun <T : Any> list(
        @StringRes
        title: Int,
        key: String,
        defaultValue: T,
        entryValues: (() -> List<String>),
        entryLabels: ((Context) -> List<CharSequence>),
        enableUiOn: (() -> Boolean)? = null,
    ): PreferenceDelegate<String> {
        val pref = PreferenceDelegate(sharedPreferences, key, defaultValue.toString())
        val ui = PreferenceDelegateUi.UniversalStringList(title, key, defaultValue, entryValues, entryLabels, enableUiOn)
        pref.register()
        ui.registerUi()
        return pref
    }

    // TODO: replace all [enum] with this
    protected inline fun <reified T> enum(
        @StringRes title: Int,
        key: String,
        defaultValue: T,
        noinline enableUiOn: (() -> Boolean)? = null,
    ): PreferenceDelegate.SerializableDelegate<T> where T : Enum<T>, T : PreferenceDelegateEnum {
        val serializer =
            object : PreferenceDelegate.Serializer<T> {
                override fun serialize(t: T) = t.name

                override fun deserialize(raw: String) = enumValueOf<T>(raw.uppercase())
            }
        val entryValues = enumValues<T>().toList()
        val entryLabels = entryValues.map { it.stringRes }
        return list(title, key, defaultValue, serializer, entryValues, entryLabels, enableUiOn)
    }

    protected fun editText(
        @StringRes
        title: Int,
        key: String,
        defaultValue: String,
        @StringRes
        message: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ): PreferenceDelegate<String> {
        val pref = PreferenceDelegate(sharedPreferences, key, defaultValue)
        val ui = PreferenceDelegateUi.EditText(title, key, defaultValue, message, enableUiOn)
        pref.register()
        ui.registerUi()
        return pref
    }

    protected fun int(
        @StringRes
        title: Int,
        key: String,
        defaultValue: Int,
        min: Int = 0,
        max: Int = Int.MAX_VALUE,
        unit: String = "",
        step: Int = 1,
        @StringRes
        defaultLabel: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ): PreferenceDelegate<Int> {
        val pref = PreferenceDelegate(sharedPreferences, key, defaultValue)
        // Int can overflow when min < 0 && max == Int.MAX_VALUE
        val ui = if ((max.toLong() - min.toLong()) / step.toLong() >= 256L) {
            PreferenceDelegateUi.EditTextInt(
                title,
                key,
                defaultValue,
                min,
                max,
                unit,
                enableUiOn,
            )
        } else {
            PreferenceDelegateUi.SeekBarInt(
                title, key, defaultValue, min, max, unit, step, defaultLabel, enableUiOn,
            )
        }
        pref.register()
        ui.registerUi()
        return pref
    }

    override fun createUi(screen: PreferenceScreen) {
        val ctx = screen.context
        preferenceDelegatesUi.forEach {
            screen.addPreference(
                it.createUi(ctx).apply {
                    isEnabled = it.isEnabled()
                },
            )
        }
    }
}
