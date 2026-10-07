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
    private val MODE_IDS = listOf("light", "dark")

    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val labels = listOf(context.getString(R.string.light), context.getString(R.string.dark))
        val activeTheme = ThemeManager.activeTheme
        val currentIndex = if (ColorManager.activeColorScheme == activeTheme.colorSchemas.dark) 1 else 0
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.normal_mode_color)
                setSingleChoiceItems(
                    labels.toTypedArray(),
                    currentIndex,
                ) { dialog, which ->
                    val modeId = MODE_IDS[which]
                    if (ThemeManager.prefs.followSystemDayNight.getValue()) {
                        // 跟随系统开启时，手动选择需先征得用户确认，否则跟随系统会让选择静默失效。
                        confirmDisableFollowSystem(context, scope, modeId, afterConfirm)
                    } else {
                        applyMode(scope, modeId, afterConfirm)
                    }
                    dialog.dismiss()
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }

    private fun applyMode(
        scope: LifecycleCoroutineScope,
        modeId: String,
        afterConfirm: (suspend () -> Unit)?,
    ) {
        scope.launch {
            afterConfirm?.invoke()
            ColorManager.setColorScheme(modeId)
        }
    }

    private fun confirmDisableFollowSystem(
        context: Context,
        scope: LifecycleCoroutineScope,
        modeId: String,
        afterConfirm: (suspend () -> Unit)?,
    ) {
        AlertDialog
            .Builder(context)
            .setMessage(R.string.manual_select_disables_follow_system)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ThemeManager.prefs.followSystemDayNight.setValue(false)
                applyMode(scope, modeId, afterConfirm)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
