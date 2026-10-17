/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    fun getAllThemes(): List<ThemeItem> {
        val sharedThemes = ThemeFilesManager.listThemes(DataManager.sharedDataDir)
        val userThemes = ThemeFilesManager.listThemes(DataManager.userDataDir)
        return sharedThemes + userThemes
    }

    private var _activeTheme: Theme? = null
    private var _activeFindings: List<ThemeDiagnostics.Finding>? = null
    private var activeConfigId: String? = null
    private val loadMutex = Mutex()

    // Reading a theme must never start deployment from a view/layout callback.
    val activeTheme: Theme
        get() = checkNotNull(_activeTheme) { "ThemeManager.init must complete before creating themed views" }

    /**
     * What static checks found in the active theme, or null when it was read
     * but the checks could not run. The findings always belong to the load
     * that produced [activeTheme].
     */
    val activeFindings: List<ThemeDiagnostics.Finding>?
        get() {
            checkNotNull(_activeTheme) { "ThemeManager.init must complete before reading theme diagnostics" }
            return _activeFindings
        }

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onThemeChange(activeTheme) }
    }

    val prefs = AppPrefs.defaultInstance().registerProvider(::ThemePrefs)

    private data class ResolvedTheme(
        val configId: String,
        val theme: Theme,
        val findings: List<ThemeDiagnostics.Finding>?,
    )

    private suspend fun getThemeById(id: String): ResolvedTheme {
        when (val result = ThemeLoader.loadTheme(id)) {
            is ThemeLoader.ThemeLoadResult.Success -> return ResolvedTheme(id, result.theme, result.findings)
            is ThemeLoader.ThemeLoadResult.Failure -> Timber.w(result.error)
        }

        // Keep a decoded, usable theme on a failed reload, not a possibly partial disk artifact.
        _activeTheme?.let { active ->
            activeConfigId?.let { return ResolvedTheme(it, active, _activeFindings) }
        }

        if (id != "trime") {
            when (val result = ThemeLoader.loadTheme("trime")) {
                is ThemeLoader.ThemeLoadResult.Success -> {
                    Timber.w("Theme '$id' is unavailable, fallback to default theme 'trime'")
                    return ResolvedTheme("trime", result.theme, result.findings)
                }

                is ThemeLoader.ThemeLoadResult.Failure -> Timber.w(result.error)
            }
        }

        var lastFailure: ThemeLoader.ThemeLoadError? = null
        for (fallbackId in getAllThemes().map { it.configId }.distinct()) {
            when (val result = ThemeLoader.loadTheme(fallbackId)) {
                is ThemeLoader.ThemeLoadResult.Success -> {
                    Timber.w("Theme '$id' is unavailable, fallback to available theme '$fallbackId'")
                    return ResolvedTheme(fallbackId, result.theme, result.findings)
                }

                is ThemeLoader.ThemeLoadResult.Failure -> lastFailure = result.error
            }
        }

        Timber.w(lastFailure, "No valid theme available")
        error("No valid theme available")
    }

    private fun applyTheme(
        resolvedTheme: ResolvedTheme,
        forceReload: Boolean = false,
    ) {
        val theme = resolvedTheme.theme
        // An ordinary structurally equal selection keeps its views and injected
        // scope. A deployment reload bypasses this because referenced asset bytes
        // are not part of data-class equality. Findings always describe this load.
        if (!forceReload && _activeTheme == theme) {
            activeConfigId = resolvedTheme.configId
            _activeFindings = resolvedTheme.findings
            return
        }
        ColorManager.attachTheme(theme)
        _activeTheme = theme
        activeConfigId = resolvedTheme.configId
        _activeFindings = resolvedTheme.findings
        fireChange()
    }

    suspend fun init(configuration: Configuration) = withContext(Dispatchers.Main.immediate) {
        loadMutex.withLock {
            if (_activeTheme == null) {
                val resolved = withContext(Dispatchers.IO) { getThemeById(prefs.selectedTheme.getValue()) }
                try {
                    applyTheme(resolved)
                } catch (e: Exception) {
                    _activeTheme = null
                    _activeFindings = null
                    activeConfigId = null
                    throw e
                }
                prefs.selectedTheme.setValue(resolved.configId)
            }
            ColorManager.init(configuration)
        }
    }

    /**
     * Switches to theme [configId], falling back when it is unavailable.
     * Native deployment runs on RimeDispatcher, file parsing on [Dispatchers.IO],
     * and state changes and listener callbacks on the main thread.
     * @param forceReload replaces the scope and caches even when the decoded theme is structurally
     *   unchanged. Deployment uses this when referenced image or font bytes may have changed.
     * @return the config id actually in effect; differs from [configId] when a fallback was used.
     */
    suspend fun selectTheme(
        configId: String,
        forceReload: Boolean = false,
    ): String = withContext(Dispatchers.Main.immediate) {
        loadMutex.withLock {
            val resolvedTheme = withContext(Dispatchers.IO) { getThemeById(configId) }
            applyTheme(resolvedTheme, forceReload)
            prefs.selectedTheme.setValue(resolvedTheme.configId)
            resolvedTheme.configId
        }
    }

    /** The import transaction already rebuilt and decoded this exact theme. */
    internal suspend fun applyImportedTheme(result: ThemeLoader.ThemeLoadResult.Success) = withContext(Dispatchers.Main.immediate) {
        loadMutex.withLock {
            if (prefs.selectedTheme.getValue() == result.themeId) {
                applyTheme(ResolvedTheme(result.themeId, result.theme, result.findings), forceReload = true)
            }
        }
    }

    /** Resource-only imports reuse the verified model, replacing its bitmap/font scopes. */
    internal fun loadedThemeForImport(): ThemeLoader.ThemeLoadResult.Success? {
        val theme = _activeTheme ?: return null
        val id = activeConfigId ?: return null
        return ThemeLoader.ThemeLoadResult.Success(id, theme, _activeFindings)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun syncToDeviceEncryptedStorage() {
        val ctx = appContext.createDeviceProtectedStorageContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit {
            prefs.preferenceDelegates.forEach {
                it.value.putValueTo(this@edit)
            }
        }
    }
}
