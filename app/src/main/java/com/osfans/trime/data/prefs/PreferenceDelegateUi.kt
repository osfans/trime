/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.prefs

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference

abstract class PreferenceDelegateUi<T : Preference>(
    val key: String,
    private val enableUiOn: (() -> Boolean)? = null,
) {
    abstract fun createUi(context: Context): T

    fun isEnabled() = enableUiOn?.invoke() ?: true

    class StringLike(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: String,
        @StringRes
        val summary: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi<Preference>(key, enableUiOn) {
        override fun createUi(context: Context) =
            Preference(context).apply {
                key = this@StringLike.key
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setDefaultValue(defaultValue)
                if (this@StringLike.summary != null) {
                    setSummary(this@StringLike.summary)
                }
                setTitle(this@StringLike.title)
            }
    }

    class Switch(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: Boolean,
        @StringRes
        val summary: Int? = null,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi<SwitchPreference>(key, enableUiOn) {
        override fun createUi(context: Context) =
            SwitchPreference(context).apply {
                key = this@Switch.key
                isIconSpaceReserved = false
                isSingleLineTitle = false
                setDefaultValue(defaultValue)
                if (this@Switch.summary != null) {
                    setSummary(this@Switch.summary)
                }
                setTitle(this@Switch.title)
            }
    }

    class StringList<T : Any>(
        @StringRes
        val title: Int,
        key: String,
        val defaultValue: T,
        val serializer: PreferenceDelegate.Serializer<T>,
        val entryValues: List<T>,
        @StringRes
        val entryLabels: List<Int>,
        enableUiOn: (() -> Boolean)? = null,
    ) : PreferenceDelegateUi<ListPreference>(key, enableUiOn) {
        override fun createUi(context: Context) =
            ListPreference(context).apply {
                key = this@StringList.key
                isIconSpaceReserved = false
                isSingleLineTitle = false
                entryValues = this@StringList.entryValues.map { serializer.serialize(it) }.toTypedArray()
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                setDefaultValue(serializer.serialize(defaultValue))
                setTitle(this@StringList.title)
                entries = this@StringList.entryLabels.map { context.getString(it) }.toTypedArray()
                setDialogTitle(this@StringList.title)
            }
    }
}
