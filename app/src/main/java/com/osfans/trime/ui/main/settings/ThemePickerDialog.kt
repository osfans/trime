// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeFilesManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.components.withLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ThemePickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        preSelect: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val allThemes = ThemeManager.getAllThemes()
        val selectedTheme by ThemeManager.prefs.selectedTheme
        val selectedIndex = allThemes.indexOfFirst { it.id == selectedTheme.id }
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
                            preSelect?.invoke()
                            val newTheme = allThemes[which]
                            ThemeManager.selectTheme(newTheme)
                            dialog.dismiss()
                        }
                    }
                }
                setNeutralButton(R.string.deploy) { _, _ ->
                    scope.withLoadingDialog(context, R.string.deploy_progress) {
                        withContext(NonCancellable + Dispatchers.Default) {
                            ThemeFilesManager.deployThemes()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }
}
