// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.storage.FolderSync
import com.osfans.trime.data.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ThemePickerDialog {
    suspend fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
    ): AlertDialog {
        val all =
            withContext(Dispatchers.IO) {
                ThemeManager.getAllThemes()
            }
        val allNames =
            all.map {
                when (it) {
                    "trime" -> context.getString(R.string.theme_trime)
                    "tongwenfeng" -> context.getString(R.string.theme_tongwenfeng)
                    else -> it
                }
            }
        val current =
            AppPrefs.defaultInstance().theme.selectedTheme
        val currentIndex = all.indexOfFirst { it == current }
        return AlertDialog.Builder(context).apply {
            setTitle(R.string.looks__selected_theme_title)
            if (allNames.isEmpty()) {
                setMessage(R.string.no_theme_to_select)
            } else {
                setSingleChoiceItems(
                    allNames.toTypedArray(),
                    currentIndex,
                ) { dialog, which ->
                    scope.launch {
                        dialog.dismiss()
                        withContext(Dispatchers.IO) {
                            copyThemeFile(context, all[which])
                            ThemeManager.setNormalTheme(all[which])
                        }
                    }
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }.create()
    }

    private suspend fun copyThemeFile(
        context: Context,
        selectedName: String,
    ) {
        val fileNameWithoutExt = if (selectedName == "trime") selectedName else "$selectedName.trime"

        val sync = FolderSync(context, AppPrefs.defaultInstance().profile.userDataDir)
        sync.copyFiles(
            arrayOf("$fileNameWithoutExt.yaml", "$fileNameWithoutExt.custom.yaml"),
            AppPrefs.Profile.getAppPath(),
        )
    }
}
