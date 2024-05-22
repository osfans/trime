// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
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
        val current =
            AppPrefs.defaultInstance().theme.selectedTheme
        val currentIndex = all.indexOfFirst { it == current }
        return AlertDialog.Builder(context).apply {
            setTitle(R.string.looks__selected_theme_title)
            if (all.isEmpty()) {
                setMessage(R.string.no_theme_to_select)
            } else {
                setSingleChoiceItems(
                    all.toTypedArray(),
                    currentIndex,
                ) { dialog, which ->
                    scope.launch {
                        ThemeManager.setNormalTheme(all[which])
                        dialog.dismiss()
                    }
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }.create()
    }
}
