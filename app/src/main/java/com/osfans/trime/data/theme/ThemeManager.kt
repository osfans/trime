package com.osfans.trime.data.theme

import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import timber.log.Timber
import java.io.File

object ThemeManager {
    val prefs = AppPrefs.defaultInstance()

    private fun listThemes(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("trime.yaml") }
            ?.map { f -> f.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()
    }

    @JvmStatic
    fun switchTheme(theme: String) {
        currentTheme = runCatching { Theme(theme) }.getOrNull() ?: Theme("trime")
        prefs.themeAndColor.selectedTheme = theme
    }

    val sharedThemes = listThemes(DataManager.sharedDataDir)

    val userThemes = listThemes(DataManager.userDataDir)

    private lateinit var currentTheme: Theme

    @JvmStatic
    fun init() {
        val selectedThemeName = prefs.themeAndColor.selectedTheme
        currentTheme = runCatching { Theme(selectedThemeName) }.getOrNull() ?: Theme("trime").also {
            Timber.w("Cannot find selected theme '$selectedThemeName', fallback to ${it.themeId}")
            prefs.themeAndColor.selectedTheme = it.themeId
        }
    }

    @JvmStatic
    fun getAllThemes(): List<String> {
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userThemes
        }
        return sharedThemes + userThemes
    }

    @JvmStatic
    fun getActiveTheme() = currentTheme
}
