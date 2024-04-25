// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.sound.SoundEffect
import com.osfans.trime.data.sound.SoundEffectManager
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ui.components.CoroutineChoiceDialog
import kotlinx.coroutines.Dispatchers

suspend fun buildThemePickerDialog(
    context: Context,
    scope: LifecycleCoroutineScope,
): AlertDialog {
    return CoroutineChoiceDialog(context, scope).apply {
        title = context.getString(R.string.looks__selected_theme_title)
        initDispatcher = Dispatchers.IO
        onInit {
            items =
                ThemeManager.getAllThemes()
                    .map { it.substringBeforeLast('.') }
                    .toTypedArray()
            val current =
                AppPrefs.defaultInstance().theme.selectedTheme
                    .substringBeforeLast('.')
            checkedItem = items.indexOf(current)
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            with(items[checkedItem].toString()) {
                ThemeManager.setNormalTheme(if (this == "trime") this else "$this.trime")
            }
        }
    }.create()
}

suspend fun buildColorPickerDialog(
    context: Context,
    scope: LifecycleCoroutineScope,
): AlertDialog {
    return CoroutineChoiceDialog(context, scope).apply {
        title = context.getString(R.string.looks__selected_color_title)
        initDispatcher = Dispatchers.Default
        val all by lazy { ColorManager.presetColorSchemes }
        onInit {
            items = all.map { it.value["name"]!! }.toTypedArray()
            val current = ColorManager.selectedColor
            val schemeIds = all.keys
            checkedItem = schemeIds.indexOf(current).takeIf { it > -1 } ?: 1
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            val schemeIds = all.keys.toList()
            ColorManager.setColorScheme(schemeIds[checkedItem])
        }
    }.create()
}

fun buildSoundEffectPickerDialog(context: Context): AlertDialog {
    val all = SoundEffectManager.getAllSoundEffects().mapNotNull(SoundEffect::name)
    val current = SoundEffectManager.getActiveSoundEffect().getOrNull()?.name ?: ""
    var checked = all.indexOf(current)
    return AlertDialog.Builder(context)
        .setTitle(R.string.keyboard__key_sound_package_title)
        .setSingleChoiceItems(
            all.toTypedArray(),
            checked,
        ) { _, id -> checked = id }
        .setPositiveButton(android.R.string.ok) { _, _ ->
            SoundEffectManager.switchSound(all[checked])
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
}
