// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import androidx.annotation.Keep
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.symbol.TabManager
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
    }

    // 在初始化 ColorManager 时会被赋值
    lateinit var activeTheme: Theme
        private set

    private val prefs = AppPrefs.defaultInstance().theme

    fun init() = setNormalTheme(prefs.selectedTheme)

    fun setNormalTheme(name: String) {
        Theme(name).let {
            if (::activeTheme.isInitialized) {
                if (it == activeTheme) return
            }
            activeTheme = it
            // 由于这里的顺序不能打乱，不适合使用 listener
            EventManager.refresh()
            FontManager.refresh()
            ColorManager.refresh()
            TabManager.refresh()
        }
    }
}
