/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.prefs

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import kotlinx.coroutines.launch

abstract class PreferenceDelegateFragment(
    private val preferenceProvider: PreferenceDelegateProvider,
) : PaddingPreferenceFragment() {
    private val visibility = mutableMapOf<String, Boolean>()

    // it would be better to declare the dependency relationship, rather than reevaluating on each value changed
    @Keep
    private val onValueChangeListener = PreferenceDelegateProvider.OnChangeListener {
        evaluateVisibility()
    }

    init {
        preferenceProvider.registerOnChangeListener(onValueChangeListener)
    }

    open fun onPreferenceUiCreated(screen: PreferenceScreen) {}

    @CallSuper
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        evaluateVisibility()
        preferenceScreen =
            preferenceManager.createPreferenceScreen(preferenceManager.context).also { screen ->
                preferenceProvider.createUi(screen)
                onPreferenceUiCreated(screen)
            }
    }

    fun evaluateVisibility() {
        val changed = mutableMapOf<String, Boolean>()
        preferenceProvider.preferenceDelegatesUi.forEach { ui ->
            val old = visibility[ui.key]
            val new = ui.isEnabled()
            if (old != null && old != new) {
                changed[ui.key] = new
            }
            visibility[ui.key] = new
        }
        if (changed.isNotEmpty()) {
            lifecycleScope.launch {
                changed.forEach { (key, enable) ->
                    findPreference<Preference>(key)?.isEnabled = enable
                }
            }
        }
    }

    override fun onDestroy() {
        preferenceProvider.unregisterOnChangeListener(onValueChangeListener)
        super.onDestroy()
    }
}
