// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import androidx.annotation.Keep
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.symbol.TabManager
import com.osfans.trime.util.WeakHashSet
import java.io.File

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    /**
     * Update sharedThemes and userThemes.
     */
    @Keep
    private val onDataDirChange =
        DataManager.OnDataDirChangeListener {
            refreshThemes()
        }

    init {
        // register listener
        DataManager.addOnChangedListener(onDataDirChange)
    }

    private fun listThemes(path: File): MutableList<String> =
        path
            .listFiles { _, name -> name.endsWith("trime.yaml") }
            ?.mapNotNull { f ->
                if (f.name == "trime.yaml") "trime" else f.name.substringBeforeLast(".trime.yaml")
            }?.toMutableList() ?: mutableListOf()

    private val sharedThemes: MutableList<String> get() = listThemes(DataManager.sharedDataDir)

    private val userThemes: MutableList<String> get() = listThemes(DataManager.userDataDir)

    fun getAllThemes(): List<String> = sharedThemes + userThemes

    private fun refreshThemes() {
        sharedThemes.clear()
        userThemes.clear()
        sharedThemes.addAll(listThemes(DataManager.sharedDataDir))
        userThemes.addAll(listThemes(DataManager.userDataDir))
        setNormalTheme(prefs.selectedTheme)
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

    private val prefs = AppPrefs.defaultInstance().theme

    fun init() {
        Theme(prefs.selectedTheme).let {
            EventManager.resetCache()
            FontManager.resetCache(it)
            ColorManager.resetCache(it)
            TabManager.resetCache(it)
            _activeTheme = it
        }
    }

    fun setNormalTheme(name: String) {
        Theme(name).let {
            EventManager.resetCache()
            FontManager.resetCache(it)
            ColorManager.resetCache(it)
            TabManager.resetCache(it)
            activeTheme = it
        }
        prefs.selectedTheme = name
    }
}
