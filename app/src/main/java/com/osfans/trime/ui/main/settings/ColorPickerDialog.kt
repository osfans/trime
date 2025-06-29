// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import kotlinx.coroutines.launch

object ColorPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val presetSchemes = ThemeManager.activeTheme.colorSchemes
        val currentScheme = ColorManager.activeColorScheme
        val currentIndex = presetSchemes.indexOfFirst { it.id == currentScheme.id }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.normal_mode_color)
                if (presetSchemes.isEmpty()) {
                    setMessage(R.string.no_color_to_select)
                } else {
                    setSingleChoiceItems(
                        presetSchemes.map { it.colors["name"] }.toTypedArray(),
                        currentIndex,
                    ) { dialog, which ->
                        scope.launch {
                            afterConfirm?.invoke()
                            if (which != currentIndex) {
                                val newScheme = presetSchemes[which]
                                ColorManager.setColorScheme(newScheme)
                            }
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }
}
