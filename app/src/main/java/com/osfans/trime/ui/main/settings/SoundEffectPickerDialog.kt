// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.soundeffect.SoundEffectManager
import kotlinx.coroutines.launch

object SoundEffectPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
    ): AlertDialog {
        val all = SoundEffectManager.getAllSoundEffects().map { it.name }
        val current = SoundEffectManager.activeSoundEffect?.name ?: ""
        val currentIndex = all.indexOfFirst { it == current }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.custom_sound_effect_name)
                if (all.isEmpty()) {
                    setMessage(R.string.no_effect_to_select)
                } else {
                    setSingleChoiceItems(
                        all.toTypedArray(),
                        currentIndex,
                    ) { dialog, which ->
                        scope.launch {
                            if (which != currentIndex) {
                                SoundEffectManager.switchEffect(all[which])
                            }
                            dialog.dismiss()
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }
}
