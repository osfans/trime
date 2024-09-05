// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ColorPickerDialog {
    suspend fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
    ): AlertDialog {
        val all = withContext(Dispatchers.Default) { ColorManager.presetColorSchemes }
        val allIds = all.keys
        val allNames = all.values.mapNotNull { it["name"] }
        val currentId = ColorManager.selectedColor
        val currentIndex = all.keys.indexOfFirst { it == currentId }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.looks__selected_color_title)
                if (all.isEmpty()) {
                    setMessage(R.string.no_color_to_select)
                } else {
                    setSingleChoiceItems(
                        allNames.toTypedArray(),
                        currentIndex,
                    ) { dialog, which ->
                        scope.launch {
                            if (which != currentIndex) {
                                ColorManager.setColorScheme(allIds.elementAt(which))
                            }
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }
}
