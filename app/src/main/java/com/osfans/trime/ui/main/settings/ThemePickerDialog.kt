// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.core.data.ConfigurationImportException
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object ThemePickerDialog {
    suspend fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val allThemes =
            withContext(Dispatchers.IO) {
                ThemeManager.getAllThemes()
            }
        val selectedTheme by ThemeManager.prefs.selectedTheme
        val selectedIndex = allThemes.indexOfFirst { it.configId == selectedTheme }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.selected_theme)
                if (allThemes.isEmpty()) {
                    setMessage(R.string.no_theme_to_select)
                } else {
                    setSingleChoiceItems(
                        allThemes.map { it.name }.toTypedArray(),
                        selectedIndex,
                    ) { dialog, which ->
                        scope.launch {
                            afterConfirm?.invoke()
                            val newItem = allThemes[which]
                            try {
                                if (UserDataManager.usesExternalSync()) {
                                    check(UserDataManager.hasExternalAccess()) { "No access to the external theme directory" }
                                    val sessionName = "ThemePicker-${UUID.randomUUID()}"
                                    val session = RimeDaemon.createSession(sessionName)
                                    val stats = try {
                                        withTimeout(60.seconds) { session.runOnReady { Unit } }
                                        withTimeout(5.minutes) { session.runOnReady { importThemeConfiguration(newItem.configId) } }
                                    } finally {
                                        RimeDaemon.destroySession(sessionName)
                                    }
                                    context.toast(context.getString(R.string.configuration_import_validated, stats.validatedConfigs, stats.copied), Toast.LENGTH_LONG)
                                } else {
                                    val resolvedThemeId = ThemeManager.selectTheme(newItem.configId)
                                    if (resolvedThemeId != newItem.configId) {
                                        // Chosen theme unavailable: report the one actually in effect.
                                        val fallbackName = allThemes.firstOrNull { it.configId == resolvedThemeId }?.name ?: resolvedThemeId
                                        context.toast(context.getString(R.string.theme_unavailable_fallback, newItem.name, fallbackName), Toast.LENGTH_LONG)
                                    }
                                }
                            } catch (failure: Exception) {
                                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                                val message = when {
                                    failure is ConfigurationImportException && failure.previousConfigurationRestored -> context.getString(R.string.configuration_import_rejected)
                                    failure is ConfigurationImportException && failure.recoveryDirectory != null -> context.getString(R.string.configuration_import_recovery_needed, failure.recoveryDirectory)
                                    failure is ConfigurationImportException -> context.getString(R.string.configuration_import_recovery_failed)
                                    else -> context.getString(R.string.setup__data_path_import_failed)
                                }
                                context.toast(message, Toast.LENGTH_LONG)
                            } finally {
                                dialog.dismiss()
                            }
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }
}
