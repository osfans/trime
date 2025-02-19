// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.symbol.TabManager
import com.osfans.trime.util.WeakHashSet

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    fun getAllThemes(): List<Theme> {
        val sharedThemes = ThemeFilesManager.listThemes(DataManager.sharedDataDir)
        val userThemes = ThemeFilesManager.listThemes(DataManager.userDataDir)
        return sharedThemes + userThemes
    }

    private lateinit var _activeTheme: Theme

    var activeTheme: Theme
        get() = _activeTheme
        set(value) {
            if (::_activeTheme.isInitialized && _activeTheme == value) return
            _activeTheme = value
            fireChange()
        }

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onThemeChange(_activeTheme) }
    }

    val prefs = AppPrefs.defaultInstance().registerProvider(::ThemePrefs)

    private fun evaluateActiveTheme(): Theme {
        val newTheme = Theme(prefs.selectedTheme.getValue())
        KeyActionManager.resetCache()
        FontManager.resetCache(newTheme)
        ColorManager.resetCache(newTheme)
        TabManager.resetCache(newTheme)
        return newTheme
    }

    fun init() {
        _activeTheme = evaluateActiveTheme()
    }

    fun selectTheme(theme: Theme) {
        KeyActionManager.resetCache()
        FontManager.resetCache(theme)
        ColorManager.resetCache(theme)
        TabManager.resetCache(theme)
        activeTheme = theme
        prefs.selectedTheme.setValue(theme.configId)
    }
}
