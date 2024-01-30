package com.osfans.trime.data.theme

import android.content.res.Configuration
import androidx.annotation.Keep
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.util.isNightMode
import java.io.File

object ThemeManager {
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

    fun refreshThemes() {
        sharedThemes.clear()
        userThemes.clear()
        sharedThemes.addAll(listThemes(DataManager.sharedDataDir))
        userThemes.addAll(listThemes(DataManager.userDataDir))
    }

    private lateinit var _activeTheme: Theme

    @JvmStatic
    var activeTheme: Theme
        get() = _activeTheme
        private set(value) {
            if (_activeTheme == value) return
            _activeTheme = value
        }

    private var isNightMode = false

    val prefs = AppPrefs.defaultInstance().themeAndColor

    fun setNormalTheme(name: String) {
        AppPrefs.defaultInstance().themeAndColor.selectedTheme = name
        _activeTheme = evalActiveTheme()
    }

    private fun evalActiveTheme(): Theme {
        return if (prefs.autoDark) {
            Theme(isNightMode)
        } else {
            Theme(false)
        }
    }

    @JvmStatic
    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        _activeTheme = evalActiveTheme()
    }

    @JvmStatic
    fun onSystemNightModeChange(isNight: Boolean) {
        isNightMode = isNight
        if (::_activeTheme.isInitialized) {
            activeTheme.systemChangeColor(isNightMode)
        } else {
            activeTheme = evalActiveTheme()
        }
    }
}
