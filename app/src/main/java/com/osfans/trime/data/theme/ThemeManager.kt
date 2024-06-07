// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import androidx.annotation.Keep
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ime.symbol.TabManager
import java.io.File

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    private const val SHARE_TYPE = "SHARE"
    private const val USER_TYPE = "USER"
    const val DEFAULT_THEME = "trime"
    const val TONGWENFENG_THEME = "tongwenfeng"

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

    private fun listThemes(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("trime.yaml") }
            ?.mapNotNull { f ->
                getThemeName(f)
            }
            ?.toMutableList() ?: mutableListOf()
    }

    private fun getThemeName(f: File): String {
        val name = f.name.substringBeforeLast(".trime.yaml")
        return if (name == "trime.yaml") {
            "trime"
        } else {
            name
        }
    }

    private fun getSharedThemes(): List<String> = listThemes(DataManager.sharedDataDir).sorted()

    private fun getUserThemes(): List<String> = listThemes(DataManager.userDataDir).sorted()

    fun getAllThemes(): List<Pair<String, String>> {
        val userThemes = getUserThemes()
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userThemes.map { Pair(it, USER_TYPE) }
        }

        // if same theme exists in both user & share dir, user dir will be used
        val allThemes = userThemes.map { Pair(it, USER_TYPE) }.toMutableList()
        getSharedThemes().forEach {
            if (!userThemes.contains(it)) {
                allThemes.add(Pair(it, SHARE_TYPE))
            }
        }

        return allThemes.also {
            moveThemeToFirst(it, TONGWENFENG_THEME)
            moveThemeToFirst(it, DEFAULT_THEME)
        }
    }

    private fun moveThemeToFirst(
        themes: MutableList<Pair<String, String>>,
        themeName: String,
    ) {
        val defaultThemeIdx = themes.indexOfFirst { it.first == themeName }
        if (defaultThemeIdx > 0) {
            val pair = themes.removeAt(defaultThemeIdx)
            themes.add(0, pair)
        }
    }

    private fun refreshThemes() {
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

    fun isUserTheme(theme: Pair<String, String>): Boolean {
        return theme.second == USER_TYPE
    }
}
