/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.prefs

import androidx.preference.PreferenceScreen
import com.osfans.trime.util.WeakHashSet

abstract class PreferenceDelegateProvider {
    private val _preferenceDelegates: MutableMap<String, PreferenceDelegate<*>> = mutableMapOf()

    private val _preferenceDelegatesUi: MutableList<PreferenceDelegateUi<*>> = mutableListOf()

    val preferenceDelegates: Map<String, PreferenceDelegate<*>>
        get() = _preferenceDelegates

    val preferenceDelegatesUi: List<PreferenceDelegateUi<*>>
        get() = _preferenceDelegatesUi

    open fun createUi(screen: PreferenceScreen) {
    }

    fun interface OnChangeListener {
        fun onChange(key: String)
    }

    private val onChangeListeners = WeakHashSet<OnChangeListener>()

    fun registerOnChangeListener(listener: OnChangeListener) {
        onChangeListeners.add(listener)
    }

    fun unregisterOnChangeListener(listener: OnChangeListener) {
        onChangeListeners.remove(listener)
    }

    fun notifyChange(key: String) {
        val preference = _preferenceDelegates[key] ?: return
        onChangeListeners.forEach { it.onChange(key) }
        preference.notifyChange()
    }

    fun PreferenceDelegateUi<*>.registerUi() {
        _preferenceDelegatesUi.add(this)
    }

    fun PreferenceDelegate<*>.register() {
        _preferenceDelegates[key] = this
    }
}
