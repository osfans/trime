package com.osfans.trime.data.theme

import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import java.io.File

object ThemeManager {

    private fun listThemes(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("trime.yaml") }
            ?.map { f -> f.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()
    }

    @JvmStatic
    fun switchTheme(theme: String) {
        currentThemeName = theme
        AppPrefs.defaultInstance().themeAndColor.selectedTheme = theme
    }

    val sharedThemes: MutableList<String> = listThemes(DataManager.sharedDataDir)

    val userThemes: MutableList<String> = listThemes(DataManager.userDataDir)

    private lateinit var currentThemeName: String

    @JvmStatic
    fun init() {
        currentThemeName = AppPrefs.defaultInstance().themeAndColor.selectedTheme
    }

    @JvmStatic
    fun getAllThemes(): List<String> {
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userThemes
        }
        return sharedThemes + userThemes
    }

    @JvmStatic
    fun getActiveTheme() = currentThemeName
}
