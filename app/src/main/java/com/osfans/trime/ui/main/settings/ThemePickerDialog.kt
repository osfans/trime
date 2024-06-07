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
                when (val themeName = it.first) {
                    ThemeManager.DEFAULT_THEME -> context.getString(R.string.theme_trime)
                    ThemeManager.TONGWENFENG_THEME -> context.getString(R.string.theme_tongwenfeng)
                    else ->
                        if (ThemeManager.isUserTheme(it)) {
                            themeName
                        } else {
                            "[${context.getString(R.string.share)}] $themeName"
                        }
                }
            }
        val current = AppPrefs.defaultInstance().theme.selectedTheme
        val currentIndex = all.indexOfFirst { it.first == current }.coerceAtLeast(0)
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
                            ThemeManager.setNormalTheme(all[which].first)
                        }
                    }
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }.create()
    }

    private suspend fun copyThemeFile(
        context: Context,
        selectedTheme: Pair<String, String>,
    ) {
        val themeName = selectedTheme.first
        val fileNameWithoutExt =
            if (themeName == "trime") {
                "trime"
            } else {
                "$themeName.trime"
            }

        val profile = AppPrefs.defaultInstance().profile
        val uri =
            if (ThemeManager.isUserTheme(selectedTheme)) {
                profile.userDataDirUri
            } else {
                profile.sharedDataDirUri
            }

        val targetPath =
            if (ThemeManager.isUserTheme(selectedTheme)) {
                profile.getAppUserDir()
            } else {
                profile.getAppShareDir()
            }

        val sync = FolderSync(context, uri)
        sync.copyFiles(
            arrayOf("$fileNameWithoutExt.yaml", "$fileNameWithoutExt.custom.yaml"),
            targetPath,
        )
    }
}
