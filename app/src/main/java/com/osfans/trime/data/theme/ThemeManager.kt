package com.osfans.trime.data.theme

import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataDirectoryChangeListener
import com.osfans.trime.data.DataManager
import java.io.File

object ThemeManager : DataDirectoryChangeListener.Listener {

    init {
        // register listener
        DataDirectoryChangeListener.addDirectoryChangeListener(this)
    }

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

    var sharedThemes: MutableList<String> = listThemes(DataManager.sharedDataDir)

    var userThemes: MutableList<String> = listThemes(DataManager.userDataDir)

    private lateinit var currentThemeName: String

    /**
     * Update sharedThemes and userThemes.
     */
    override fun onDataDirectoryChange() {
        sharedThemes = listThemes(DataManager.sharedDataDir)
        userThemes = listThemes(DataManager.userDataDir)
    }

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
