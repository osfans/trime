package com.osfans.trime.data.theme

import android.content.res.Configuration
import androidx.annotation.Keep
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.isNightMode
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

    private fun listThemes(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("trime.yaml") }
            ?.map { f -> f.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()
    }

    private val sharedThemes: MutableList<String> = listThemes(DataManager.sharedDataDir)

    private val userThemes: MutableList<String> = listThemes(DataManager.userDataDir)

    @JvmStatic
    fun getAllThemes(): List<String> {
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userThemes
        }
        return sharedThemes + userThemes
    }

    private fun refreshThemes() {
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
            fireChange()
            FontManager.reload()
            EventManager.clearCache()
        }

    private var isNightMode = false

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    @JvmStatic
    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    @JvmStatic
    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onThemeChange(_activeTheme) }
    }

    val prefs = AppPrefs.defaultInstance().theme

    fun setNormalTheme(name: String) {
        AppPrefs.defaultInstance().theme.selectedTheme = name
        activeTheme = evalActiveTheme()
    }

    fun setColorScheme(name: String) {
        _activeTheme.setColorScheme(name)
        fireChange()
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
        if (isNightMode == isNight) return
        isNightMode = isNight
        _activeTheme.switchDarkMode(isNightMode)
        fireChange()
    }
}
