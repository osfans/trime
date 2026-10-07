/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.theme.model.MaybeStringList
import com.osfans.trime.data.theme.model.v2.ThemeV2
import com.osfans.trime.util.WeakHashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: ThemeV2)
    }

    fun getAllThemes(): List<ThemeItem> {
        val sharedThemes = ThemeFilesManager.listThemes(DataManager.sharedDataDir)
        val userThemes = ThemeFilesManager.listThemes(DataManager.userDataDir)
        return sharedThemes + userThemes
    }

    private lateinit var _activeTheme: ThemeV2

    private var _activeFindings: List<ThemeDiagnostics.Finding>? = null

    /**
     * What static checks found in the active theme, or null when it was read
     * from its deployed artifact and never checked. Serves the diagnostics
     * screen: the findings belong to the load that produced the active theme.
     */
    val activeFindings: List<ThemeDiagnostics.Finding>?
        get() {
            ensureActiveTheme()
            return _activeFindings
        }

    private fun ensureActiveTheme() {
        if (!::_activeTheme.isInitialized) {
            _activeTheme = evaluateActiveTheme()
        }
    }

    var activeTheme: ThemeV2
        get() {
            ensureActiveTheme()
            return _activeTheme
        }
        private set(value) {
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

    private val mainScope = MainScope()

    /** 字号/字体偏好变化时重新加载主题，使覆盖即时生效。 */
    private val textAppearanceChangeListener = PreferenceDelegate.OnChangeListener<Any> { _, _ ->
        mainScope.launch { reapplyTheme() }
    }

    init {
        prefs.keyTextSize.registerOnChangeListener(textAppearanceChangeListener)
        prefs.candidateTextSize.registerOnChangeListener(textAppearanceChangeListener)
        prefs.commentTextSize.registerOnChangeListener(textAppearanceChangeListener)
        prefs.keyFont.registerOnChangeListener(textAppearanceChangeListener)
        prefs.candidateFont.registerOnChangeListener(textAppearanceChangeListener)
        prefs.commentFont.registerOnChangeListener(textAppearanceChangeListener)
    }

    private data class ResolvedTheme(
        val configId: String,
        val theme: ThemeV2,
        val findings: List<ThemeDiagnostics.Finding>?,
    )

    private fun getThemeById(id: String): ResolvedTheme {
        when (val result = ThemeLoader.loadTheme(id)) {
            is ThemeLoader.ThemeLoadResult.Success -> return ResolvedTheme(id, applyUserOverrides(result.theme), result.findings)
            is ThemeLoader.ThemeLoadResult.Failure -> Timber.w(result.error)
        }

        if (id != "trime") {
            when (val result = ThemeLoader.loadTheme("trime")) {
                is ThemeLoader.ThemeLoadResult.Success -> {
                    Timber.w("Theme '$id' is unavailable, fallback to default theme 'trime'")
                    return ResolvedTheme("trime", applyUserOverrides(result.theme), result.findings)
                }

                is ThemeLoader.ThemeLoadResult.Failure -> Timber.w(result.error)
            }
        }

        var lastFailure: ThemeLoader.ThemeLoadError? = null
        for (fallbackId in getAllThemes().map { it.configId }.distinct()) {
            when (val result = ThemeLoader.loadTheme(fallbackId)) {
                is ThemeLoader.ThemeLoadResult.Success -> {
                    Timber.w("Theme '$id' is unavailable, fallback to available theme '$fallbackId'")
                    return ResolvedTheme(fallbackId, applyUserOverrides(result.theme), result.findings)
                }

                is ThemeLoader.ThemeLoadResult.Failure -> lastFailure = result.error
            }
        }

        Timber.w(lastFailure, "No valid theme available")
        error("No valid theme available")
    }

    private fun evaluateActiveTheme(): ThemeV2 {
        val selectedThemeId = prefs.selectedTheme.getValue()
        val resolvedTheme = getThemeById(selectedThemeId)
        val newTheme = resolvedTheme.theme
        if (resolvedTheme.configId != selectedThemeId) {
            prefs.selectedTheme.setValue(resolvedTheme.configId)
        }
        applyTheme(resolvedTheme)
        return newTheme
    }

    private fun applyTheme(resolvedTheme: ResolvedTheme) {
        val theme = resolvedTheme.theme
        // The findings describe the file this load read; they are not tied to the
        // views, so they are refreshed even when the theme itself is unchanged.
        _activeFindings = resolvedTheme.findings
        // A structurally equal theme suppresses the change notification below, so the
        // UI tree keeps its views and their injected scope. Replace neither the
        // caches nor the scope in that case, or later scheme changes would update
        // the new global scope while existing views still read the old one.
        if (::_activeTheme.isInitialized && _activeTheme == theme) return
        ColorManager.attachTheme(theme)
        activeTheme = theme
    }

    fun init(configuration: Configuration) {
        ensureActiveTheme()
        ColorManager.init(configuration)
    }

    /**
     * Switches to theme [configId], falling back when it is unavailable.
     * Loading runs on [Dispatchers.IO]; state changes and listener callbacks run on the main thread.
     * @return the config id actually in effect; differs from [configId] when a fallback was used.
     */
    suspend fun selectTheme(configId: String): String {
        val resolvedTheme = withContext(Dispatchers.IO) { getThemeById(configId) }
        return withContext(Dispatchers.Main.immediate) {
            applyTheme(resolvedTheme)
            prefs.selectedTheme.setValue(resolvedTheme.configId)
            resolvedTheme.configId
        }
    }

    /** 用字号/字体偏好覆盖主题值：0 / 空白 = 跟随主题。 */
    private fun applyUserOverrides(theme: ThemeV2): ThemeV2 {
        val keyText = prefs.keyTextSize.getValue()
        val candidateText = prefs.candidateTextSize.getValue()
        val commentText = prefs.commentTextSize.getValue()
        return theme.copy(
            keyboard = theme.keyboard.copy(
                textSize = keyText.takeIf { it > 0 }?.toFloat() ?: theme.keyboard.textSize,
            ),
            candidateBar = theme.candidateBar.copy(
                textSize = candidateText.takeIf { it > 0 }?.toFloat() ?: theme.candidateBar.textSize,
                commentTextSize = commentText.takeIf { it > 0 }?.toFloat() ?: theme.candidateBar.commentTextSize,
            ),
            fontFaces = theme.fontFaces.copy(
                key = prefs.keyFont.getValue().toMaybeStringListOr(theme.fontFaces.key),
                candidate = prefs.candidateFont.getValue().toMaybeStringListOr(theme.fontFaces.candidate),
                comment = prefs.commentFont.getValue().toMaybeStringListOr(theme.fontFaces.comment),
            ),
        )
    }

    /** 空白字体偏好 = 跟随主题。 */
    private fun String.toMaybeStringListOr(fallback: MaybeStringList): MaybeStringList =
        if (isBlank()) fallback else MaybeStringList.Scalar(this)

    /** 重新加载当前主题并应用（字号/字体偏好变化时）。 */
    private suspend fun reapplyTheme() {
        if (!::_activeTheme.isInitialized) return
        val selectedThemeId = prefs.selectedTheme.getValue()
        val resolvedTheme = withContext(Dispatchers.IO) { getThemeById(selectedThemeId) }
        withContext(Dispatchers.Main.immediate) { applyTheme(resolvedTheme) }
    }
}
