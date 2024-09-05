// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.dialog

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.daemon.RimeDaemon
import kotlinx.coroutines.launch

object AvailableSchemaPickerDialog {
    suspend fun build(
        rime: RimeApi,
        scope: LifecycleCoroutineScope,
        context: Context,
    ): AlertDialog {
        val availables = rime.availableSchemata()
        val enableds = rime.enabledSchemata()
        val availableIds = availables.map { it.id }
        val enabledIds = enableds.map { it.id }
        val enabledBools = availableIds.map { enabledIds.contains(it) }.toBooleanArray()
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.enable_schemata)
                if (availables.isEmpty()) {
                    setMessage(R.string.no_schema_to_enable)
                } else {
                    setMultiChoiceItems(availables.map { it.name }.toTypedArray(), enabledBools) { _, which, isChecked ->
                        enabledBools[which] = isChecked
                    }
                    setPositiveButton(R.string.ok) { _, _ ->
                        val newEnabled = availableIds.filterIndexed { index, _ -> enabledBools[index] }
                        if (setOf(newEnabled) == setOf(enabledIds)) return@setPositiveButton
                        scope.launch {
                            rime.setEnabledSchemata(newEnabled.toTypedArray())
                            RimeDaemon.restartRime()
                        }
                    }
                }
                setNegativeButton(R.string.cancel, null)
            }.create()
    }
}
