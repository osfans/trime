package com.osfans.trime.data.theme

import androidx.annotation.Keep
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import java.io.File

object ThemeManager {
    /**
     * Update sharedThemes and userThemes.
     */
    @Keep
    private val onDataDirChange =
        DataManager.OnDataDirChangeListener {
            sharedThemes.clear()
            userThemes.clear()
            sharedThemes.addAll(listThemes(DataManager.sharedDataDir))
            userThemes.addAll(listThemes(DataManager.userDataDir))
        }

    init {
        // register listener
        DataManager.addOnChangedListener(onDataDirChange)
    }

    private fun listThemes(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("trime.yaml") }
            ?.map { f -> f.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()
    }

    @JvmStatic
    fun switchTheme(theme: String) {
        AppPrefs.defaultInstance().themeAndColor.selectedTheme = theme
    }

    val sharedThemes: MutableList<String> = listThemes(DataManager.sharedDataDir)

    val userThemes: MutableList<String> = listThemes(DataManager.userDataDir)

    @JvmStatic
    fun getAllThemes(): List<String> {
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userThemes
        }
        return sharedThemes + userThemes
    }

    @JvmStatic
    fun getActiveTheme() = AppPrefs.defaultInstance().themeAndColor.selectedTheme
}
