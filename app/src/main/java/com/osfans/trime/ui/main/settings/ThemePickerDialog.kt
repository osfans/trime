// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.data.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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
                            withContext(Dispatchers.IO) {
                                if (RimeDataSync.usesExternalSync()) {
                                    RimeDataSync.importThemeToLocal(context, newItem.configId)
                                        .onFailure { Timber.w(it, "Theme import failed for ${newItem.configId}") }
                                }
                            }
                            ThemeManager.selectTheme(newItem.configId)
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }
}
